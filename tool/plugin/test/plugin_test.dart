// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'package:plugin/plugin.dart';
import 'package:test/test.dart';

void main() {
  group("create", () {
    test('build', () {
      expect(new AntBuildCommand(new BuildCommandRunner()).name, "build");
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
    test('build', () {
      var runner = new BuildCommandRunner();
      var cmd = new TestAntBuildCommand(runner);
      runner.addCommand(cmd);
      runner.run(["-r=19", "build"])
          .whenComplete(() {
        var specs = cmd.specs;
        expect(specs.length, 2);
        expect(specs[0].ideaProduct, 'android-studio-ide');
        expect(specs[1].ideaProduct, 'ideaIC');
      });
    });
  });
}

class TestAntBuildCommand extends AntBuildCommand {
  TestAntBuildCommand(runner) : super(runner);

  doit() {}
}
