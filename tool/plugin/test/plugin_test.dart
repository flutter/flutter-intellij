// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io';

import 'package:plugin_tool/plugin.dart';
import 'package:plugin_tool/runner.dart';
import 'package:test/test.dart';

void main() {
  group("create", () {
    test('deploy', () {
      expect(DeployCommand(BuildCommandRunner()).name, "deploy");
    });

    test('generate', () {
      expect(GenerateCommand(BuildCommandRunner()).name, "generate");
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
}

BuildCommandRunner makeTestRunner() {
  var runner = BuildCommandRunner();
    runner.addCommand(TestDeployCommand(runner));
  runner.addCommand(TestGenCommand(runner));
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
