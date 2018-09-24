// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:args/args.dart';
import 'package:args/command_runner.dart';
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

String rootPath;
int pluginCount = 0;

void addProductFlags(ArgParser argParser, String verb) {
  argParser.addFlag('ij', help: '$verb the IntelliJ plugin', defaultsTo: true);
  argParser.addFlag('as',
      help: '$verb the Android Studio plugin', defaultsTo: true);
}

void copyResources({String from, String to}) {
  log('copying resources from $from to $to');
  _copyResources(new Directory(from), new Directory(to));
}

List<BuildSpec> createBuildSpecs(ProductCommand command) {
  var specs = new List<BuildSpec>();
  var input = readProductMatrix();
  input.forEach((json) {
    specs.add(new BuildSpec.fromJson(json, command.release));
  });
  return specs;
}

void createDir(String name) {
  final dir = new Directory(name);
  if (!dir.existsSync()) {
    log('creating $name/');
    dir.createSync(recursive: true);
  }
}

Future<int> curl(String url, {String to}) async {
  return await exec('curl', ['-o', to, url]);
}

Future<int> deleteBuildContents() async {
  final dir = new Directory(p.join(rootPath, 'build'));
  if (!dir.existsSync()) throw 'No build directory found';
  var args = new List<String>();
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
  final dir = new Directory(path);
  return dir
      .listSync(recursive: true, followLinks: false)
      .where((e) => e.path.endsWith('.jar'))
      .toList();
}

List<String> findJavaFiles(String path) {
  final dir = new Directory(path);
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
      await new File(p.join(rootPath, destDir, path)).create(recursive: true);
  log('writing ${p.relative(file.path)}');
  var dest = file.openWrite();
  dest.writeln(
      "<!-- Do not edit; instead, modify ${p.basename(templatePath)}, and run './bin/plugin generate'. -->");
  dest.writeln();
  await new File(p.join(rootPath, 'resources', templatePath))
      .openRead()
      .transform(utf8.decoder)
      .transform(new LineSplitter())
      .forEach((l) => dest.writeln(substituteTemplateVariables(l, spec)));
  await dest.close();
  return await dest.done;
}

void genTravisYml(List<BuildSpec> specs) {
  var file = new File(p.join(rootPath, '.travis.yml'));
  var env = '';
  for (var spec in specs) {
    env += '  - IDEA_VERSION=${spec.version}\n';
  }

  var templateFile = new File(p.join(rootPath, '.travis_template.yml'));
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
  var dir = new Directory(dirPath);
  if (!dir.existsSync()) {
    return false;
  }
  var filePath = artifact.file;
  var file = new File(p.join(rootPath, 'artifacts', filePath));
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
  var travisFile = new File(travisPath);
  if (!travisFile.existsSync()) {
    return false;
  }
  var matrixPath = p.join(rootPath, 'product-matrix.json');
  var matrixFile = new File(matrixPath);
  if (!matrixFile.existsSync()) {
    throw 'product-matrix.json is missing';
  }
  return isNewer(travisFile, matrixFile);
}

Future<int> jar(String directory, String outFile) async {
  var args = ['cf', p.absolute(outFile)];
  args.addAll(new Directory(directory)
      .listSync(followLinks: false)
      .map((f) => p.basename(f.path)));
  args.remove('.DS_Store');
  return await exec('jar', args, cwd: directory);
}

void log(String s, {bool indent: true}) {
  indent ? print('  $s') : print(s);
}

Future<int> moveToArtifacts(ProductCommand cmd, BuildSpec spec) async {
  final dir = new Directory(p.join(rootPath, 'artifacts'));
  if (!dir.existsSync()) throw 'No artifacts directory found';
  var file = plugins[spec.pluginId];
  var args = new List<String>();
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
      return new Future(() => true);
    }
    if (!cmd.isReleaseValid) {
      log('the release identifier ("${cmd.release}") must be of the form xx.x (major.minor)');
      return new Future(() => false);
    }
    var gitDir = await GitDir.fromExisting(rootPath);
    var isClean = await gitDir.isWorkingTreeClean();
    if (isClean) {
      var branch = await gitDir.getCurrentBranch();
      var name = branch.branchName;
      var result = name == "release_${cmd.releaseMajor}";
      if (result) {
        if (isTravisFileValid()) {
          return new Future(() => result);
        } else {
          log('the .travis.yml file needs updating: plugin generate');
        }
      } else {
        log('the current git branch must be named "release_${cmd.releaseMajor}"');
      }
    } else {
      log('the current git branch has uncommitted changes');
    }
  } else {
    log('the current working directory is not managed by git: $rootPath');
  }
  return new Future(() => false);
}

