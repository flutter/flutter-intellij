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
import 'verify.dart';

Future<int> main(List<String> args) async {
  var runner = BuildCommandRunner();

  runner.addCommand(LintCommand(runner));
  runner.addCommand(GradleBuildCommand(runner));
  runner.addCommand(TestCommand(runner));
  runner.addCommand(DeployCommand(runner));
  runner.addCommand(GenerateCommand(runner));
  runner.addCommand(VerifyCommand(runner));
  runner.addCommand(RunIdeCommand(runner));

  try {
    return await runner.run(args) ?? 0;
  } on UsageException catch (e) {
    print('$e');
    return 1;
  }
}

void addProductFlags(ArgParser argParser, String verb) {
  argParser.addFlag('ij', help: '$verb the IntelliJ plugin', defaultsTo: true);
  argParser.addFlag(
    'as',
    help: '$verb the Android Studio plugin',
    defaultsTo: true,
  );
}

void copyResources({required String from, required String to}) {
  log('copying resources from $from to $to');
  _copyResources(Directory(from), Directory(to));
}

List<BuildSpec> createBuildSpecs(ProductCommand command) {
  var specs = <BuildSpec>[];
  var input = readProductMatrix();
  for (var json in input) {
    specs.add(BuildSpec.fromJson(json, command.release));
  }
  return specs;
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

bool genPresubmitYaml(List<BuildSpec> specs) {
  // This assumes the file contains 'steps:', which seems reasonable.
  var file = File(p.join(rootPath, '.github', 'workflows', 'presubmit.yaml'));
  var versions = [];
  for (var spec in specs) {
    if (spec.channel == 'stable' && !spec.untilBuild.contains('SNAPSHOT')) {
      versions.add(spec.version);
    }
  }

  var templateFile = File(
    p.join(rootPath, '.github', 'workflows', 'presubmit.yaml.template'),
  );
  var templateContents = templateFile.readAsStringSync();
  // If we need to make many changes consider something like genPluginXml().
  templateContents = templateContents.replaceFirst(
    '@VERSIONS@',
    versions.join(', '),
  );
  var header =
      "# Do not edit; instead, modify ${p.basename(templateFile.path)},"
      " and run './bin/plugin generate'.\n\n";
  var contents = header + templateContents;
  log('writing ${p.relative(file.path)}');
  var templateIndex = contents.indexOf('steps:');
  if (templateIndex < 0) {
    log('presubmit template cannot be generated');
    return false;
  }
  var fileContents = file.readAsStringSync();
  var fileIndex = fileContents.indexOf('steps:');
  var newContent =
      contents.substring(0, templateIndex) + fileContents.substring(fileIndex);
  file.writeAsStringSync(newContent, flush: true);
  return true;
}

bool isTravisFileValid() {
  var travisPath = p.join(rootPath, '.github/workflows/presubmit.yaml');
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
  args.addAll(
    Directory(
      directory,
    ).listSync(followLinks: false).map((f) => p.basename(f.path)),
  );
  args.remove('.DS_Store');
  return await exec('jar', args, cwd: directory);
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
      log(
        'the release identifier ("${cmd.release}") must be of the form xx.x (major.minor)',
      );
      return false;
    }
    var gitDir = await GitDir.fromExisting(rootPath);
    var isClean = await gitDir.isWorkingTreeClean();
    if (isClean) {
      var branch = await gitDir.currentBranch();
      var name = branch.branchName;
      var expectedName =
          cmd.isDevChannel ? 'master' : "release_${cmd.releaseMajor}";
      var result = name == expectedName;
      if (!result) {
        result = name.startsWith("release_${cmd.releaseMajor}") &&
            name.lastIndexOf(RegExp(r"\.[0-9]")) == name.length - 2;
      }
      if (result) {
        if (isTravisFileValid()) {
          return result;
        } else {
          log('the presubmit.yaml file needs updating: plugin generate');
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
  // Finally, check that a jxbrowser.properties exists
  var jxBrowserFile = File(
    p.join(rootPath, 'resources', 'jxbrowser', 'jxbrowser.properties'),
  );
  var jxBrowserFileContents = jxBrowserFile.readAsStringSync();
  if (jxBrowserFile.existsSync() &&
      jxBrowserFileContents.isNotEmpty &&
      jxBrowserFileContents.contains('jxbrowser.license.key=') &&
      !jxBrowserFileContents.contains('jxbrowser.license.key=<KEY>')) {
    return true;
  } else {
    log(
      'Release mode requires the jxbrowser.properties file to exist and include a key.',
    );
  }
  return false;
}

List<Map<String, Object?>> readProductMatrix() {
  var contents =
      File(p.join(rootPath, 'product-matrix.json')).readAsStringSync();
  var map = json.decode(contents);
  return (map['list'] as List<Object?>).cast<Map<String, Object?>>();
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

/// Build deployable plugin files. If the --release argument is given
/// then perform additional checks to verify that the release environment
/// is in good order.
class GradleBuildCommand extends ProductCommand {
  @override
  final BuildCommandRunner runner;

  GradleBuildCommand(this.runner) : super('make') {
    argParser.addOption(
      'only-version',
      abbr: 'o',
      help: 'Only build the specified IntelliJ version; useful for sharding '
          'builds on CI systems.',
    );
    argParser.addFlag(
      'unpack',
      abbr: 'u',
      help: 'Unpack the artifact files during provisioning, '
          'even if the cache appears fresh.\n'
          'This flag is ignored if --release is given.',
      defaultsTo: false,
    );
    argParser.addOption(
      'minor',
      abbr: 'm',
      help: 'Set the minor version number.',
    );
    argParser.addFlag('setup', abbr: 's', defaultsTo: true);
  }

  @override
  String get description => 'Build a deployable version of the Flutter plugin, '
      'compiled against the specified artifacts.';

  @override
  Future<int> doit() async {
    final argResults = this.argResults!;
    if (isReleaseMode) {
      if (argResults.flag('unpack')) {
        separator('Release mode (--release) implies --unpack');
      }
      if (!await performReleaseChecks(this)) {
        return 1;
      }
    }

    // Check to see if we should only be building a specific version.
    final onlyVersion = argResults.option('only-version');

    var buildSpecs = specs;
    if (onlyVersion != null && onlyVersion.isNotEmpty) {
      buildSpecs = specs.where((spec) => spec.version == onlyVersion).toList();
      if (buildSpecs.isEmpty) {
        log("No spec found for version '$onlyVersion'");
        return 1;
      }
    }

    final minorNumber = argResults.option('minor');
    if (minorNumber != null) {
      pluginCount = int.parse(minorNumber) - 1;
    }

    var result = 0;
    for (var spec in buildSpecs) {
      if (spec.channel != channel) {
        continue;
      }
      if (!(isForIntelliJ && isForAndroidStudio)) {
        // This is a little more complicated than I'd like because the default
        // is to always do both.
        if (isForAndroidStudio && !spec.isAndroidStudio) continue;
        if (isForIntelliJ && spec.isAndroidStudio) continue;
      }

      pluginCount++;
      if (spec.isDevChannel && !isDevChannel) {
        spec.buildForMaster();
      }

      separator('Building flutter-intellij.jar');
      await removeAll('build');

      log('$spec');

      result = await applyEdits(spec, () async {
        return await externalBuildCommand(spec);
      });
      if (result != 0) {
        log('applyEdits() returned ${result.toString()}');
        return result;
      }

      try {
        result = await savePluginArtifact(spec);
        if (result != 0) {
          return result;
        }
      } catch (ex) {
        log("$ex");
        return 1;
      }

      separator('Built artifact');
      final releaseFilePath = releasesFilePath(spec);
      final file = File(releaseFilePath);
      final fileSize = file.lengthSync() / 1000000;
      log('$releaseFilePath, $fileSize MB');
    }
    if (argResults.option('only-version') == null) {
      checkAndClearAppliedEditCommands();
    }
    // Print a summary of the collection of plugin versions built:
    var builtVersions = buildSpecs.map((spec) => spec.name).toList().join(', ');
    log(
      '\nMake of the ${buildSpecs.length} builds was '
      'successful: $builtVersions.',
    );
    return result;
  }

  Future<int> externalBuildCommand(BuildSpec spec) async {
    var pluginFile = File('resources/META-INF/plugin.xml');
    var studioFile = File('resources/META-INF/studio-contribs.xml');
    var pluginSrc = pluginFile.readAsStringSync();
    var studioSrc = studioFile.readAsStringSync();
    try {
      return await runner.buildPlugin(spec, buildVersionNumber(spec));
    } finally {
      pluginFile.writeAsStringSync(pluginSrc);
      studioFile.writeAsStringSync(studioSrc);
    }
  }

  Future<int> savePluginArtifact(BuildSpec spec) async {
    final file = File(releasesFilePath(spec));

    // Log the contents of ./build/distributions, this is useful in debugging
    // in general and especially useful for the Kokoro bot which is run remotely
    final result = Process.runSync('ls', [
      '-laf',
      '-laf',
      'build/distributions',
    ]);
    log('Content generated in ./build/distributions:\n${result.stdout}');

    var source = File('build/distributions/flutter-intellij.zip');
    if (!source.existsSync()) {
      source = File('build/distributions/flutter-intellij-kokoro.zip');
    }
    _copyFile(source, file.parent, filename: p.basename(file.path));
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

/// Either the --release or --channel options must be provided.
/// The permanent token is read from the file specified by Kokoro.
/// This is used by Kokoro to build and upload the dev version of the plugin.
class DeployCommand extends ProductCommand {
  @override
  final BuildCommandRunner runner;

  DeployCommand(this.runner) : super('deploy');

  @override
  String get description => 'Upload the Flutter plugin to the JetBrains site.';

  @override
  Future<int> doit() async {
    if (!isDevChannel) {
      log('Deploy must have --channel=dev argument');
      return 1;
    }

    var token = readTokenFromKeystore('FLUTTER_KEYSTORE_NAME');
    var value = 0;
    var originalDir = Directory.current;
    var filePath = p.join(
      rootPath,
      'build/distributions/flutter-intellij-kokoro.zip',
    );
    log("uploading $filePath");
    var file = File(filePath);
    changeDirectory(file.parent);
    value = await upload(
      p.basename(file.path),
      '9212', // plugin registry ID for io.flutter
      token,
      'dev',
    );
    if (value != 0) {
      return value;
    }
    changeDirectory(originalDir);
    return value;
  }

  void changeDirectory(Directory dir) {
    Directory.current = dir.path;
  }

  Future<int> upload(
    String filePath,
    String pluginNumber,
    String token,
    String channel,
  ) async {
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
    final out = processResult.stdout as String;
    var message = out.trim().split('\n').last.trim();
    log(message);
    return processResult.exitCode;
  }
}

/// Generate the plugin.xml from the plugin.xml.template file. If the --release
/// argument is given, create a git branch and commit the new file to it,
/// assuming the release checks pass.
///
/// Note: The product-matrix.json file includes a build spec for the EAP version
/// at the end. When the EAP version is released that needs to be updated.
class GenerateCommand extends ProductCommand {
  @override
  final BuildCommandRunner runner;

  GenerateCommand(this.runner) : super('generate');

  @override
  String get description =>
      'Generate plugin.xml, .github/workflows/presubmit.yaml, '
      'and resources/liveTemplates/flutter_miscellaneous.xml files for the '
      'Flutter plugin.\nThe plugin.xml.template and product-matrix.json are '
      'used as input.';

  @override
  Future<int> doit() async {
    if (!genPresubmitYaml(specs)) {
      return 1;
    }
    generateLiveTemplates();
    if (isReleaseMode) {
      if (!await performReleaseChecks(this)) {
        return 1;
      }
    }
    return 0;
  }

  void generateLiveTemplates() {
    // Find all the live templates.
    final templateFragments = Directory(p.join('resources', 'liveTemplates'))
        .listSync()
        .whereType<File>()
        .where((file) => p.extension(file.path) == '.txt')
        .cast<File>()
        .toList();
    final templateFile = File(
      p.join('resources', 'liveTemplates', 'flutter_miscellaneous.xml'),
    );
    var contents = templateFile.readAsStringSync();

    log('writing ${p.relative(templateFile.path)}');

    for (var file in templateFragments) {
      final name = p.basenameWithoutExtension(file.path);

      var replaceContents = file.readAsStringSync();
      replaceContents = replaceContents
          .replaceAll('\n', '&#10;')
          .replaceAll('<', '&lt;')
          .replaceAll('>', '&gt;');

      // look for '<template name="$name" value="..."'
      final regexp = RegExp('<template name="$name" value="([^"]+)"');
      final match = regexp.firstMatch(contents);
      if (match == null) {
        throw 'No entry found for "$name" live template in ${templateFile.path}';
      }

      // Replace the existing content in the xml live template file with the
      // content from the template $name.txt file.
      final matchString = match.group(1);
      final matchStart = contents.indexOf(matchString!);
      contents = contents.substring(0, matchStart) +
          replaceContents +
          contents.substring(matchStart + matchString.length);
    }

    templateFile.writeAsStringSync(contents);
  }
}

/// Run the IDE using the gradle task "runIde"
class RunIdeCommand extends ProductCommand {
  @override
  final BuildCommandRunner runner;

  RunIdeCommand(this.runner) : super('run');

  @override
  String get description => 'Run the IDE plugin';

  @override
  Future<int> doit() async {
    final javaHome = Platform.environment['JAVA_HOME'];
    if (javaHome == null) {
      log('JAVA_HOME environment variable not set - this is needed by Gradle.');
      return 1;
    }
    log('JAVA_HOME=$javaHome');

    // TODO(jwren) The IDE run is determined currently by the isUnitTestTarget
    //  in the product-matrix.json, while useful, a new field should be added
    //  into the product-matrix.json instead of re-using this field,
    //  Or, the run IDE should be passed via the command line (I don't like this
    //  as much.)
    final spec = specs.firstWhere((s) => s.isUnitTestTarget);
    return await _runIde(spec);
  }

  Future<int> _runIde(BuildSpec spec) async {
    // run './gradlew runIde'
    return await applyEdits(spec, () async {
      return await runner.runGradleCommand(['runIde'], spec, '1', 'false');
    });
  }
}

abstract class ProductCommand extends Command<int> {
  @override
  final String name;
  late List<BuildSpec> specs;

  ProductCommand(this.name) {
    addProductFlags(argParser, name[0].toUpperCase() + name.substring(1));
    argParser.addOption(
      'channel',
      abbr: 'c',
      help: 'Select the channel to build: stable or dev',
      defaultsTo: 'stable',
    );
  }

  String get channel => argResults!.option('channel')!;

  bool get isDevChannel => channel == 'dev';

  /// Returns true when running in the context of a unit test.
  bool get isTesting => false;

  bool get isForAndroidStudio => argResults!.flag('as');

  bool get isForIntelliJ => argResults!.flag('ij');

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

  bool get isTestMode => globalResults!.option('cwd') != null;

  String? get release {
    var rel = globalResults!.option('release');

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

  String? get releaseMajor {
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
      rootPath,
      'releases',
      subDir,
      spec.version,
      'flutter-intellij.zip',
    );
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

  @override
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
    var rel = globalResults!.option('cwd');
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
  @override
  final BuildCommandRunner runner;
  String baseDir = Directory.current.path; // Run from flutter-intellij dir.
  late String oldName;
  late String newName;

  RenamePackageCommand(this.runner) : super('rename') {
    argParser.addOption(
      'package',
      defaultsTo: 'com.android.tools.idea.npw',
      help: 'Package to be renamed',
    );
    argParser.addOption(
      'append',
      defaultsTo: 'Old',
      help: 'Suffix to be appended to package name',
    );
    argParser.addOption('new-name', help: 'Name of package after renaming');
    argParser.addFlag(
      'studio',
      negatable: true,
      help: 'The package is in the flutter-studio module',
    );
  }

  @override
  String get description => 'Rename a package in the plugin sources';

  @override
  Future<int> doit() async {
    final argResults = this.argResults!;
    if (argResults.flag('studio')) {
      baseDir = p.join(baseDir, 'flutter-studio/src');
    }
    oldName = argResults.option('package')!;
    newName = argResults.wasParsed('new-name')
        ? argResults.option('new-name')!
        : oldName + argResults.option('append')!;
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

  void _editAll(
    Directory src, {
    required Directory skipOld,
    required Directory skipNew,
  }) {
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
  @override
  final BuildCommandRunner runner;

  TestCommand(this.runner) : super('test') {
    argParser.addFlag(
      'skip',
      negatable: false,
      help: 'Do not run tests, just unpack artifaccts',
      abbr: 's',
    );
    argParser.addFlag('setup', abbr: 'p', defaultsTo: true);
  }

  @override
  String get description => 'Run the tests for the Flutter plugin.';

  @override
  Future<int> doit() async {
    final javaHome = Platform.environment['JAVA_HOME'];
    if (javaHome == null) {
      log('ERROR: JAVA_HOME environment variable not set - this is needed by gradle.');
      return 1;
    }

    log('JAVA_HOME=$javaHome');

    // Case 1: Handle skipping tests
    if (argResults != null && argResults!.flag('skip')) {
      log('Skipping unit tests as requested.');
      return 0;
    }

    // Filter for all unit test targets
    final unitTestTargets = specs.where((s) => s.isUnitTestTarget).toList();

    // Case 2: Zero unit test targets
    if (unitTestTargets.isEmpty) {
      log('ERROR: No unit test target found in the specifications. Cannot run tests.');
      return 1;
    }

    // Case 3: More than one unit test target
    if (unitTestTargets.length > 1) {
      final targetNames = unitTestTargets.map((s) => s.name).join(', ');
      log('ERROR: More than one unit test target found: $targetNames. Please specify which one to run, or ensure only one exists.');
      return 1;
    }

    // Happy Case: Exactly one unit test target
    final spec = unitTestTargets.first;
    return await _runUnitTests(spec);
  }

  Future<int> _runUnitTests(BuildSpec spec) async {
    // run './gradlew test'
    return await applyEdits(spec, () async {
      return await runner.runGradleCommand(['test'], spec, '1', 'true');
    });
  }
}
