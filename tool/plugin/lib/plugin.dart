// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:args/args.dart';
import 'package:args/command_runner.dart';
import 'package:cli_util/cli_logging.dart';
import 'package:git/git.dart';
import 'package:markdown/markdown.dart';
import 'package:path/path.dart' as p;

import 'lint.dart';

Future<int> main(List<String> args) async {
  var runner = new BuildCommandRunner();

  runner.addCommand(new LintCommand(runner));
  runner.addCommand(new BuildCommand(runner));
  runner.addCommand(new TestCommand(runner));
  runner.addCommand(new DeployCommand(runner));
  runner.addCommand(new GenerateCommand(runner));

  try {
    return await runner.run(args) ?? 0;
  } on UsageException catch (e) {
    print('$e');
    return 1;
  }
}

const Map<String, String> plugins = const {
  'io.flutter': '9212',
  'io.flutter.as': '10139', // Currently unused.
};

const int cloudErrorFileMaxSize = 1000; // In bytes.

// Globals are initialized early in ProductCommand, and everything in this
// file is a subclass of ProductCommand. It is not ideal, but the "proper"
// solution would be to move nearly all the top-level functions to methods
// in ProductCommand.
String rootPath;
String lastReleaseName;
DateTime lastReleaseDate;
int pluginCount = 0;

void addProductFlags(ArgParser argParser, String verb) {
  argParser.addFlag('ij', help: '$verb the IntelliJ plugin', defaultsTo: true);
  argParser.addFlag('as',
      help: '$verb the Android Studio plugin', defaultsTo: true);
}

void copyResources({String from, String to}) {
  log('copying resources from $from to $to');
  _copyResources(Directory(from), Directory(to));
}

List<BuildSpec> createBuildSpecs(ProductCommand command) {
  var specs = List<BuildSpec>();
  var input = readProductMatrix();
  input.forEach((json) {
    specs.add(BuildSpec.fromJson(json, command.release));
  });
  return specs;
}

void createDir(String name) {
  final dir = Directory(name);
  if (!dir.existsSync()) {
    log('creating $name/');
    dir.createSync(recursive: true);
  }
}

Future<int> curl(String url, {String to}) async {
  return await exec('curl', ['-o', to, url]);
}

Future<int> deleteBuildContents() async {
  final dir = Directory(p.join(rootPath, 'build'));
  if (!dir.existsSync()) throw 'No build directory found';
  var args = List<String>();
  args.add('-rf');
  args.add(p.join(rootPath, 'build', '*'));
  return await exec('rm', args);
}

Future<int> exec(String cmd, List<String> args, {String cwd}) async {
  if (cwd != null) {
    log(_shorten('$cmd ${args.join(' ')} {cwd=$cwd}'));
  } else {
    log(_shorten('$cmd ${args.join(' ')}'));
  }

  final process = await Process.start(cmd, args, workingDirectory: cwd);
  _toLineStream(process.stderr, utf8).listen(log);
  _toLineStream(process.stdout, utf8).listen(log);

  return await process.exitCode;
}

List<File> findJars(String path) {
  final dir = Directory(path);
  return dir
      .listSync(recursive: true, followLinks: false)
      .where((e) => e is File && e.path.endsWith('.jar'))
      .toList()
      .cast<File>();
}

List<String> findJavaFiles(String path) {
  final dir = Directory(path);
  return dir
      .listSync(recursive: true, followLinks: false)
      .where((e) => e.path.endsWith('.java'))
      .map((f) => f.path)
      .toList();
}

Future<bool> genPluginFiles(BuildSpec spec, String destDir) async {
  var result = await genPluginXml(spec, destDir, 'META-INF/plugin.xml');
  if (result == null) return false;
  result = await genPluginXml(spec, destDir, 'META-INF/studio-contribs.xml');
  if (result == null) return false;
  return true;
}

Future<File> genPluginXml(BuildSpec spec, String destDir, String path) async {
  var templatePath =
      path.substring(0, path.length - '.xml'.length) + '_template.xml';
  var file =
      await File(p.join(rootPath, destDir, path)).create(recursive: true);
  log('writing ${p.relative(file.path)}');
  var dest = file.openWrite();
  dest.writeln(
      "<!-- Do not edit; instead, modify ${p.basename(templatePath)}, and run './bin/plugin generate'. -->");
  dest.writeln();
  await utf8.decoder
      .bind(File(p.join(rootPath, 'resources', templatePath)).openRead())
      .transform(LineSplitter())
      .forEach((l) => dest.writeln(substituteTemplateVariables(l, spec)));
  await dest.close();
  return await dest.done;
}

void genTravisYml(List<BuildSpec> specs) {
  var file = File(p.join(rootPath, '.travis.yml'));
  var env = '';
  for (var spec in specs) {
    if (!spec.untilBuild.contains('SNAPSHOT'))
      env += '  - IDEA_VERSION=${spec.version}\n';
  }

  var templateFile = File(p.join(rootPath, '.travis_template.yml'));
  var templateContents = templateFile.readAsStringSync();
  var header =
      "# Do not edit; instead, modify ${p.basename(templateFile.path)},"
      " and run './bin/plugin generate'.\n\n";
  var contents = header + templateContents + env;
  log('writing ${p.relative(file.path)}');
  file.writeAsStringSync(contents, flush: true);
}

