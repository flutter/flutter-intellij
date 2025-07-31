// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io';

import 'package:plugin_tool/plugin.dart';
import 'package:plugin_tool/runner.dart';
import 'package:plugin_tool/verify.dart';
import 'package:string_validator/string_validator.dart' as validator;
import 'package:test/test.dart';

void main() {
  group("create", () {
    test('make', () {
      expect(GradleBuildCommand(BuildCommandRunner()).name, "make");
    });

    test('test', () {
      expect(TestCommand(BuildCommandRunner()).name, "test");
    });

    test('deploy', () {
      expect(DeployCommand(BuildCommandRunner()).name, "deploy");
    });

    test('generate', () {
      expect(GenerateCommand(BuildCommandRunner()).name, "generate");
    });

    test('verify', () {
      expect(VerifyCommand(BuildCommandRunner()).name, "verify");
    });
  });

  group("spec", () {
    /// This method has assertions which can be made for all commands in this
    /// test group.
    void buildSpecAssertions(BuildCommandRunner runner, String command) {
      var specs = (runner.commands[command] as ProductCommand).specs;
      expect(specs, isList);
      expect(specs, isNotEmpty);

      // channel should be set to stable in the product-matrix.json
      for (String channel in specs.map((spec) => spec.channel).toList()) {
        expect(channel, anyOf('stable'));
      }

      // name should be set to stable in the product-matrix.json
      for (String name in specs.map((spec) => spec.name).toList()) {
        expect(name, isNotEmpty);
        expect(name.length, 6);
        expect(name, validator.isFloat);
      }

      // ideaProduct should be android-studio or IC
      for (String ideaProduct
          in specs.map((spec) => spec.ideaProduct).toList()) {
        expect(ideaProduct, anyOf('android-studio', 'IC'));
      }

      // sinceBuild should be in the form of '243'
      for (String sinceBuild in specs.map((spec) => spec.sinceBuild).toList()) {
        expect(sinceBuild.length, 3);
        expect(sinceBuild, validator.isNumeric);
      }

      // untilBuild should be in the form of '243.*'
      for (String untilBuild in specs.map((spec) => spec.untilBuild).toList()) {
        expect(untilBuild.length, 5);
        expect(untilBuild.substring(0, 2), validator.isNumeric);
      }
    }

    test('build', () async {
      var runner = makeTestRunner();
      await runner.run(["-r=19", "-d../..", "make"]).whenComplete(() {
        buildSpecAssertions(runner, "make");
      });
    });

    test('test', () async {
      var runner = makeTestRunner();
      await runner.run(["-r=19", "-d../..", "test"]).whenComplete(() {
        buildSpecAssertions(runner, "test");
      });
    });

    test('deploy', () async {
      var runner = makeTestRunner();
      await runner.run(["-r19", "-d../..", "deploy"]).whenComplete(() {
        buildSpecAssertions(runner, "deploy");
      });
    });

    test('verify', () async {
      var runner = makeTestRunner();
      await runner.run(["-r19", "-d../..", "verify"]).whenComplete(() {
        buildSpecAssertions(runner, "verify");
      });
    });
  });

  group('release', () {
    test('simple', () async {
      var runner = makeTestRunner();
      late TestDeployCommand cmd;
      await runner.run(["-r19", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.isReleaseValid, true);
    });

    test('minor', () async {
      var runner = makeTestRunner();
      late TestDeployCommand cmd;
      await runner.run(["-r19.2", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.isReleaseValid, true);
    });

    test('patch invalid', () async {
      var runner = makeTestRunner();
      late TestDeployCommand cmd;
      await runner.run(["-r19.2.1", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.isReleaseValid, false);
    });

    test('non-numeric', () async {
      var runner = makeTestRunner();
      late TestDeployCommand cmd;
      await runner.run(["-rx19.2", "-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.isReleaseValid, false);
    });
  });

  group('deploy', () {
    test('clean', () async {
      var dir = Directory.current;
      var runner = makeTestRunner();
      await runner.run([
        "-r=19",
        "-d../..",
        "deploy",
        "--no-as",
        "--no-ij"
      ]).whenComplete(() {
        expect(Directory.current.path, equals(dir.path));
      });
    });

    test('without --release', () async {
      var runner = makeTestRunner();
      late TestDeployCommand cmd;
      await runner.run(["-d../..", "deploy"]).whenComplete(() {
        cmd = (runner.commands['deploy'] as TestDeployCommand);
      });
      expect(cmd.paths, orderedEquals([]));
    });
  });

  group('build', () {
    test('only-version', () async {
      ProductCommand command =
          makeTestRunner().commands['make'] as ProductCommand;
      var results = command.argParser.parse(['--only-version=2023.1']);
      expect(results['only-version'], '2023.1');
    });
  });

  group('verify', () {
    test('only-version', () async {
      ProductCommand command =
          makeTestRunner().commands['verify'] as ProductCommand;
      var results = command.argParser.parse(['--only-version=2023.1']);
      expect(results['only-version'], '2023.1');
    });
  });

  group('ProductCommand', () {
    test('parses release', () async {
      var runner = makeTestRunner();
      late ProductCommand command;
      await runner.run(["-d../..", '-r22.0', "make"]).whenComplete(() {
        command = (runner.commands['make'] as ProductCommand);
      });
      expect(command.release, '22.0');
    });
    test('parses release partial number', () async {
      var runner = makeTestRunner();
      late ProductCommand command;
      await runner.run(["-d../..", '-r22', "make"]).whenComplete(() {
        command = (runner.commands['make'] as ProductCommand);
      });
      expect(command.release, '22.0');
    });

    test('isReleaseValid', () async {
      var runner = makeTestRunner();
      late ProductCommand command;
      await runner.run(["-d../..", '-r22.0', "make"]).whenComplete(() {
        command = (runner.commands['make'] as ProductCommand);
      });
      expect(command.isReleaseValid, true);
    });
    test('isReleaseValid partial version', () async {
      var runner = makeTestRunner();
      late ProductCommand command;
      await runner.run(["-d../..", '-r22', "make"]).whenComplete(() {
        command = (runner.commands['make'] as ProductCommand);
      });
      expect(command.isReleaseValid, true);
    });
    test('isReleaseValid bad version', () async {
      var runner = makeTestRunner();
      late ProductCommand command;
      await runner.run(["-d../..", '-r22.0.0', "make"]).whenComplete(() {
        command = (runner.commands['make'] as ProductCommand);
      });
      expect(command.isReleaseValid, false);
    });
  });
}

BuildCommandRunner makeTestRunner() {
  var runner = BuildCommandRunner();
  runner.addCommand(TestMakeCommand(runner));
  runner.addCommand(TestTestCommand(runner));
  runner.addCommand(TestDeployCommand(runner));
  runner.addCommand(TestGenCommand(runner));
  runner.addCommand(TestVerifyCommand(runner));
  return runner;
}

class TestDeployCommand extends DeployCommand {
  List<String> paths = <String>[];
  List<String> plugins = <String>[];

  TestDeployCommand(super.runner);

  @override
  bool get isTesting => true;

  @override
  void changeDirectory(Directory dir) {}

  String readTokenFile() {
    return "token";
  }

  @override
  Future<int> upload(
    String filePath,
    String pluginNumber,
    String token,
    String channel,
  ) {
    paths.add(filePath);
    plugins.add(pluginNumber);
    return Future(() => 0);
  }
}

class TestGenCommand extends GenerateCommand {
  TestGenCommand(super.runner);

  @override
  bool get isTesting => true;

  @override
  Future<int> doit() async => Future(() => 0);
}

class TestMakeCommand extends GradleBuildCommand {
  TestMakeCommand(super.runner);

  @override
  bool get isTesting => true;

  @override
  Future<int> doit() async => Future(() => 0);
}

class TestTestCommand extends TestCommand {
  TestTestCommand(super.runner);

  @override
  bool get isTesting => true;

  @override
  Future<int> doit() async => Future(() => 0);
}

class TestVerifyCommand extends VerifyCommand {
  TestVerifyCommand(super.runner);

  @override
  bool get isTesting => true;

  @override
  Future<int> doit() async => Future(() => 0);
}
