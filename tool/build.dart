// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// TODO: include forms_rt.jar?
// https://stackoverflow.com/questions/4547515/ant-build-for-intellij-idea-gui-forms

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:args/args.dart';
import 'package:args/command_runner.dart';
import 'package:path/path.dart' as p;

main(List<String> args) async {
  BuildCommandRunner runner = new BuildCommandRunner();

  runner.argParser.addOption(
    'idea-product',
    help: 'IDEA product',
    defaultsTo: 'ideaIC',
    allowed: ['ideaIC', 'WebStorm', 'android-studio-ide'],
  );

  runner.argParser.addOption(
    'idea-version',
    help: 'IDEA product verion',
    defaultsTo: '2017.1.3',
  );

  runner.argParser.addOption(
    'dart-version',
    help: 'Dart plugin version',
    defaultsTo: '171.4694.29',
  );

  runner.addCommand(new BuildCommand(runner));
  runner.addCommand(new TestCommand(runner));

  try {
    exit(await runner.run(args) ?? 0);
  } on UsageException catch (e) {
    print('$e');
    exit(1);
  }
}

class BuildCommandRunner extends CommandRunner {
  final ArtifactManager artifacts = new ArtifactManager();

  Artifact product;
  Artifact dartPlugin;

  BuildCommandRunner()
      : super(
            'build', 'A script to build and test the Flutter IntelliJ plugin.');

  Future runCommand(ArgResults results) async {
    // vette and collect artifacts
    final String ideaProduct = results['idea-product'];
    final String ideaVersion = results['idea-version'];
    final String dartVersion = results['dart-version'];

    if (ideaProduct == 'android-studio-ide') {
      product = artifacts.add(new Artifact(
          '$ideaProduct-$ideaVersion-linux.zip',
          output: ideaProduct));
    } else {
      product = artifacts.add(new Artifact('$ideaProduct-$ideaVersion.tar.gz',
          output: ideaProduct));
    }

    if (ideaVersion != 'WebStorm') {
      dartPlugin = artifacts.add(new Artifact('Dart-$dartVersion.zip'));
    }

    return super.runCommand(results);
  }

  Future javac(
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

    return exec('javac', args);
  }
}

class BuildCommand<int> extends Command {
  final BuildCommandRunner runner;

  BuildCommand(this.runner);

  String get name => 'build';

  String get description => 'Build a deployable version of the Flutter plugin, '
      'compiled against the specified artifacts.';

  run() async {
    await runner.artifacts.provision();

    separator('Building flutter-intellij.jar');

    List<File> jars = []
      ..addAll(findJars('${runner.dartPlugin.outPath}/lib'))
      ..addAll(
          findJars('${runner.product.outPath}/lib')); // TODO: also, plugins

    List<String> sourcepath = [
      'src',
      'resources',
      'gen',
      'third_party/intellij-plugins-dart/src'
    ];
    createDir('build/classes');

    // TODO: use the javac from idea
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

    return 0;
  }
}

// TODO(devoncarew): build the tests if necessary
// TODO(devoncarew): run them and return any failure code

class TestCommand<int> extends Command {
  final BuildCommandRunner runner;

  TestCommand(this.runner);

  String get name => 'test';

  String get description => 'Run the tests for the Flutter plugin.';

  run() async {
    await runner.artifacts.provision();

    // TODO(devoncarew): implement
    return 0;
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

Future exec(String cmd, List<String> args, {String cwd}) async {
  if (cwd != null) {
    log(_shorten('$cmd ${args.join(' ')} {cwd=$cwd}'));
  } else {
    log(_shorten('$cmd ${args.join(' ')}'));
  }

  final Process process = await Process.start(cmd, args, workingDirectory: cwd);
  _toLineStream(process.stderr, SYSTEM_ENCODING).listen(log);
  _toLineStream(process.stdout, SYSTEM_ENCODING).listen(log);

  final int code = await process.exitCode;
  if (code != 0) {
    throw 'error, exit code $code';
  }
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

Future jar(String directory, String outFile) async {
  List<String> args = ['cf', p.absolute(outFile)];
  args.addAll(new Directory(directory)
      .listSync(followLinks: false)
      .map((f) => p.basename(f.path)));
  await exec('jar', args, cwd: directory);
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