bool isCacheDirectoryValid(Artifact artifact) {
  var dirPath = artifact.outPath;
  var dir = Directory(dirPath);
  if (!dir.existsSync()) {
    return false;
  }
  var filePath = artifact.file;
  var file = File(p.join(rootPath, 'artifacts', filePath));
  if (!file.existsSync()) {
    throw 'Artifact file missing: $filePath';
  }
  return isNewer(dir, file);
}

bool isNewer(FileSystemEntity newer, FileSystemEntity older) {
  return newer.statSync().modified.isAfter(older.statSync().modified);
}

bool isTravisFileValid() {
  var travisPath = p.join(rootPath, '.travis.yml');
  var travisFile = File(travisPath);
  if (!travisFile.existsSync()) {
    return false;
  }
  var matrixPath = p.join(rootPath, 'product-matrix.json');
  var matrixFile = File(matrixPath);
  if (!matrixFile.existsSync()) {
    throw 'product-matrix.json is missing';
  }
  return isNewer(travisFile, matrixFile);
}

Future<int> jar(String directory, String outFile) async {
  var args = ['cf', p.absolute(outFile)];
  args.addAll(Directory(directory)
      .listSync(followLinks: false)
      .map((f) => p.basename(f.path)));
  args.remove('.DS_Store');
  return await exec('jar', args, cwd: directory);
}

void log(String s, {bool indent: true}) {
  indent ? print('  $s') : print(s);
}

Future<int> moveToArtifacts(ProductCommand cmd, BuildSpec spec) async {
  final dir = Directory(p.join(rootPath, 'artifacts'));
  if (!dir.existsSync()) throw 'No artifacts directory found';
  var file = plugins[spec.pluginId];
  var args = List<String>();
  args.add(p.join(rootPath, 'build', file));
  args.add(cmd.releasesFilePath(spec));
  return await exec('mv', args);
}

Future<bool> performReleaseChecks(ProductCommand cmd) async {
  // git must have a release_NN branch where NN is the value of --release
  // git must have no uncommitted changes
  var isGitDir = await GitDir.isGitDir(rootPath);
  if (isGitDir) {
    if (cmd.isTestMode) {
      return true;
    }
    if (cmd.isDevChannel) {
      log('release mode is incompatible with the dev channel');
      return false;
    }
    if (!cmd.isReleaseValid) {
      log('the release identifier ("${cmd.release}") must be of the form xx.x (major.minor)');
      return false;
    }
    var gitDir = await GitDir.fromExisting(rootPath);
    var isClean = await gitDir.isWorkingTreeClean();
    if (isClean) {
      var branch = await gitDir.getCurrentBranch();
      var name = branch.branchName;
      var expectedName =
          cmd.isDevChannel ? 'master' : "release_${cmd.releaseMajor}";
      var result = name == expectedName;
      if (!result)
        result = name.startsWith("release_${cmd.releaseMajor}") &&
            name.lastIndexOf(RegExp("\.[0-9]")) == name.length - 2;
      if (result) {
        if (isTravisFileValid()) {
          return result;
        } else {
          log('the .travis.yml file needs updating: plugin generate');
        }
      } else {
        log('the current git branch must be named "$expectedName"');
      }
    } else {
      log('the current git branch has uncommitted changes');
    }
  } else {
    log('the current working directory is not managed by git: $rootPath');
  }
  return false;
}

List readProductMatrix() {
  var contents =
      File(p.join(rootPath, 'product-matrix.json')).readAsStringSync();
  var map = json.decode(contents);
  return map['list'];
}

Future<int> removeAll(String dir) async {
  var args = ['-rf', dir];
  return await exec('rm', args);
}

final Ansi ansi = Ansi(true);

void separator(String name) {
  log('');
  log('${ansi.red}${ansi.bold}$name${ansi.none}', indent: false);
}

int get devBuildNumber {
  // The dev channel is automatically refreshed weekly, so the build number
  // is just the number of weeks since the last stable release.
  var today = DateTime.now();
  var daysSinceRelease = today.difference(lastReleaseDate).inDays;
  var weekNumber = daysSinceRelease ~/ 7 + 1;
  return weekNumber;
}