List readProductMatrix() {
  var contents =
      new File(p.join(rootPath, 'product-matrix.json')).readAsStringSync();
  var map = json.decode(contents);
  return map['list'];
}

Future<int> removeAll(String dir) async {
  var args = ['-rf', dir];
  return await exec('rm', args);
}

void separator(String name) {
  log('');
  log('$name:', indent: false);
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
        return spec.release == null
            ? ''
            : '<version>${spec.release}.${++pluginCount}</version>';
      case 'CHANGELOG':
        return spec.changeLog;
      case 'DEPEND':
        // If found, this is the module that triggers loading the Android Studio
        // support. The public sources and the installable plugin use different ones.
        return spec.isSynthetic
            ? 'com.intellij.modules.androidstudio'
            : 'com.android.tools.apk';
      case 'PROJECTSYSTEM':
        // Temporary work-around for 3.0 vs 3.1 AS incompatibility.
        // TODO(messick) Delete this when we are SURE we do not need to build version < 3.1
        return spec.version == '3.1'
            ? '<projectsystem implementation="io.flutter.project.FlutterProjectSystemProvider"/>'
            : '';
      default:
        throw 'unknown template variable: $name';
    }
  }

  var start = line.indexOf('@');
  while (start >= 0 && start < line.length) {
    var end = line.indexOf('@', start + 1);
    var name = line.substring(start + 1, end);
    line = line.replaceRange(start, end + 1, valueOf(name));
    if (end < line.length - 1) {
      start = line.indexOf('@', end + 1);
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

void _copyFile(File file, Directory to) {
  if (!to.existsSync()) {
    to.createSync(recursive: true);
  }
  final target = new File(p.join(to.path, p.basename(file.path)));
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
    } else {
      _copyResources(entity, new Directory(p.join(to.path, basename)));
    }
  }
}

String _shorten(String str) {
  return str.length < 200
      ? str
      : str.substring(0, 170) + ' ... ' + str.substring(str.length - 30);
}

Stream<String> _toLineStream(Stream<List<int>> s, Encoding encoding) =>
    s.transform(encoding.decoder).transform(const LineSplitter());

class Artifact {
  final String file;
  final bool bareArchive;
  String output;

  Artifact(this.file, {this.bareArchive: false, this.output}) {
    if (output == null) {
      output = file.substring(0, file.lastIndexOf('-'));
    }
  }

  bool get isZip => file.endsWith('.zip');

  String get outPath => p.join(rootPath, 'artifacts', output);
}

class ArtifactManager {
  final String base =
      'https://storage.googleapis.com/flutter_infra/flutter/intellij';

  final List<Artifact> artifacts = [];

  Artifact javac;

