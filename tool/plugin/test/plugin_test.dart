// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io';

import 'package:plugin/plugin.dart';
import 'package:test/test.dart';

void main() {
  group("create", () {
    test('build', () {
      expect(new BuildCommand(new BuildCommandRunner()).name, "build");
    });
    test('test', () {
      expect(new TestCommand(new BuildCommandRunner()).name, "test");
    });
    test('deploy', () {
      expect(new DeployCommand(new BuildCommandRunner()).name, "deploy");
    });
    test('generate', () {
      expect(new GenerateCommand(new BuildCommandRunner()).name, "generate");
    });
  });

  group("spec", () {
    test('build', () async {
      var runner = makeTestRunner();
      await runner.run(["-r=19", "-d../..", "build"]).whenComplete(() {
        var specs = (runner.commands['build'] as ProductCommand).specs;
        expect(specs, isNotNull);
        expect(specs.map((spec) => spec.ideaProduct),
            orderedEquals(['android-studio', 'android-studio', 'ideaIC']));
      });
    });
    test('test', () async {
      var runner = makeTestRunner();
      await runner.run(["-r=19", "-d../..", "test"]).whenComplete(() {
        var specs = (runner.commands['test'] as ProductCommand).specs;
        expect(specs, isNotNull);
        expect(specs.map((spec) => spec.ideaProduct),
            orderedEquals(['android-studio', 'android-studio', 'ideaIC']));
      });
    });
    test('deploy', () async {
      var runner = makeTestRunner();
      await runner.run(["-r19", "-d../..", "deploy"]).whenComplete(() {
        var specs = (runner.commands['deploy'] as ProductCommand).specs;
        expect(specs, isNotNull);
        expect(specs.map((spec) => spec.ideaProduct),
            orderedEquals(['android-studio', 'android-studio', 'ideaIC']));
      });
    });
  });

  group('release', () {
    test('simple', () async {
      var runner = makeTestRunner();
      TestDeployCommand cmd;
      await runner.run(["-r19", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.isReleaseValid, true);
    });
    test('minor', () async {
      var runner = makeTestRunner();
      TestDeployCommand cmd;
      await runner.run(["-r19.2", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.isReleaseValid, true);
    });
    test('patch invalid', () async {
      var runner = makeTestRunner();
      TestDeployCommand cmd;
      await runner.run(["-r19.2.1", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.isReleaseValid, false);
    });
    test('non-numeric', () async {
      var runner = makeTestRunner();
      TestDeployCommand cmd;
      await runner.run(["-rx19.2", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.isReleaseValid, false);
    });
  });

  group('deploy', () {
    test('clean', () async {
      var runner = makeTestRunner();
      await runner.run([
        "-r=19",
        "-d../..",
        "deploy",
        "--no-as",
        "--no-ij"
      ]).whenComplete(() {
        var dir = (runner.commands['deploy'] as DeployCommand).tempDir;
        expect(new Directory(dir).existsSync(), false);
      });
    });
    test('without --release', () async {
      var runner = makeTestRunner();
      TestDeployCommand cmd;
      await runner.run(["-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.paths, orderedEquals([]));
    });
    test('release paths', () async {
      var runner = makeTestRunner();
      TestDeployCommand cmd;
      await runner.run(["--release=19", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(
          cmd.paths.map((p) => p.substring(p.indexOf('releases'))),
          orderedEquals([
            'releases/release_19/3.1/flutter-intellij.zip',
            'releases/release_19/3.2/flutter-intellij.zip',
            'releases/release_19/2018.1/flutter-intellij.zip',
          ]));
    });
  });

  group('build', () {
    test('plugin.xml', () async {
      var runner = makeTestRunner();
      TestBuildCommand cmd;
      await runner.run(["-d../..", "build"]).whenComplete(() {
        cmd = (runner.commands['build'] as TestBuildCommand);
      });
      var spec = cmd.specs[0];
      await removeAll('../../build/classes');
      await genPluginFiles(spec, 'build/classes');
      var file = new File("../../build/classes/META-INF/plugin.xml");
      expect(file.existsSync(), isTrue);
      var content = file.readAsStringSync();
      expect(content.length, greaterThan(10000));
      var loc = content.indexOf('@');
      expect(loc, -1);
    });
    // Skipped as downloading the artifacts can take longer than the 30 second
    // test timeout.
    test('provision', () async {
      var runner = makeTestRunner();
      TestBuildCommand cmd;
      await runner.run(["-d../..", "build"]).whenComplete(() {
        cmd = (runner.commands['build'] as TestBuildCommand);
      });
      var spec = cmd.specs[0];
      expect(spec.artifacts.artifacts.length, greaterThan(1));
      var result = await spec.artifacts.provision(rebuildCache: false);
      expect(result, 0);
    }, skip: true);
    test('only-version', () async {
      ProductCommand command = makeTestRunner().commands['build'];
      var results = command.argParser.parse(['--only-version=2018.1']);
      expect(results['only-version'], '2018.1');
    });
  });

  group('ProductCommand', () {
    test('parses release', () async {
      var runner = makeTestRunner();
      ProductCommand command;
      await runner.run(["-d../..", '-r22.0', "build"]).whenComplete(() {
        command = (runner.commands['build'] as ProductCommand);
      });
      expect(command.release, '22.0');
    });
    test('parses release partial number', () async {
      var runner = makeTestRunner();
      ProductCommand command;
      await runner.run(["-d../..", '-r22', "build"]).whenComplete(() {
        command = (runner.commands['build'] as ProductCommand);
      });
      expect(command.release, '22.0');
    });

    test('isReleaseValid', () async {
      var runner = makeTestRunner();
      ProductCommand command;
      await runner.run(["-d../..", '-r22.0', "build"]).whenComplete(() {
        command = (runner.commands['build'] as ProductCommand);
      });
      expect(command.isReleaseValid, true);
    });
    test('isReleaseValid partial version', () async {
      var runner = makeTestRunner();
      ProductCommand command;
      await runner.run(["-d../..", '-r22', "build"]).whenComplete(() {
        command = (runner.commands['build'] as ProductCommand);
      });
      expect(command.isReleaseValid, true);
    });
    test('isReleaseValid bad version', () async {
      var runner = makeTestRunner();
      ProductCommand command;
      await runner.run(["-d../..", '-r22.0.0', "build"]).whenComplete(() {
        command = (runner.commands['build'] as ProductCommand);
      });
      expect(command.isReleaseValid, false);
    });
  });
}

BuildCommandRunner makeTestRunner() {
  var runner = new BuildCommandRunner();
  runner.addCommand(new TestBuildCommand(runner));
  runner.addCommand(new TestTestCommand(runner));
  runner.addCommand(new TestDeployCommand(runner));
  runner.addCommand(new TestGenCommand(runner));
  return runner;
}

class TestBuildCommand extends BuildCommand {
  TestBuildCommand(runner) : super(runner);

  bool get isTesting => true;

  Future<int> doit() async => new Future(() => 0);
}

class TestDeployCommand extends DeployCommand {
  List<String> paths = new List<String>();
  List<String> plugins = new List<String>();

  TestDeployCommand(runner) : super(runner);

  bool get isTesting => true;

  Future<int> upload(String filePath, String pluginNumber) {
    paths.add(filePath);
    plugins.add(pluginNumber);
    return new Future(() => 0);
  }
}

class TestGenCommand extends GenerateCommand {
  TestGenCommand(runner) : super(runner);

  bool get isTesting => true;

  Future<int> doit() async => new Future(() => 0);
}

class TestTestCommand extends TestCommand {
  TestTestCommand(runner) : super(runner);

  bool get isTesting => true;

  Future<int> doit() async => new Future(() => 0);
}