String substituteTemplateVariables(String line, BuildSpec spec) {
  String valueOf(String name) {
    switch (name) {
      case 'PLUGINID':
        return spec.pluginId;
      case 'SINCE':
        return spec.sinceBuild;
      case 'UNTIL':
        return spec.untilBuild;
      case 'VERSION':
        var releaseNo = spec.isDevChannel ? _nextRelease() : spec.release;
        if (releaseNo == null) {
          releaseNo = 'SNAPSHOT';
        } else {
          releaseNo = '$releaseNo.${++pluginCount}';
          if (spec.isDevChannel) {
            releaseNo += '-dev.$devBuildNumber';
          }
        }
        return '<version>$releaseNo</version>';
      case 'CHANGELOG':
        return spec.changeLog;
      case 'DEPEND':
        // If found, this is the module that triggers loading the Android Studio
        // support. The public sources and the installable plugin use different ones.
        return spec.isSynthetic
            ? 'com.intellij.modules.androidstudio'
            : 'com.android.tools.apk';
      default:
        throw 'unknown template variable: $name';
    }
  }

  var start = line.indexOf('@');
  while (start >= 0 && start < line.length) {
    var end = line.indexOf('@', start + 1);
    if (end > 0) {
      var name = line.substring(start + 1, end);
      line = line.replaceRange(start, end + 1, valueOf(name));
      if (end < line.length - 1) {
        start = line.indexOf('@', end + 1);
      }
    } else {
      break; // Some commit message has a '@' in it.
    }
  }
  return line;
}

Future<int> zip(String directory, String outFile) async {
  var dest = p.absolute(outFile);
  createDir(p.dirname(dest));
  var args = ['-r', dest, p.basename(directory)];
  return await exec('zip', args, cwd: p.dirname(directory));
}

String _nextRelease() {
  var current = RegExp(r'release_(\d+)').matchAsPrefix(lastReleaseName).group(1);
  var val = int.parse(current) + 1;
  return '$val.0';
}

String _convertToTar(String path) =>
    path.replaceFirst('.zip', '.tar.gz', path.length - 5);

void _copyFile(File file, Directory to, {String filename = ''}) {
  if (!to.existsSync()) {
    to.createSync(recursive: true);
  }
  if (filename == '') filename = p.basename(file.path);
  final target = File(p.join(to.path, filename));
  target.writeAsBytesSync(file.readAsBytesSync());
}

void _copyResources(Directory from, Directory to) {
  for (var entity in from.listSync(followLinks: false)) {
    final basename = p.basename(entity.path);
    if (basename.endsWith('.java') ||
        basename.endsWith('.kt') ||
        basename.endsWith('.form') ||
        basename == 'plugin.xml.template') {
      continue;
    }

    if (entity is File) {
      _copyFile(entity, to);
    } else if (entity is Directory) {
      _copyResources(entity, Directory(p.join(to.path, basename)));
    }
  }
}

bool _isValidDownloadArtifact(File archiveFile) =>
    archiveFile.existsSync() &&
    archiveFile.lengthSync() > cloudErrorFileMaxSize;

String _shorten(String str) {
  return str.length < 200
      ? str
      : str.substring(0, 170) + ' ... ' + str.substring(str.length - 30);
}

Stream<String> _toLineStream(Stream<List<int>> s, Encoding encoding) =>
    s.transform(encoding.decoder).transform(const LineSplitter());

class Artifact {
  String file;
  final bool bareArchive;
  String output;

  Artifact(this.file, {this.bareArchive: false, this.output}) {
    if (output == null) {
      output = file.substring(0, file.lastIndexOf('-'));
    }
  }

  bool get isZip => file.endsWith('.zip');

  String get outPath => p.join(rootPath, 'artifacts', output);

  // Historically, Android Studio has been distributed as a zip file.
  // Recent distros are packaged as gzip'd tar files.
  void convertToTar() {
    if (!isZip) return;
    file = _convertToTar(file);
  }
}

class ArtifactManager {
  final String base =
      'https://storage.googleapis.com/flutter_infra/flutter/intellij';

  final List<Artifact> artifacts = [];

  Artifact javac;

  ArtifactManager() {
    javac = add(Artifact(
      'intellij-javac2.zip',
      output: 'javac2',
      bareArchive: true,
    ));
  }

  Artifact add(Artifact artifact) {
    artifacts.add(artifact);
    return artifact;
  }

