// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:args/args.dart';
import 'package:args/command_runner.dart';
import 'package:path/path.dart' as p;

import 'src/lint.dart';

const plugins = const {
  'io.flutter': '9212',
  'io.flutter.as': '10139',
};

const files = const {
  'io.flutter': 'flutter-intellij.jar',
  'io.flutter.as': 'flutter-studio.zip',
};

main(List<String> args) async {
  BuildCommandRunner runner = new BuildCommandRunner();

  runner.addCommand(new LintCommand(runner));
  runner.addCommand(new AntBuildCommand(runner));
//  runner.addCommand(new BuildCommand(runner));
  runner.addCommand(new TestCommand(runner));
  runner.addCommand(new DeployCommand(runner));
  runner.addCommand(new GenCommand(runner));

  try {
    exit(await runner.run(args) ?? 0);
  } on UsageException catch (e) {
    print('$e');
    exit(1);
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
  }

  Future<int> javac(
      {List sourcepath,
      String destdir,
      List classpath,
      List<String> sources}) async {
    //final Directory javacDir = new Directory('artifacts/${artifacts.javac.output}');

    final List<String> args = [
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

    // TODO(messick) Change to javac2, add classpath entries.
    return exec('javac', args);
  }
}

abstract class ProductCommand extends Command {
  List<BuildSpec> specs;

  ProductCommand() {
    addProductFlags(argParser, name[0].toUpperCase() + name.substring(1));
  }

  bool get isForAndroidStudio => argResults['as'];

  bool get isForIntelliJ => argResults['ij'];

  String get release {
    var rel = globalResults['release'];
    if (rel != null && rel.startsWith('=')) {
      rel = rel.substring(1);
    }
    return rel;
  }

  bool get isReleaseMode => release != null;

  Future<int> run() async {
    specs = createBuildSpecs(this);
    return await doit();
  }

  Future<int> doit();

  String archiveFilePath(BuildSpec spec) {
    String subDir = isReleaseMode ? '/release_$release' : '';
    String filePath = 'artifacts$subDir/${files[spec.pluginId]}';
    return filePath;
  }
}

/// Temporary command to use the Ant build script.
class AntBuildCommand extends ProductCommand {
  final BuildCommandRunner runner;

  AntBuildCommand(this.runner);

  String get name => 'abuild';

  String get description => 'Build a deployable version of the Flutter plugin, '
      'compiled against the specified artifacts.';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (!performReleaseChecks()) {
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

/// Build deployable plugin files. If the --release argument is given
/// then perform additional checks to verify that the release environment
/// is in good order.
class BuildCommand extends ProductCommand {
  final BuildCommandRunner runner;

  BuildCommand(this.runner);

  String get name => 'build';

  String get description => 'Build a deployable version of the Flutter plugin, '
      'compiled against the specified artifacts.';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (!performReleaseChecks()) {
        return new Future(() => 1);
      }
    }
    for (var spec in specs) {
      await spec.artifacts.provision();

      separator('Building flutter-intellij.jar');

      List<File> jars = []
        ..addAll(findJars('${spec.dartPlugin.outPath}/lib'))
        ..addAll(
            findJars('${spec.product.outPath}/lib')); // TODO: also, plugins

      List<String> sourcepath = [
        'src',
        'resources',
        'gen',
        'third_party/intellij-plugins-dart/src'
      ];
      createDir('build/classes');

      await runner.javac(
        sources: sourcepath.expand(findJavaFiles).toList(),
        sourcepath: sourcepath,
        destdir: 'build/classes',
        classpath: jars.map((j) => j.path).toList(),
      );

      // copy resources
      copyResources(from: 'src', to: 'build/classes');
      copyResources(from: 'resources', to: 'build/classes');
      copyResources(from: 'gen', to: 'build/classes');
      copyResources(
          from: 'third_party/intellij-plugins-dart/src', to: 'build/classes');

      // create the jar
      await jar('build/classes', 'build/flutter-intellij.jar');
    }
    return 0;
  }
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

  String get name => 'test';

  String get description => 'Run the tests for the Flutter plugin.';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (!performReleaseChecks()) {
        return new Future(() => 1);
      }
    }

    for (var spec in specs) {
      await spec.artifacts.provision();

      // TODO(devoncarew): implement
    }
    throw 'unimplemented';
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

  String get name => 'deploy';

  String get description => 'Upload the Flutter plugin to the JetBrains site.';

  Future<int> doit() async {
    if (isReleaseMode) {
      if (!performReleaseChecks()) {
        return new Future(() => 1);
      }
    } else {
      log('Deploy must have a --release argument');
      return new Future(() => 1);
    }
    String password;
    try {
      // Detect test mode early to keep stdio clean for the test results parser.
      bool mode = stdin.echoMode;
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

    Directory directory = Directory.systemTemp.createTempSync('plugin');
    tempDir = directory.path;
    var file = new File('$tempDir/.content');
    file.writeAsStringSync(password, flush: true);

    var value = 0;
    for (var spec in specs) {
      String filePath = archiveFilePath(spec);
      value = await upload(filePath, plugins[spec.pluginId]);
      if (value != 0) return value;
    }

    file.deleteSync();
    directory.deleteSync();
    return value;
  }

  Future<int> upload(String filePath, String pluginNumber) async {
    if (!new File(filePath).existsSync()) {
      throw 'File not found: $filePath';
    }
    log("uploading $filePath");
    String args = '''
-i 
-F userName="${username}" 
-F password="<${tempDir}/.content" 
-F pluginId="$pluginNumber" 
-F file="@$filePath" 
"https://plugins.jetbrains.com/plugin/uploadPlugin"
''';

    final Process process = await Process.start('curl', args.split(r'\w'));
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
class GenCommand extends Command {
  final BuildCommandRunner runner;

  GenCommand(this.runner);

  String get name => 'gen';

  String get description =>
      'Generate a valid plugin.xml for the Flutter plugin from the template.';

  Future<int> run() async {
    // TODO(messick): implement
    throw 'unimplemented';
  }
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

  Future provision() async {
    separator('Getting artifacts');
    createDir('artifacts');

    for (Artifact artifact in artifacts) {
      final String path = 'artifacts/${artifact.file}';
      if (FileSystemEntity.isFileSync(path)) {
        log('$path exists in cache');
        continue;
      }

      log('downloading $path...');
      await curl('$base/${artifact.file}', to: path);

      // expand
      createDir(artifact.outPath);

      if (artifact.isZip) {
        if (artifact.bareArchive) {
          await exec('unzip', ['-q', '-d', artifact.output, artifact.file],
              cwd: 'artifacts');
        } else {
          await exec('unzip', ['-q', artifact.file], cwd: 'artifacts');
        }
      } else {
        await exec(
          'tar',
          [
            '--strip-components=1',
            '-zxf',
            artifact.file,
            '-C',
            artifact.output
          ],
          cwd: 'artifacts',
        );
      }

      log('');
    }
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

  String get outPath => 'artifacts/$output';
}

class BuildSpec {
  // Build targets
  String ideaProduct = 'ideaIC';
  String ideaVersion = '2017.2';
  String dartVersion = '172.3317.48';

  // plugin.xml variables
  String since = '172.1';
  String until = '173.*';
  String pluginId = 'io.flutter';
  String depends = '';
  String version;

  ArtifactManager artifacts = new ArtifactManager();

  Artifact product;
  Artifact dartPlugin;

  static BuildSpec forAndroidStudio(ProductCommand command) {
    BuildSpec spec = new BuildSpec();

    // TODO(messick) Extract these values from .travis.yml
    spec.ideaProduct = 'android-studio-ide';
    spec.ideaVersion = '171.4408382';
    spec.dartVersion = '171.4424.10';

    // These values are determined from the above.
    spec.since = '${spec.ideaVersion.substring(0, 3)}.1';
    spec.until = '${spec.ideaVersion.substring(0, 3)}.*';
    spec.pluginId = '${spec.pluginId}.as';

    // This is constant for Android Studio.
    spec.depends = '<depends>com.android.tools.apk</depends>';

    // Version from arguments -- may be null if not release mode.
    spec.version = command.release;

    spec.createArtifacts();
    return spec;
  }

  static BuildSpec forIntelliJ(ProductCommand command) {
    BuildSpec spec = new BuildSpec();

    // TODO(messick) Current defaults should be replaced by values from .travis.yml.
    spec.version = command.release;

    spec.createArtifacts();
    return spec;
  }

  bool get isReleaseMode => version != null;

  createArtifacts() {
    if (ideaProduct == 'android-studio-ide') {
      product = artifacts.add(new Artifact(
          '$ideaProduct-$ideaVersion-linux.zip',
          output: ideaProduct));
    } else {
      product = artifacts.add(new Artifact('$ideaProduct-$ideaVersion.tar.gz',
          output: ideaProduct));
    }
    dartPlugin = artifacts.add(new Artifact('Dart-$dartVersion.zip'));
  }

  String toString() {
    return 'BuildSpec($ideaProduct $ideaVersion $dartVersion $since $until '
        '$pluginId depends: "$depends" version: "$version")';
  }
}

bool performReleaseChecks() {
  // TODO(messick) Implement release checks.
  // git should have a release_NN branch where NN is the value of --release
  // git should have no uncommitted changes
  return true;
}

void addProductFlags(ArgParser argParser, String verb) {
  argParser.addFlag('ij', help: '$verb the IntelliJ plugin', defaultsTo: true);
  argParser.addFlag('as',
      help: '$verb the Android Studio plugin', defaultsTo: true);
}

List<BuildSpec> createBuildSpecs(ProductCommand command) {
  var specs = new List<BuildSpec>();
  if (command.isForAndroidStudio) {
    specs.add(BuildSpec.forAndroidStudio(command));
  }
  if (command.isForIntelliJ) {
    specs.add(BuildSpec.forIntelliJ(command));
  }
  return specs;
}

Future<int> exec(String cmd, List<String> args, {String cwd}) async {
  if (cwd != null) {
    log(_shorten('$cmd ${args.join(' ')} {cwd=$cwd}'));
  } else {
    log(_shorten('$cmd ${args.join(' ')}'));
  }

  final Process process = await Process.start(cmd, args, workingDirectory: cwd);
  _toLineStream(process.stderr, SYSTEM_ENCODING).listen(log);
  _toLineStream(process.stdout, SYSTEM_ENCODING).listen(log);

  return await process.exitCode;
}

Future<int> ant(BuildSpec spec) async {
  var args = new List<String>();
  String directory = null;
  args.add('-Ddart.plugin.version=${spec.dartVersion}');
  args.add('-Didea.version=${spec.ideaVersion}');
  args.add('-Didea.product=${spec.ideaProduct}');
  args.add('-DDEPENDS=${spec.depends}');
  args.add('-DPLUGINID=${spec.pluginId}');
  args.add('-DSINCE=${spec.since}');
  args.add('-DUNTIL=${spec.until}');
  // TODO(messick) Add version to plugin.xml.template.
  return await exec('ant', args, cwd: directory);
}

Future<int> deleteBuildContents() async {
  final Directory dir = new Directory('build');
  if (!dir.existsSync()) throw 'No build directory found';
  var args = new List<String>();
  args.add('-rf');
  args.add('build/*');
  return await exec('rm', args);
}

Future<int> moveToArtifacts(ProductCommand cmd, BuildSpec spec) async {
  final Directory dir = new Directory('artifacts');
  if (!dir.existsSync()) throw 'No artifacts directory found';
  String file = plugins[spec.pluginId];
  var args = new List<String>();
  args.add('build/$file');
  args.add(cmd.archiveFilePath(spec));
  return await exec('mv', args);
}

Future<int> jar(String directory, String outFile) async {
  List<String> args = ['cf', p.absolute(outFile)];
  args.addAll(new Directory(directory)
      .listSync(followLinks: false)
      .map((f) => p.basename(f.path)));
  return await exec('jar', args, cwd: directory);
}

Future curl(String url, {String to}) async {
  await exec('curl', ['-o', to, url]);
}

void separator(String name) {
  log('');
  log('$name:', indent: false);
}

void log(String s, {bool indent: true}) {
  indent ? print('  $s') : print(s);
}

void createDir(String name) {
  final Directory dir = new Directory(name);
  if (!dir.existsSync()) {
    log('creating $name/');
    dir.createSync(recursive: true);
  }
}

List<File> findJars(String path) {
  final Directory dir = new Directory(path);
  return dir
      .listSync(recursive: true, followLinks: false)
      .where((e) => e.path.endsWith('.jar'))
      .toList();
}

List<String> findJavaFiles(String path) {
  final Directory dir = new Directory(path);
  return dir
      .listSync(recursive: true, followLinks: false)
      .where((e) => e.path.endsWith('.java'))
      .map((f) => f.path)
      .toList();
}

void copyResources({String from, String to}) {
  _copyResources(new Directory(from), new Directory(to));
}

void _copyResources(Directory from, Directory to) {
  for (FileSystemEntity entity in from.listSync(followLinks: false)) {
    final String basename = p.basename(entity.path);
    if (basename.endsWith('.java') || basename.endsWith('.kt')) {
      continue;
    }

    if (entity is File) {
      _copyFile(entity, to);
    } else {
      _copyResources(entity, new Directory(p.join(to.path, basename)));
    }
  }
}

void _copyFile(File file, Directory to) {
  if (!to.existsSync()) {
    to.createSync(recursive: true);
  }
  final File target = new File(p.join(to.path, p.basename(file.path)));
  target.writeAsBytesSync(file.readAsBytesSync());
}

Stream<String> _toLineStream(Stream<List<int>> s, Encoding encoding) =>
    s.transform(encoding.decoder).transform(const LineSplitter());

String _shorten(String s) {
  if (s.length < 200) {
    return s;
  }
  return s.substring(0, 170) + ' ... ' + s.substring(s.length - 30);
}