  ArtifactManager() {
    javac = add(new Artifact(
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
      final path = 'artifacts/${artifact.file}';
      if (FileSystemEntity.isFileSync(path)) {
        log('$path exists in cache');
      } else {
        log('downloading $path...');
        result = await curl('$base/${artifact.file}', to: path);
        if (result != 0) {
          log('download failed');
          break;
        }
        var archiveFile = new File(path);
        if (!archiveFile.existsSync() || archiveFile.lengthSync() < 200) {
          // If the file is missing the server returns a small file containing
          // an error message. Delete it and fail. The smallest file we store in
          // the cloud is over 700K.
          log('archive file not found: $base/${artifact.file}');
          archiveFile.deleteSync();
          result = 1;
          break;
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
      if (new Directory(artifact.outPath).existsSync()) {
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
    return new Future(() => result);
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
      if (!(isForIntelliJ && isForAndroidStudio)) {
        // This is a little more complicated than I'd like because the default
        // is to always do both.
        if (isForAndroidStudio && !spec.isAndroidStudio) continue;
        if (isForIntelliJ && spec.isAndroidStudio) continue;
      }

      result = await spec.artifacts.provision(
          rebuildCache:
              isReleaseMode || argResults['unpack'] || buildSpecs.length > 1);
      if (result != 0) {
        return new Future(() => result);
      }

      separator('Building flutter-intellij.jar');
      await removeAll('build');

      // Handle skipped files.
      for (String file in spec.filesToSkip) {
        var entity =
            FileSystemEntity.isFileSync(file) ? File(file) : Directory(file);
        if (entity.existsSync()) {
          await entity.rename('$file~');
        }
      }

      // TODO(devoncarew): Remove this when we no longer support AS 3.1.
      File processedFile1, processedFile2, processedFile3;
      String oldSource1, oldSource2, oldSource3, newSource;
      if (spec.version == '2017.3') {
        processedFile1 = new File(
            'flutter-studio/src/io/flutter/module/FlutterDescriptionProvider.java');
        log('Editing ${processedFile1.path}');
        oldSource1 = processedFile1.readAsStringSync();
        newSource = oldSource1;
        newSource = newSource.replaceAll(
          'import com.android.tools.idea.npw.model.NewModuleModel',
          'import com.android.tools.idea.npw.module.NewModuleModel',
        );
        newSource = newSource.replaceAll(
          'import java.awt.*',
          'import javax.swing.*',
        );
        newSource = newSource.replaceAll('public Image', 'public Icon');
        newSource =
            newSource.replaceAll('IconUtil.toImage', ''); // Leaves parens
        processedFile1.writeAsStringSync(newSource);
        processedFile2 = new File(
            'flutter-studio/src/io/flutter/project/ChoseProjectTypeStep.java');
        log('Editing ${processedFile2.path}');
        oldSource2 = processedFile2.readAsStringSync();
        newSource = oldSource2;
        newSource = newSource.replaceAll(
            "import com.intellij.ui.components.JBScrollPane;",
            "import com.intellij.ui.components.JBScrollPane;\nimport com.intellij.util.IconUtil;");
        newSource = newSource.replaceAll(
            'image.getIcon()', 'IconUtil.toImage(image.getIcon())');
        processedFile2.writeAsStringSync(newSource);
        processedFile3 = new File(
            'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java');
        oldSource3 = processedFile3.readAsStringSync();
        newSource = oldSource3;
        newSource = newSource.replaceAll('.LightResourceClassService', '.*');
        newSource =
            newSource.replaceAll(' LightResourceClassService', ' Object');
        processedFile3.writeAsStringSync(newSource);
      } else if (spec.version == '2018.1') {
        processedFile3 = new File(
            'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java');
        oldSource3 = processedFile3.readAsStringSync();
        newSource = oldSource3;
        newSource = newSource.replaceAll('.LightResourceClassService', '.*');
        newSource =
            newSource.replaceAll(' LightResourceClassService', ' Object');
        processedFile3.writeAsStringSync(newSource);
      }

      try {
        result = await runner.javac2(spec);
      } finally {
        // Restore 3.2 sources.
        if (oldSource1 != null) {
          log('Restoring ${processedFile1.path}');
          processedFile1.writeAsStringSync(oldSource1);
        }
        if (oldSource2 != null) {
          log('Reestoring ${processedFile2.path}');
          processedFile2.writeAsStringSync(oldSource2);
        }
        if (oldSource3 != null) {
          log('Reestoring ${processedFile3.path}');
          processedFile3.writeAsStringSync(oldSource3);
        }

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
        return new Future(() => result);
      }

      // copy resources
      copyResources(from: 'src', to: 'build/classes');
      copyResources(from: 'resources', to: 'build/classes');
      copyResources(from: 'gen', to: 'build/classes');
      await genPluginFiles(spec, 'build/classes');

      // create the jars
      createDir('build/flutter-intellij/lib');
      result = await jar(
          'build/classes', 'build/flutter-intellij/lib/flutter-intellij.jar');
      if (result != 0) {
        log('jar failed: ${result.toString()}');
        return new Future(() => result);
      }
      if (spec.isAndroidStudio) {
        result = await jar(
            'build/studio', 'build/flutter-intellij/lib/flutter-studio.jar');
        if (result != 0) {
          log('jar failed: ${result.toString()}');
          return new Future(() => result);
        }
      }

      // zip it up
      result = await zip('build/flutter-intellij', releasesFilePath(spec));
      if (result != 0) {
        log('zip failed: ${result.toString()}');
        return new Future(() => result);
      }
      separator('BUILT');
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

  // Use this to compile test code, which should not define forms.
  Future<int> javac(
      {List sourcepath,
      String destdir,
      List classpath,
      List<String> sources}) async {
    //final Directory javacDir = new Directory('artifacts/${artifacts.javac.output}');

    final args = [
      '-d',
      destdir,
      '-encoding',
      'UTF-8',
      '-source',
      '8',
      '-target',
      '8',
      '-classpath',
      classpath.join(':'),
      '-sourcepath',
      sourcepath.join(':'),
    ];
    args.addAll(sources);

    return await exec('javac', args);
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
      return await exec('ant', args.split(new RegExp(r'\s')));
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
  final String ideaProduct;
  final String ideaVersion;
  final String dartPluginVersion;

  // plugin.xml variables
  final String sinceBuild;
  final String untilBuild;
  final String pluginId = 'io.flutter';
  final String release;
  final List<dynamic> filesToSkip;
  String _changeLog;

  ArtifactManager artifacts = new ArtifactManager();

  Artifact product;
  Artifact dartPlugin;

  BuildSpec.fromJson(Map json, String releaseNum)
      : release = releaseNum,
        name = json['name'],
        version = json['version'],
        ideaProduct = json['ideaProduct'],
        ideaVersion = json['ideaVersion'],
        dartPluginVersion = json['dartPluginVersion'],
        sinceBuild = json['sinceBuild'],
        untilBuild = json['untilBuild'],
        filesToSkip = json['filesToSkip'] ?? [] {
    createArtifacts();
  }

  bool get isAndroidStudio => ideaProduct.contains('android-studio');

  bool get isReleaseMode => release != null;

  bool get isSynthetic => false;

  String get productFile => isAndroidStudio ? "$ideaProduct-ide" : ideaProduct;

  String get changeLog {
    if (_changeLog == null) {
      _changeLog = _parseChangelog();
    }

    return _changeLog;
  }

  void createArtifacts() {
    if (ideaProduct == 'android-studio') {
      product = artifacts.add(new Artifact(
          '$ideaProduct-ide-$ideaVersion-linux.zip',
          output: ideaProduct));
    } else {
      product = artifacts.add(new Artifact('$ideaProduct-$ideaVersion.tar.gz',
          output: ideaProduct));
    }
    dartPlugin = artifacts.add(new Artifact('Dart-$dartPluginVersion.zip'));
  }

  String _parseChangelog() {
    var text = new File('CHANGELOG.md').readAsStringSync();
    var html = markdownToHtml(text);
    return html
        .replaceAll('</h2><ul>', '</h2>\n<ul>')
        .replaceAll('<ul><li>', '<ul>\n  <li>')
        .replaceAll('</li><li>', '</li>\n  <li>')
        .replaceAll('</li></ul>', '</li>\n</ul>')
        .replaceAll('<li>\n<p>', '<li><p>');
  }

  String toString() {
    return 'BuildSpec($ideaProduct $ideaVersion $dartPluginVersion $sinceBuild '
        '$untilBuild version: "$release")';
  }
}

/// Prompt for the JetBrains account password then upload
/// the plugin distribution files to the JetBrains site.
/// The --release argument is not optional.
class DeployCommand extends ProductCommand {
  final BuildCommandRunner runner;
  String username;
  String tempDir;

  DeployCommand(this.runner) : super('deploy');

  String get description => 'Upload the Flutter plugin to the JetBrains site.';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (!await performReleaseChecks(this)) {
        return new Future(() => 1);
      }
    } else {
      log('Deploy must have a --release argument');
      return new Future(() => 1);
    }

    String password;

    if (isTesting) {
      password = "hello"; // For testing.
      username = "test";
    } else {
      var mode = stdin.echoMode;
      stdout.writeln('Please enter the username and password '
          'for the JetBrains plugin repository');
      stdout.write('Username: ');
      username = stdin.readLineSync();
      stdout.write('Password: ');
      stdin.echoMode = false;
      password = stdin.readLineSync();
      stdin.echoMode = mode;
    }

    var directory = Directory.systemTemp.createTempSync('plugin');
    tempDir = directory.path;
    var file = new File('$tempDir/.content');
    file.writeAsStringSync(password, flush: true);

    var value = 0;
    try {
      for (var spec in specs) {
        var filePath = releasesFilePath(spec);
        value = await upload(filePath, plugins[spec.pluginId]);
        if (value != 0) {
          return value;
        }
      }
    } finally {
      file.deleteSync();
      directory.deleteSync();
    }
    return value;
  }

  Future<int> upload(String filePath, String pluginNumber) async {
    if (!new File(filePath).existsSync()) {
      throw 'File not found: $filePath';
    }
    log("uploading $filePath");
    var args = '''
-i 
-F userName="${username}" 
-F password="<${tempDir}/.content" 
-F pluginId="$pluginNumber" 
-F file="@$filePath" 
"https://plugins.jetbrains.com/plugin/uploadPlugin"
''';

    final process = await Process.start('curl', args.split(new RegExp(r'\s')));
    var result = await process.exitCode;
    if (result != 0) {
      log('Upload failed: ${result.toString()} for file: $filePath');
    }
    return result;
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
    var spec = new SyntheticBuildSpec.fromJson(json.first, release, json.last);
    var value = 1;
    var result = await genPluginFiles(spec, 'resources');
    if (result != null) {
      genTravisYml(specs);
      value = 0;
    }
    if (isReleaseMode) {
      if (!await performReleaseChecks(this)) {
        return new Future(() => 1);
      }
    }
    return new Future(() => value);
  }

  SyntheticBuildSpec makeSyntheticSpec(List specs) =>
      new SyntheticBuildSpec.fromJson(specs[0], release, specs[2]);
}

abstract class ProductCommand extends Command {
  final String name;
  List<BuildSpec> specs;

  ProductCommand(this.name) {
    addProductFlags(argParser, name[0].toUpperCase() + name.substring(1));
  }

  /// Returns true when running in the context of a unit test.
  bool get isTesting => false;

  bool get isForAndroidStudio => argResults['as'];

  bool get isForIntelliJ => argResults['ij'];

  bool get isReleaseMode => release != null;

  bool get isReleaseValid {
    var rel = release;
    if (rel == null) {
      return false;
    }
    // Validate for '00.0'.
    return rel == new RegExp(r'\d+\.\d').stringMatch(rel);
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
    var subDir = isReleaseMode ? 'release_$releaseMajor' : 'release_master';
    var filePath = p.join(
        rootPath, 'releases', subDir, spec.version, 'flutter-intellij.zip');
    return filePath;
  }

  Future<int> doit();

  Future<int> run() async {
    rootPath = Directory.current.path;
    var rel = globalResults['cwd'];
    if (rel != null) {
      rootPath = p.normalize(p.join(rootPath, rel));
    }
    specs = createBuildSpecs(this);
    try {
      return await doit();
    } catch (ex, stack) {
      log(ex.toString());
      log(stack.toString());
      return new Future(() => 1);
    }
  }
}

/// This represents a BuildSpec that is used to generate the plugin.xml
/// that is used during development. It needs to span all possible versions.
/// The product-matrix.json file lists the versions in increasing build order.
/// The first one is the earliest version used during development and the
/// last one is the latest used during development. This BuildSpec combines
/// those two.
class SyntheticBuildSpec extends BuildSpec {
  final Map alternate;

  SyntheticBuildSpec.fromJson(Map json, String releaseNum, this.alternate)
      : super.fromJson(json, releaseNum);

  String get untilBuild => alternate['untilBuild'];

  bool get isSynthetic => true;
}

/// Build the tests if necessary then run them and return any failure code.
class TestCommand extends ProductCommand {
  final BuildCommandRunner runner;

  TestCommand(this.runner) : super('test') {
    argParser.addFlag('unit',
        abbr: 'u', defaultsTo: true, help: 'Run unit tests');
    argParser.addFlag('integration',
        abbr: 'i', defaultsTo: false, help: 'Run integration tests');
  }

  String get description => 'Run the tests for the Flutter plugin.';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (!await performReleaseChecks(this)) {
        return new Future(() => 1);
      }
    }

    for (var spec in specs) {
      await spec.artifacts.provision();

      //TODO(messick) Finish the implementation of TestCommand.
      separator('Compiling test sources');

      var jars = []
        ..addAll(findJars('${spec.dartPlugin.outPath}/lib'))
        ..addAll(findJars('${spec.product.outPath}/lib')); //TODO: also, plugins

      var sourcepath = [
        'testSrc',
        'resources',
        'gen',
        'third_party/intellij-plugins-dart/testSrc'
      ];
      createDir('build/classes');

      await runner.javac(
        sources: sourcepath.expand(findJavaFiles).toList(),
        sourcepath: sourcepath,
        destdir: 'build/classes',
        classpath: jars.map((j) => j.path).toList(),
      );
    }
    throw 'unimplemented';
  }
}