  Future<int> provision({bool rebuildCache = false}) async {
    separator('Getting artifacts');
    createDir('artifacts');

    var result = 0;
    for (var artifact in artifacts) {
      var doDownload = true;

      void alreadyDownloaded(String path) {
        log('$path exists in cache');
        doDownload = false;
      }

      var path = 'artifacts/${artifact.file}';
      if (FileSystemEntity.isFileSync(path)) {
        alreadyDownloaded(path);
      } else {
        if (artifact.isZip) {
          var tarPath = _convertToTar(path);
          if (FileSystemEntity.isFileSync(tarPath)) {
            artifact.convertToTar();
            alreadyDownloaded(tarPath);
          }
        }
        if (doDownload) {
          log('downloading $path...');
          result = await curl('$base/${artifact.file}', to: path);
          if (result != 0) {
            log('download failed');
            break;
          }
          var archiveFile = File(path);
          if (!_isValidDownloadArtifact(archiveFile)) {
            // If the file is missing the server returns a small file containing
            // an error message. Delete it and try again. The smallest file we
            // store in the cloud is over 700K.
            log('archive file not found: $base/${artifact.file}');
            archiveFile.deleteSync();
            if (artifact.isZip) {
              artifact.convertToTar();
              path = 'artifacts/${artifact.file}';
              result = await curl('$base/${artifact.file}', to: path);
              if (result != 0) {
                log('download failed');
                break;
              }
              var archiveFile = File(path);
              if (!_isValidDownloadArtifact(archiveFile)) {
                log('archive file not found: $base/${artifact.file}');
                archiveFile.deleteSync();
                result = 1;
                break;
              }
            }
          }
        }
      }

      // clear unpacked cache
      if (rebuildCache || !FileSystemEntity.isDirectorySync(artifact.outPath)) {
        await removeAll(artifact.outPath);
      }
      if (isCacheDirectoryValid(artifact)) {
        continue;
      }

      // expand
      if (Directory(artifact.outPath).existsSync()) {
        await removeAll(artifact.outPath);
      }
      createDir(artifact.outPath);

      if (artifact.isZip) {
        if (artifact.bareArchive) {
          result = await exec(
              'unzip', ['-q', '-d', artifact.output, artifact.file],
              cwd: 'artifacts');
        } else {
          result = await exec('unzip', ['-q', artifact.file], cwd: 'artifacts');
        }
      } else {
        result = await exec(
          'tar',
          [
            '--strip-components=1',
            '-zxf',
            artifact.file,
            '-C',
            artifact.output
          ],
          cwd: p.join(rootPath, 'artifacts'),
        );
      }
      if (result != 0) {
        log('unpacking failed');
        await removeAll(artifact.output);
        break;
      }

      log('');
    }
    return result;
  }
}

/// Build deployable plugin files. If the --release argument is given
/// then perform additional checks to verify that the release environment
/// is in good order.
class BuildCommand extends ProductCommand {
  final BuildCommandRunner runner;

  BuildCommand(this.runner) : super('build') {
    argParser.addOption('only-version',
        abbr: 'o',
        help: 'Only build the specified IntelliJ version; useful for sharding '
            'builds on CI systems.');
    argParser.addFlag('unpack',
        abbr: 'u',
        help: 'Unpack the artifact files during provisioning, '
            'even if the cache appears fresh.\n'
            'This flag is ignored if --release is given.',
        defaultsTo: false);
  }

