// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:args/args.dart';
import 'package:args/command_runner.dart';
import 'package:git/git.dart';
import 'package:path/path.dart' as p;

import 'build_spec.dart';
import 'edit.dart';
import 'globals.dart';
import 'lint.dart';
import 'runner.dart';
import 'util.dart';

Future<int> main(List<String> args) async {
  var runner = new BuildCommandRunner();

  runner.addCommand(new LintCommand(runner));
  runner.addCommand(new AntBuildCommand(runner));
  runner.addCommand(new GradleBuildCommand(runner));
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

Future<int> deleteBuildContents() async {
  final dir = Directory(p.join(rootPath, 'build'));
  if (!dir.existsSync()) throw 'No build directory found';
  var args = List<String>();
  args.add('-rf');
  args.add(p.join(rootPath, 'build', '*'));
  return await exec('rm', args);
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

Future<int> moveToArtifacts(ProductCommand cmd, BuildSpec spec) async {
  final dir = Directory(p.join(rootPath, 'artifacts'));
  if (!dir.existsSync()) throw 'No artifacts directory found';
  var file = pluginRegistryIds[spec.pluginId];
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
  var current =
      RegExp(r'release_(\d+)').matchAsPrefix(lastReleaseName).group(1);
  var val = int.parse(current) + 1;
  return '$val.0';
}

void _copyFile(File file, Directory to, {String filename = ''}) {
  if (!file.existsSync()) {
    throw "${file.path} does not exist";
  }
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

class AntBuildCommand extends BuildCommand {
  AntBuildCommand(BuildCommandRunner runner) : super(runner, 'build');

  Future<int> externalBuildCommand(BuildSpec spec) async {
    var r = await runner.javac2(spec);
    if (r == 0) {
      // copy resources
      copyResources(from: 'src', to: 'build/classes');
      copyResources(from: 'resources', to: 'build/classes');
      copyResources(from: 'gen', to: 'build/classes');
      await genPluginFiles(spec, 'build/classes');
    }
    return r;
  }

  Future<int> savePluginArtifact(BuildSpec spec, String version) async {
    int result;

    // create the jars
    createDir('build/flutter-intellij/lib');
    result = await jar(
      'build/classes',
      'build/flutter-intellij/lib/flutter-intellij.jar',
    );
    if (result != 0) {
      log('jar failed: ${result.toString()}');
      return result;
    }
    if (spec.isTestTarget && !isReleaseMode && !isDevChannel) {
      _copyFile(
        File('build/flutter-intellij/lib/flutter-intellij.jar'),
        Directory(testTargetPath(spec)),
        filename: 'io.flutter.jar',
      );
    }
    if (spec.isAndroidStudio) {
      result = await jar(
        'build/studio',
        'build/flutter-intellij/lib/flutter-studio.jar',
      );
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
      _copyFile(
        File(releasesFilePath(spec)),
        Directory(ijVersionPath(spec)),
        filename: 'flutter-intellij.zip',
      );
    }
    return result;
  }
}

class GradleBuildCommand extends BuildCommand {
  GradleBuildCommand(BuildCommandRunner runner) : super(runner, 'make');

  Future<int> externalBuildCommand(BuildSpec spec) async {
    var pluginFile = File('resources/META-INF/plugin.xml');
    var studioFile = File('resources/META-INF/studio-contribs.xml');
    var pluginSrc = pluginFile.readAsStringSync();
    var studioSrc = studioFile.readAsStringSync();
    try {
      await genPluginFiles(spec, 'resources');
      return await runner.buildPlugin(spec, pluginVersion);
    } finally {
      pluginFile.writeAsStringSync(pluginSrc);
      studioFile.writeAsStringSync(studioSrc);
    }
  }

  Future<int> savePluginArtifact(BuildSpec spec, String version) async {
    final file = File(releasesFilePath(spec));
    var source = File(
        'build/distributions/flutter-intellij-${version}.${pluginCount}.zip');
    if (!source.existsSync()) {
      // Setting the plugin name in Gradle should eliminate the need for this,
      // but it does not.
      // TODO(messick) Find a way to make the Kokoro file name: flutter-intellij-DEV.zip
      source = File('build/distributions/flutter-intellij-kokoro-$version.zip');
    }
    _copyFile(
      source,
      file.parent,
      filename: p.basename(file.path),
    );
    await _stopDaemon();
    return 0;
  }

  Future<int> _stopDaemon() async {
    if (Platform.isWindows) {
      return await exec('.\\gradlew.bat', ['--stop']);
    } else {
      return await exec('./gradlew', ['--stop']);
    }
  }
}

/// Build deployable plugin files. If the --release argument is given
/// then perform additional checks to verify that the release environment
/// is in good order.
abstract class BuildCommand extends ProductCommand {
  final BuildCommandRunner runner;

  BuildCommand(this.runner, String commandName) : super(commandName) {
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
    argParser.addOption('minor',
        abbr: 'm', help: 'Set the minor version number.');
  }

  String get description => 'Build a deployable version of the Flutter plugin, '
      'compiled against the specified artifacts.';

  Future<int> externalBuildCommand(BuildSpec spec);

  Future<int> savePluginArtifact(BuildSpec spec, String version);

  String get pluginVersion => release ?? 'DEV';

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

    String minorNumber = argResults['minor'];
    if (minorNumber != null) {
      pluginCount = int.parse(minorNumber) - 1;
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

      log('spec.version: ${spec.version}');

      final compileFn = () async {
        return await externalBuildCommand(spec);
      };

      result = await applyEdits(spec, compileFn);
      if (result != 0) {
        log('applyEdits() returned ${result.toString()}');
        return result;
      }

      try {
        result = await savePluginArtifact(spec, pluginVersion);
        if (result != 0) {
          return result;
        }
      } catch (ex) {
        log("$ex");
        return 1;
      }

      separator('Built artifact');
      log('${releasesFilePath(spec)}');
    }
    if (argResults['only-version'] == null) {
      checkAndClearAppliedEditCommands();
    }

    return 0;
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

    var token = readTokenFromKeystore('FLUTTER_KEYSTORE_NAME');
    var value = 0;
    var originalDir = Directory.current;
    for (var spec in specs) {
      if (spec.channel != channel) continue;
      var filePath = releasesFilePath(spec);
      log("uploading $filePath");
      var file = File(filePath);
      changeDirectory(file.parent);
      var pluginNumber = pluginRegistryIds[spec.pluginId];
      value = await upload(
          p.basename(file.path), pluginNumber, token, spec.channel);
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

/// A crude rename utility. The IntelliJ feature does not work on the case
/// needed. This just substitutes package names and assumes all are FQN-form.
/// It does not update forms; they use paths instead of packages.
/// It would be easy to do forms but it isn't worth the trouble. Only one
/// had to be edited.
class RenamePackageCommand extends ProductCommand {
  final BuildCommandRunner runner;
  String baseDir = Directory.current.path; // Run from flutter-intellij dir.
  String oldName, newName;

  RenamePackageCommand(this.runner) : super('rename') {
    argParser.addOption('package',
        defaultsTo: 'com.android.tools.idea.npw',
        help: 'Package to be renamed');
    argParser.addOption('append',
        defaultsTo: 'Old', help: 'Suffix to be appended to package name');
    argParser.addOption('new-name', help: 'Name of package after renaming');
    argParser.addFlag('studio',
        negatable: true, help: 'The package is in the flutter-studio module');
  }

  String get description => 'Rename a package in the plugin sources';

  Future<int> doit() async {
    if (argResults['studio']) baseDir = p.join(baseDir, 'flutter-studio/src');
    oldName = argResults['package'];
    newName = argResults.wasParsed('new-name')
        ? argResults['new-name']
        : oldName + argResults['append'];
    if (oldName == newName) {
      log('Nothing to do; new name is same as old name');
      return 1;
    }
    // TODO(messick) If the package is not in flutter-studio then we need to edit it too
    moveFiles();
    editReferences();
    await deleteDir();
    return 0;
  }

  void moveFiles() {
    final srcDir = Directory(p.join(baseDir, oldName.replaceAll('.', '/')));
    final destDir = Directory(p.join(baseDir, newName.replaceAll('.', '/')));
    _editAndMoveAll(srcDir, destDir);
  }

  void editReferences() {
    final srcDir = Directory(p.join(baseDir, oldName.replaceAll('.', '/')));
    final destDir = Directory(p.join(baseDir, newName.replaceAll('.', '/')));
    _editAll(Directory(baseDir), skipOld: srcDir, skipNew: destDir);
  }

  Future<int> deleteDir() async {
    final dir = Directory(p.join(baseDir, oldName.replaceAll('.', '/')));
    await dir.delete(recursive: true);
    return 0;
  }

  void _editAndMoveFile(File file, Directory to) {
    if (!to.existsSync()) {
      to.createSync(recursive: true);
    }
    final filename = p.basename(file.path);
    if (filename.startsWith('.')) return;
    final target = File(p.join(to.path, filename));
    var source = file.readAsStringSync();
    source = source.replaceAll(oldName, newName);
    target.writeAsStringSync(source);
    if (to.path != file.parent.path) file.deleteSync();
  }

  void _editAndMoveAll(Directory from, Directory to) {
    for (var entity in from.listSync(followLinks: false)) {
      final basename = p.basename(entity.path);

      if (entity is File) {
        _editAndMoveFile(entity, to);
      } else if (entity is Directory) {
        _editAndMoveAll(entity, Directory(p.join(to.path, basename)));
      }
    }
  }

  void _editAll(Directory src, {Directory skipOld, Directory skipNew}) {
    if (src.path == skipOld.path || src.path == skipNew.path) return;
    for (var entity in src.listSync(followLinks: false)) {
      if (entity is File) {
        _editAndMoveFile(entity, src);
      } else if (entity is Directory) {
        _editAll(entity, skipOld: skipOld, skipNew: skipNew);
      }
    }
  }
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

    final spec = specs.firstWhere((s) => s.isUnitTestTarget);
    await spec.artifacts.provision(rebuildCache: true);
    if (argResults['integration']) {
      return _runIntegrationTests();
    } else {
      return _runUnitTests(spec);
    }
  }

  Future<int> _runUnitTests(BuildSpec spec) async {
    // run './gradlew test'
    final compileFn = () async {
      return await runner.runGradleCommand(['test'], spec, '1', 'true');
    };
    return await applyEdits(spec, compileFn);
  }

  Future<int> _runIntegrationTests() async {
    throw 'integration test execution not yet implemented';
  }
}
