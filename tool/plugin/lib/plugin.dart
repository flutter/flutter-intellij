// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:args/args.dart';
import 'package:args/command_runner.dart';
import 'package:git/git.dart';
import 'package:path/path.dart' as p;

import 'src/lint.dart';

Future<int> main(List<String> args) async {
  var runner = new BuildCommandRunner();

  runner.addCommand(new LintCommand(runner));
  runner.addCommand(new AntBuildCommand(runner));
  runner.addCommand(new BuildCommand(runner));
  runner.addCommand(new TestCommand(runner));
  runner.addCommand(new DeployCommand(runner));
  runner.addCommand(new GenCommand(runner));

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

const String travisHeader = r'''
language: java

jdk:
  - oraclejdk8

install: true

before_script:
  - "export DISPLAY=:99.0"
  - "sh -e /etc/init.d/xvfb start"
  - sleep 3 # give xvfb some time to start
  - git clone https://github.com/flutter/flutter.git --depth 1
  - export PATH="$PATH":`pwd`/flutter/bin:`pwd`/flutter/bin/cache/dart-sdk/bin
  - flutter config --no-analytics
  - flutter doctor
  - export FLUTTER_SDK=`pwd`/flutter

# execution
script: ./tool/travis.sh

# Testing product matrix - see gs://flutter_infra/flutter/intellij/.
# IDEA_PRODUCT can be one of ideaIC, or android-studio-ide.
# TODO(devoncarew): Re-enable unit testing on the bots (UNIT_TEST=true).
env:
''';

String rootPath;

void addProductFlags(ArgParser argParser, String verb) {
  argParser.addFlag('ij', help: '$verb the IntelliJ plugin', defaultsTo: true);
  argParser.addFlag('as',
      help: '$verb the Android Studio plugin', defaultsTo: true);
}

Future<int> ant(BuildSpec spec) async {
  var args = new List<String>();
  String directory = null;
  args.add('-Ddart.plugin.version=${spec.dartPluginVersion}');
  args.add('-Didea.version=${spec.ideaVersion}');
  args.add('-Didea.product=${spec.ideaProduct}');
  args.add('-DSINCE=${spec.sinceBuild}');
  args.add('-DUNTIL=${spec.untilBuild}');
  return await exec('ant', args, cwd: directory);
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
  _toLineStream(process.stderr, SYSTEM_ENCODING).listen(log);
  _toLineStream(process.stdout, SYSTEM_ENCODING).listen(log);

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

Future<File> genPluginXml(BuildSpec spec, String destDir) async {
  var file = await new File(p.join(rootPath, destDir, 'META-INF/plugin.xml'))
      .create(recursive: true);
  var dest = file.openWrite();
  //TODO(devoncarew): Move the change log to a separate file and insert it here.
  await new File(p.join(rootPath, 'resources/META-INF/plugin.xml.template'))
      .openRead()
      .transform(UTF8.decoder)
      .transform(new LineSplitter())
      .forEach((l) => dest.writeln(substitueTemplateVariables(l, spec)));
  await dest.close();
  return await dest.done;
}

void genTravisYml(List<BuildSpec> specs) {
  String envLine(String p, String i, String d) =>
      '  - IDEA_PRODUCT=$p IDEA_VERSION=$i DART_PLUGIN_VERSION=$d\n';
  var file = new File(p.join(rootPath, '.travis.yml'));
  var env = '';
  for (var spec in specs) {
    env += envLine(spec.ideaProduct, spec.ideaVersion, spec.dartPluginVersion);
  }
  var contents = travisHeader + env;
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
  args.add(cmd.archiveFilePath(spec));
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
    var gitDir = await GitDir.fromExisting(rootPath);
    var isClean = await gitDir.isWorkingTreeClean();
    if (isClean) {
      var branch = await gitDir.getCurrentBranch();
      var name = branch.branchName;
      var result = name == "release_${cmd.release}";
      if (result) {
        if (isTravisFileValid()) {
          return new Future(() => result);
        } else {
          log('the .travis.yml file needs updating: plugin gen');
        }
      } else {
        log('the current git branch must be named "$name"');
      }
    } else {
      log('the current git branch has uncommitted changes');
    }
  } else {
    log('the currect working directory is not managed by git: $rootPath');
  }
  return new Future(() => false);
}

List readProductMatrix() {
  var contents =
      new File(p.join(rootPath, 'product-matrix.json')).readAsStringSync();
  var map = JSON.decode(contents);
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

String substitueTemplateVariables(String line, BuildSpec spec) {
  String valueOf(String name) {
    switch (name) {
      case 'PLUGINID':
        return spec.pluginId;
      case 'SINCE':
        return spec.sinceBuild;
      case 'UNTIL':
        return spec.untilBuild;
      case 'VERSION':
        return spec.release == null ? '' : '<version>${spec.release}</version>';
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

String _shorten(String s) {
  if (s.length < 200) {
    return s;
  }
  return s.substring(0, 170) + ' ... ' + s.substring(s.length - 30);
}

Stream<String> _toLineStream(Stream<List<int>> s, Encoding encoding) =>
    s.transform(encoding.decoder).transform(const LineSplitter());

/// Temporary command to use the Ant build script.
class AntBuildCommand extends ProductCommand {
  final BuildCommandRunner runner;

  AntBuildCommand(this.runner);

  String get description => 'Build a deployable version of the Flutter plugin, '
      'compiled against the specified artifacts.';

  String get name => 'abuild';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (!await performReleaseChecks(this)) {
        return new Future(() => 1);
      }
    }
    var value;
    for (var spec in specs) {
      await spec.artifacts.provision(); // Not needed for ant script.
      await deleteBuildContents();
      value = await ant(spec);
      if (value != 0) {
        return value;
      }
      value = await moveToArtifacts(this, spec);
    }
    return value;
  }
}

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
      }

      // clear unpacked cache
      if (rebuildCache || !FileSystemEntity.isDirectorySync(artifact.outPath)) {
        await removeAll(artifact.outPath);
      }
      if (isCacheDirectoryValid(artifact)) {
        continue;
      }

      // expand
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

  BuildCommand(this.runner) {
    argParser.addFlag('unpack',
        abbr: 'u',
        help: 'Unpack the artifact files during provisioning, '
            'even if the cache appears fresh.\n'
            'This flag is ignored if --release is given.',
        defaultsTo: false);
  }

  String get description => 'Build a deployable version of the Flutter plugin, '
      'compiled against the specified artifacts.';

  String get name => 'build';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (argResults['unpack']) {
        separator('Release mode (--release) implies --unpack');
      }
      if (!await performReleaseChecks(this)) {
        return new Future(() => 1);
      }
    }
    var result = 0;
    for (var spec in specs) {
      result = await spec.artifacts
          .provision(rebuildCache: isReleaseMode || argResults['unpack']);
      if (result != 0) {
        return new Future(() => result);
      }

      separator('Building flutter-intellij.jar');
      await removeAll('build');
      result = await runner.javac2(spec);
      if (result != 0) {
        return new Future(() => result);
      }

      // copy resources
      copyResources(from: 'src', to: 'build/classes');
      copyResources(from: 'resources', to: 'build/classes');
      copyResources(from: 'gen', to: 'build/classes');
      copyResources(
          from: 'third_party/intellij-plugins-dart/src', to: 'build/classes');
      await genPluginXml(spec, 'build/classes');

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
      result = await zip('build/flutter-intellij', archiveFilePath(spec));
      if (result != 0) {
        log('zip failed: ${result.toString()}');
        return new Future(() => result);
      }
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
    return await exec('ant', args.split(new RegExp(r'\s')));
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
        untilBuild = json['untilBuild'] {
    createArtifacts();
  }

  bool get isAndroidStudio => ideaProduct.contains('android-studio');

  bool get isReleaseMode => release != null;

  void createArtifacts() {
    if (ideaProduct == 'android-studio-ide') {
      product = artifacts.add(new Artifact(
          '$ideaProduct-$ideaVersion-linux.zip',
          output: ideaProduct));
    } else {
      product = artifacts.add(new Artifact('$ideaProduct-$ideaVersion.tar.gz',
          output: ideaProduct));
    }
    dartPlugin = artifacts.add(new Artifact('Dart-$dartPluginVersion.zip'));
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

  DeployCommand(this.runner);

  String get description => 'Upload the Flutter plugin to the JetBrains site.';

  String get name => 'deploy';

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
    try {
      // Detect test mode early to keep stdio clean for the test results parser.
      var mode = stdin.echoMode;
      stdout.writeln(
          'Please enter the username and password for the JetBrains plugin repository');
      stdout.write('Username: ');
      username = stdin.readLineSync();
      stdout.write('Password: ');
      stdin.echoMode = false;
      password = stdin.readLineSync();
      stdin.echoMode = mode;
    } on StdinException {
      password = "hello"; // For testing.
      username = "test";
    }

    var directory = Directory.systemTemp.createTempSync('plugin');
    tempDir = directory.path;
    var file = new File('$tempDir/.content');
    file.writeAsStringSync(password, flush: true);

    var value = 0;
    try {
      for (var spec in specs) {
        var filePath = archiveFilePath(spec);
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
/// Note: The product-matrix.json file includes a build spec for the
/// EAP version at the end. When the EAP version is released that needs
/// to be updated.
class GenCommand extends ProductCommand {
  final BuildCommandRunner runner;

  GenCommand(this.runner);

  String get description =>
      'Generate a valid plugin.xml and .travis.yml for the Flutter plugin.\n'
      'The plugin.xml.template and product-matrix.json are used as input.';

  String get name => 'gen';

  Future<int> doit() async {
    var json = readProductMatrix();
    var spec = new SyntheticBuildSpec.fromJson(json.first, release, json.last);
    log('writing pluginl.xml');
    var value = 1;
    var result = await genPluginXml(spec, 'resources');
    if (result != null) {
      log('writing .travis.yml');
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
  List<BuildSpec> specs;

  ProductCommand() {
    addProductFlags(argParser, name[0].toUpperCase() + name.substring(1));
  }

  bool get isForAndroidStudio => argResults['as'];

  bool get isForIntelliJ => argResults['ij'];

  bool get isReleaseMode => release != null;

  bool get isTestMode => globalResults['cwd'] != null;

  String get release {
    var rel = globalResults['release'];
    if (rel != null && rel.startsWith('=')) {
      rel = rel.substring(1);
    }
    return rel;
  }

  String archiveFilePath(BuildSpec spec) {
    var subDir = isReleaseMode ? 'release_$release' : '';
    var filePath = p.join(
        rootPath, 'artifacts', subDir, spec.version, 'flutter-intellij.zip');
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
  Map alternate;

  SyntheticBuildSpec.fromJson(Map json, String releaseNum, this.alternate)
      : super.fromJson(json, releaseNum);

  String get untilBuild => alternate['untilBuild'];
}

/// Build the tests if necessary then
/// run them and return any failure code.
class TestCommand extends ProductCommand {
  final BuildCommandRunner runner;

  TestCommand(this.runner) {
    argParser.addFlag('unit',
        abbr: 'u', defaultsTo: true, help: 'Run unit tests');
    argParser.addFlag('integration',
        abbr: 'i', defaultsTo: false, help: 'Run integration tests');
  }

  String get description => 'Run the tests for the Flutter plugin.';

  String get name => 'test';

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
        ..addAll(
            findJars('${spec.product.outPath}/lib')); //TODO: also, plugins

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