  String get description => 'Build a deployable version of the Flutter plugin, '
      'compiled against the specified artifacts.';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (argResults['unpack']) {
        separator('Release mode (--release) implies --unpack');
      }
      if (!await performReleaseChecks(this)) {
        return 1;
      }
    }

    // Check to see if we should only be building a specific version.
    String onlyVersion = argResults['only-version'];

    var buildSpecs = specs;
    if (onlyVersion != null && onlyVersion.isNotEmpty) {
      buildSpecs = specs.where((spec) => spec.version == onlyVersion).toList();
      if (buildSpecs.isEmpty) {
        log("No spec found for version '$onlyVersion'");
        return 1;
      }
    }

    var result = 0;
    for (var spec in buildSpecs) {
      if (spec.channel != channel && isReleaseMode) {
        continue;
      }
      if (!(isForIntelliJ && isForAndroidStudio)) {
        // This is a little more complicated than I'd like because the default
        // is to always do both.
        if (isForAndroidStudio && !spec.isAndroidStudio) continue;
        if (isForIntelliJ && spec.isAndroidStudio) continue;
      }

      if (spec.isDevChannel && !isDevChannel) {
        spec.buildForMaster();
      }

      result = await spec.artifacts.provision(
        rebuildCache:
            isReleaseMode || argResults['unpack'] || buildSpecs.length > 1,
      );
      if (result != 0) {
        return result;
      }

      separator('Building flutter-intellij.jar');
      await removeAll('build');

      // Handle skipped files.
      for (String file in spec.filesToSkip) {
        var entity =
            FileSystemEntity.isFileSync(file) ? File(file) : Directory(file);
        if (entity.existsSync()) {
          await entity.rename('$file~');
          if (entity is File) {
            var stubFile = File('${file}_stub');
            if (stubFile.existsSync()) {
              await stubFile.copy('$file');
            }
          }
        }
      }

      log('spec.version: ${spec.version}');
      var files = <File, String>{};
      var processedFile, source;

      // TODO: Remove this when we no longer support the corresponding version.

      if (spec.version.startsWith('3.5') || spec.version.startsWith('3.6')) {
        processedFile = File(
            'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        if (spec.version.startsWith('3.5')) {
          source = source.replaceAll(
            'androidProject.init(null);',
            'androidProject.init();',
          );
        }
        var last = 'static final String TEMPLATE_PROJECT_NAME = ';
        var end = source.indexOf(last);
        // Change the initializer to use null, equivalent to original code.
        source = '${source.substring(0, end + last.length)}null;\n}}';
        source = source.replaceAll(
          'super(filePath',
          'super(filePath.toString()',
        );
        processedFile.writeAsStringSync(source);
      }

      if (spec.version.startsWith('3.5')) {
        processedFile =
            File('flutter-studio/src/io/flutter/utils/AddToAppUtils.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'connection.subscribe(DebuggerManagerListener.TOPIC, makeAddToAppAttachListener(project));',
          '',
        );
        processedFile.writeAsStringSync(source);
      }

      if (!spec.version.startsWith('4.')) {
        processedFile = File(
            'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'import com.android.tools.idea.projectsystem.SourceProvidersFactory;',
          '',
        );
        source = source.replaceAll(' SourceProvidersFactory ', ' Object ');
        source = source.replaceAll(
          'gradleProjectSystem.getSourceProvidersFactory()',
          'new Object()',
        );
        if (spec.version.startsWith('3.5') || spec.version.startsWith('3.6')) {
          source = source.replaceAll(
            'gradleProjectSystem.getSubmodules()',
            'new java.util.ArrayList()',
          );
        }
        processedFile.writeAsStringSync(source);

        processedFile = File(
            'flutter-studio/src/io/flutter/project/FlutterProjectCreator.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'Paths.get(baseDir.getPath())',
          'baseDir.getPath()',
        );
        processedFile.writeAsStringSync(source);

        processedFile = File(
            'flutter-studio/src/io/flutter/module/FlutterDescriptionProvider.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'import com.android.tools.idea.npw.model.ProjectSyncInvoker',
          'import com.android.tools.idea.npw.model.NewModuleModel',
        );
        source = source.replaceAll(
          'createStep(@NotNull Project model, @NotNull ProjectSyncInvoker invoker, String parent)',
          'createStep(@NotNull NewModuleModel model)',
        );
        source = source.replaceAll(
          'FlutterProjectModel model(@NotNull Project project,',
          'FlutterProjectModel model(@NotNull NewModuleModel project,',
        );
        source = source.replaceAll(
          'mySharedModel.getValue().project().setValue(project);',
          'mySharedModel.getValue().project().setValue(project.getProject().getValue());',
        );
        processedFile.writeAsStringSync(source);
      }

      try {
        result = await runner.javac2(spec);

        // copy resources
        copyResources(from: 'src', to: 'build/classes');
        copyResources(from: 'resources', to: 'build/classes');
        copyResources(from: 'gen', to: 'build/classes');
        await genPluginFiles(spec, 'build/classes');
      } finally {
        // Restore sources.
        files.forEach((file, src) {
          log('Restoring ${file.path}');
          file.writeAsStringSync(src);
        });

        // Restore skipped files.
        for (var file in spec.filesToSkip) {
          var name = '$file~';
          var entity =
              FileSystemEntity.isFileSync(name) ? File(name) : Directory(name);
          if (entity.existsSync()) {
            await entity.rename(file);
          }
        }
      }
      if (result != 0) {
        return result;
      }

      // create the jars
      createDir('build/flutter-intellij/lib');
      result = await jar(
          'build/classes', 'build/flutter-intellij/lib/flutter-intellij.jar');
      if (result != 0) {
        log('jar failed: ${result.toString()}');
        return result;
      }
      if (spec.isTestTarget && !isReleaseMode && !isDevChannel) {
        _copyFile(File('build/flutter-intellij/lib/flutter-intellij.jar'),
            Directory(testTargetPath(spec)),
            filename: 'io.flutter.jar');
      }
      if (spec.isAndroidStudio) {
        result = await jar(
            'build/studio', 'build/flutter-intellij/lib/flutter-studio.jar');
        if (result != 0) {
          log('jar failed: ${result.toString()}');
          return result;
        }
      }

      // zip it up
      result = await zip('build/flutter-intellij', releasesFilePath(spec));
      if (result != 0) {
        log('zip failed: ${result.toString()}');
        return result;
      }
      if (spec.copyIjVersion && !isReleaseMode && !isDevChannel) {
        _copyFile(File(releasesFilePath(spec)), Directory(ijVersionPath(spec)),
            filename: 'flutter-intellij.zip');
      }

      separator('Built artifact');
      log('${releasesFilePath(spec)}');
    }

    return 0;
  }
}

class BuildCommandRunner extends CommandRunner {
  BuildCommandRunner()
      : super('plugin',
            'A script to build, test, and deploy the Flutter IntelliJ plugin.') {
    argParser.addOption(
      'release',
      abbr: 'r',
      help: 'The release identifier; the numeric suffix of the git branch name',
      valueHelp: 'id',
    );
    argParser.addOption(
      'cwd',
      abbr: 'd',
      help: 'For testing only; the prefix used to locate the root path (../..)',
      valueHelp: 'relative-path',
    );
  }

