// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:io';

import 'package:plugin/plugin.dart';
import 'package:test/test.dart';
import 'dart:async';

void main() {
  group("create", () {
    test('abuild', () {
      expect(new AntBuildCommand(new BuildCommandRunner()).name, "abuild");
    });
    test('build', () {
      expect(new BuildCommand(new BuildCommandRunner()).name, "build");
    });
    test('test', () {
      expect(new TestCommand(new BuildCommandRunner()).name, "test");
    });
    test('deploy', () {
      expect(new DeployCommand(new BuildCommandRunner()).name, "deploy");
    });
    test('gen', () {
      expect(new GenCommand(new BuildCommandRunner()).name, "gen");
    });
  });

  group("spec", () {
    test('abuild', () {
      var runner = makeTestRunner();
      runner.run(["-r=19", "-d../..", "abuild"]).whenComplete(() {
        var specs = (runner.commands['abuild'] as ProductCommand).specs;
        expect(specs, isNotNull);
        expect(specs.map((spec) => spec.ideaProduct),
            orderedEquals(['android-studio-ide', 'ideaIC']));
      });
    });
    test('build', () {
      var runner = makeTestRunner();
      runner.run(["-r=19", "-d../..", "build"]).whenComplete(() {
        var specs = (runner.commands['build'] as ProductCommand).specs;
        expect(specs, isNotNull);
        expect(specs.map((spec) => spec.ideaProduct),
            orderedEquals(['android-studio-ide', 'ideaIC']));
      });
    });
    test('test', () {
      var runner = makeTestRunner();
      runner.run(["-r=19", "-d../..", "test"]).whenComplete(() {
        var specs = (runner.commands['test'] as ProductCommand).specs;
        expect(specs, isNotNull);
        expect(specs.map((spec) => spec.ideaProduct),
            orderedEquals(['android-studio-ide', 'ideaIC']));
      });
    });
    test('deploy', () {
      var runner = makeTestRunner();
      runner.run(["-r19", "-d../..", "deploy"]).whenComplete(() {
        var specs = (runner.commands['deploy'] as ProductCommand).specs;
        expect(specs, isNotNull);
        expect(specs.map((spec) => spec.ideaProduct),
            orderedEquals(['android-studio-ide', 'ideaIC']));
      });
    });
  });

  group('deploy', () {
    test('clean', () {
      var runner = makeTestRunner();
      runner.run(["-r=19", "-d../..", "deploy", "--no-as", "--no-ij"]).whenComplete(() {
        String dir = (runner.commands['deploy'] as DeployCommand).tempDir;
        expectAsync0(() => new Directory(dir).existsSync() == false);
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
          cmd.paths.map((p) => p.substring(p.indexOf('artifacts'))),
          orderedEquals([
            'artifacts/release_19/flutter-studio.zip',
            'artifacts/release_19/flutter-intellij.jar',
          ]));
    });
  });
}

BuildCommandRunner makeTestRunner() {
  var runner = new BuildCommandRunner();
  runner.addCommand(new TestAntBuildCommand(runner));
  runner.addCommand(new TestBuildCommand(runner));
  runner.addCommand(new TestTestCommand(runner));
  runner.addCommand(new TestDeployCommand(runner));
  runner.addCommand(new TestGenCommand(runner));
  return runner;
}

class TestAntBuildCommand extends AntBuildCommand {
  TestAntBuildCommand(runner) : super(runner);

  doit() {}
}

class TestBuildCommand extends BuildCommand {
  TestBuildCommand(runner) : super(runner);

  doit() {}
}

class TestTestCommand extends TestCommand {
  TestTestCommand(runner) : super(runner);

  doit() {}
}

class TestDeployCommand extends DeployCommand {
  var paths = new List<String>();
  var plugins = new List<String>();

  TestDeployCommand(runner) : super(runner);

  Future<int> upload(String filePath, String pluginNumber) {
    paths.add(filePath);
    plugins.add(pluginNumber);
    return new Future(() => 0);
  }
}

class TestGenCommand extends GenCommand {
  TestGenCommand(runner) : super(runner);

  doit() {}
}