  // Use this to compile plugin sources to get forms processed.
  Future<int> javac2(BuildSpec spec) async {
    var args = '''
-f tool/plugin/compile.xml
-Didea.product=${spec.ideaProduct}
-Didea.version=${spec.ideaVersion}
-Dbasedir=$rootPath
compile
''';
    try {
      return await exec('ant', args.split(RegExp(r'\s')));
    } on ProcessException catch (x) {
      if (x.message == 'No such file or directory') {
        log(
            '\nThe build command requires ant to be installed. '
            '\nPlease ensure ant is on your \$PATH.',
            indent: false);
        exit(x.errorCode);
        // The call to `exit` above does not return, but we return a value from
        // the function here to make the analyzer happy.
        return 0;
      } else {
        throw x;
      }
    }
  }
}

class BuildSpec {
  // Build targets
  final String name;
  final String version;
  final String ijVersion;
  final bool isTestTarget;
  final String ideaProduct;
  final String ideaVersion;
  final String dartPluginVersion;

  // plugin.xml variables
  final String sinceBuild;
  final String untilBuild;
  final String pluginId = 'io.flutter';
  final String release;
  final List<dynamic> filesToSkip;
  String channel;
  String _changeLog;

  ArtifactManager artifacts = ArtifactManager();

  Artifact product;
  Artifact dartPlugin;

  BuildSpec.fromJson(Map json, String releaseNum)
      : release = releaseNum,
        name = json['name'],
        channel = json['channel'],
        version = json['version'],
        ijVersion = json['ijVersion'] ?? null,
        ideaProduct = json['ideaProduct'],
        ideaVersion = json['ideaVersion'],
        dartPluginVersion = json['dartPluginVersion'],
        sinceBuild = json['sinceBuild'],
        untilBuild = json['untilBuild'],
        filesToSkip = json['filesToSkip'] ?? [],
        isTestTarget = (json['isTestTarget'] ?? 'false') == 'true' {
    createArtifacts();
  }

  bool get copyIjVersion => isAndroidStudio && ijVersion != null;

  bool get isAndroidStudio => ideaProduct.contains('android-studio');

  bool get isDevChannel => channel == 'dev';

  bool get isReleaseMode => release != null;

  bool get isSynthetic => false;

  String get productFile => isAndroidStudio ? "$ideaProduct-ide" : ideaProduct;

  String get changeLog {
    if (_changeLog == null) {
      if (channel == 'stable') {
        _changeLog = _parseChangelog();
      } else {
        _changeLog = '';
      }
    }

    return _changeLog;
  }

  void createArtifacts() {
    if (ideaProduct == 'android-studio') {
      product = artifacts.add(Artifact(
          '$ideaProduct-ide-$ideaVersion-linux.zip',
          output: ideaProduct));
    } else {
      product = artifacts.add(
          Artifact('$ideaProduct-$ideaVersion.tar.gz', output: ideaProduct));
    }
    dartPlugin = artifacts.add(Artifact('Dart-$dartPluginVersion.zip'));
  }

  String _parseChangelog() {
    var text = File('CHANGELOG.md').readAsStringSync();
    return _parseChanges(text);
  }

  String _parseChanges(String text) {
    var html = markdownToHtml(text);

    // Translate our markdown based changelog into html; remove unwanted
    // paragraph tags.
    return html
        .replaceAll('</h2><ul>', '</h2>\n<ul>')
        .replaceAll('<ul>\n<li>', '<ul>\n  <li>')
        .replaceAll('</li>\n<li>', '</li>\n  <li>')
        .replaceAll('</li></ul>', '</li>\n</ul>')
        .replaceAll('\n<p>', '')
        .replaceAll('<p>', '')
        .replaceAll('</p>\n', '')
        .replaceAll('</p>', '');
  }

  String toString() {
    return 'BuildSpec($ideaProduct $ideaVersion $dartPluginVersion $sinceBuild '
        '$untilBuild version: "$release")';
  }

  Future<BuildSpec> initChangeLog() async {
    if (channel == 'dev') {
      _changeLog = _parseChanges(await makeDevLog(this));
    }
    return this;
  }

  void buildForDev() {
    // Build everything. For release builds we do not build specs on the dev channel.
    channel = 'dev';
  }

  void buildForMaster() {
    // Ensure the dev-channel-only files are stored in release_master.
    channel = 'stable';
  }
}

/// Either the --release or --channel options must be provided.
/// The permanent token is read from the file specified by Kokoro.
class DeployCommand extends ProductCommand {
  final BuildCommandRunner runner;

  DeployCommand(this.runner) : super('deploy');

  String get description => 'Upload the Flutter plugin to the JetBrains site.';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (!await performReleaseChecks(this)) {
        return 1;
      }
    } else if (!isDevChannel) {
      log('Deploy must have a --release or --channel=dev argument');
      return 1;
    }

    var token = readTokenFile();
    var value = 0;
    var originalDir = Directory.current;
    for (var spec in specs) {
      if (spec.channel != channel) continue;
      var filePath = releasesFilePath(spec);
      log("uploading $filePath");
      var file = File(filePath);
      changeDirectory(file.parent);
      var pluginNumber = plugins[spec.pluginId];
      value = await upload(p.basename(file.path), pluginNumber, token, spec.channel);
      if (value != 0) {
        return value;
      }
    }
    changeDirectory(originalDir);
    return value;
  }

  void changeDirectory(Directory dir) {
    Directory.current = dir.path;
  }

  String readTokenFile() {
    var env = Platform.environment;
    var base = env['KOKORO_KEYSTORE_DIR'];
    var id = env['FLUTTER_KEYSTORE_ID'];
    var name = env['FLUTTER_KEYSTORE_NAME'];
    var file = File('$base/${id}_$name');
    var token = file.readAsStringSync();
    return token;
  }

  Future<int> upload(String filePath, String pluginNumber, String token,
      String channel) async {
    if (!File(filePath).existsSync()) {
      throw 'File not found: $filePath';
    }
    // See https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html#PluginUploadAPI-POST
    // Trying to run curl directly doesn't work; something odd happens to the quotes.
    var cmd = '''
curl
-i
--header "Authorization: Bearer $token"
-F pluginId=$pluginNumber
-F file=@$filePath
-F channel=$channel
https://plugins.jetbrains.com/plugin/uploadPlugin
''';

    var args = ['-c', cmd.split('\n').join(' ')];
    final processResult = await Process.run('sh', args);
    if (processResult.exitCode != 0) {
      log('Upload failed: ${processResult.stderr} for file: $filePath');
    }
    String out = processResult.stdout;
    var message = out.trim().split('\n').last.trim();
    log(message);
    return processResult.exitCode;
  }
}

/// Generate the plugin.xml from the plugin.xml.template file.
/// If the --release argument is given, create a git branch and
/// commit the new file to it, assuming the release checks pass.
///
/// Note: The product-matrix.json file includes a build spec for the
/// EAP version at the end. When the EAP version is released that needs
/// to be updated.
class GenerateCommand extends ProductCommand {
  final BuildCommandRunner runner;

  GenerateCommand(this.runner) : super('generate');

  String get description =>
      'Generate a valid plugin.xml and .travis.yml for the Flutter plugin.\n'
      'The plugin.xml.template and product-matrix.json are used as input.';

  Future<int> doit() async {
    var json = readProductMatrix();
    var spec = SyntheticBuildSpec.fromJson(json.first, release, specs);
    var value = 1;
    var result = await genPluginFiles(spec, 'resources');
    if (result != null) {
      genTravisYml(specs);
      value = 0;
    }
    if (isReleaseMode) {
      if (!await performReleaseChecks(this)) {
        return 1;
      }
    }
    return value;
  }

  SyntheticBuildSpec makeSyntheticSpec(List specs) =>
      SyntheticBuildSpec.fromJson(specs[0], release, specs[2]);
}

abstract class ProductCommand extends Command {
  final String name;
  List<BuildSpec> specs;

  ProductCommand(this.name) {
    addProductFlags(argParser, name[0].toUpperCase() + name.substring(1));
    argParser.addOption('channel',
        abbr: 'c',
        help: 'Select the channel to build: stable or dev',
        defaultsTo: 'stable');
  }

  String get channel => argResults['channel'];

  bool get isDevChannel => channel == 'dev';

  /// Returns true when running in the context of a unit test.
  bool get isTesting => false;

  bool get isForAndroidStudio => argResults['as'];

  bool get isForIntelliJ => argResults['ij'];

  DateTime get releaseDate => lastReleaseDate;

  bool get isReleaseMode => release != null;

  bool get isReleaseValid {
    var rel = release;
    if (rel == null) {
      return false;
    }
    // Validate for '00.0' with optional '-dev.0'
    return rel == RegExp(r'^\d+\.\d(?:-dev.\d)?$').stringMatch(rel);
  }

  bool get isTestMode => globalResults['cwd'] != null;

  String get release {
    String rel = globalResults['release'];

    if (rel != null) {
      if (rel.startsWith('=')) {
        rel = rel.substring(1);
      }
      if (!rel.contains('.')) {
        rel = '$rel.0';
      }
    }

    return rel;
  }

  String get releaseMajor {
    var rel = release;
    if (rel != null) {
      var idx = rel.indexOf('.');
      if (idx > 0) {
        rel = rel.substring(0, idx);
      }
    }
    return rel;
  }

  String releasesFilePath(BuildSpec spec) {
    var subDir = isReleaseMode
        ? 'release_$releaseMajor'
        : (spec.channel == "stable" ? 'release_master' : 'release_dev');
    var filePath = p.join(
        rootPath, 'releases', subDir, spec.version, 'flutter-intellij.zip');
    return filePath;
  }

  String testTargetPath(BuildSpec spec) {
    var subDir = 'release_master';
    var filePath = p.join(rootPath, 'releases', subDir, 'test_target');
    return filePath;
  }

  String ijVersionPath(BuildSpec spec) {
    var subDir = 'release_master';
    var filePath = p.join(rootPath, 'releases', subDir, spec.ijVersion);
    return filePath;
  }

  Future<int> doit();

  Future<int> run() async {
    await _initGlobals();
    await _initSpecs();
    try {
      return await doit();
    } catch (ex, stack) {
      log(ex.toString());
      log(stack.toString());
      return 1;
    }
  }

  Future<void> _initGlobals() async {
    // Initialization constraint: rootPath depends on arg parsing, and
    // lastReleaseName and lastReleaseDate depend on rootPath.
    rootPath = Directory.current.path;
    var rel = globalResults['cwd'];
    if (rel != null) {
      rootPath = p.normalize(p.join(rootPath, rel));
    }
    if (isDevChannel) {
      lastReleaseName = await lastRelease();
      lastReleaseDate = await dateOfLastRelease();
    }
  }

  Future<int> _initSpecs() async {
    specs = createBuildSpecs(this);
    for (var i = 0; i < specs.length; i++) {
      if (isDevChannel) {
        specs[i].buildForDev();
      }
      await specs[i].initChangeLog();
    }
    return specs.length;
  }
}

/// This represents a BuildSpec that is used to generate the plugin.xml
/// that is used during development. It needs to span all possible versions.
/// The product-matrix.json file lists the versions in increasing build order.
/// The first one is the earliest version used during development and the
/// last one is the latest used during development. This BuildSpec combines
/// those two.
class SyntheticBuildSpec extends BuildSpec {
  BuildSpec alternate;

  SyntheticBuildSpec.fromJson(
      Map json, String releaseNum, List<BuildSpec> specs)
      : super.fromJson(json, releaseNum) {
    alternate = specs.firstWhere((s) => s.isTestTarget);
  }

  String get untilBuild => alternate.untilBuild;

  bool get isSynthetic => true;
}

/// Build the tests if necessary then run them and return any failure code.
class TestCommand extends ProductCommand {
  final BuildCommandRunner runner;

  TestCommand(this.runner) : super('test') {
    argParser.addFlag('unit', negatable: false, help: 'Run unit tests');
    argParser.addFlag('integration',
        negatable: false, help: 'Run integration tests');
  }

  String get description => 'Run the tests for the Flutter plugin.';

  Future<int> doit() async {
    final javaHome = Platform.environment['JAVA_HOME'];
    if (javaHome == null) {
      log('JAVA_HOME environment variable not set - this is needed by gradle.');
      return 1;
    }

    log('JAVA_HOME=$javaHome');

    if (argResults['integration']) {
      return _runIntegrationTests();
    } else {
      return _runUnitTests();
    }
  }

  Future<int> _runUnitTests() async {
    // run './gradlew test'
    if (Platform.isWindows) {
      return await exec('.\\gradlew.bat', ['test']);
    } else {
      return await exec('./gradlew', ['test']);
    }
  }

  Future<int> _runIntegrationTests() async {
    throw 'integration test execution not yet implemented';
  }
}

Future<String> makeDevLog(BuildSpec spec) async {
  if (lastReleaseName == null)
    return ''; // The shallow on travis causes problems.
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var since = lastReleaseName;
  var processResult =
      await gitDir.runCommand(['log', '--oneline', '$since..HEAD']);
  String out = processResult.stdout;
  var messages = out.trim().split('\n');
  var devLog = StringBuffer();
  devLog.writeln('## Changes since ${since.replaceAll('_', ' ')}');
  messages.forEach((m) {
    devLog.writeln(m.replaceFirst(RegExp(r'^[A-Fa-f\d]+\s+'), '- '));
  });
  devLog.writeln();
  return devLog.toString();
}

Future<DateTime> dateOfLastRelease() async {
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var processResult = await gitDir
      .runCommand(['branch', '--list', '-v', '--no-abbrev', lastReleaseName]);
  String out = processResult.stdout;
  var logLine = out.trim().split('\n').first.trim();
  var match =
      RegExp(r'release_\d+\s+([A-Fa-f\d]{40})\s').matchAsPrefix(logLine);
  var commitHash = match.group(1);
  processResult =
      await gitDir.runCommand(['show', '--pretty=tformat:"%cI"', commitHash]);
  out = processResult.stdout;
  var date = out.trim().split('\n').first.trim();
  return DateTime.parse(date.replaceAll('"', ''));
}

Future<String> lastRelease() async {
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var processResult =
      await gitDir.runCommand(['branch', '--list', 'release_*']);
  String out = processResult.stdout;
  var release = out.trim().split('\n').last.trim();
  if (!release.isEmpty) return release;
  processResult =
      await gitDir.runCommand(['branch', '--list', '-a', '*release_*']);
  out = processResult.stdout;
  var remote = out.trim().split('\n').last.trim(); // "remotes/origin/release_43"
  release = remote.substring(remote.lastIndexOf('/') + 1);
  await gitDir.runCommand(['branch', '--track', release, remote]);
  return release;
}

void _checkGitDir() async {
  var isGitDir = await GitDir.isGitDir(rootPath);
  if (!isGitDir) {
    throw 'the current working directory is not managed by git: $rootPath';
  }
}
