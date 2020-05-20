// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io';

import 'package:args/command_runner.dart';

import 'build_spec.dart';
import 'globals.dart';
import 'util.dart';

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

  // Use this to compile plugin sources to get forms processed.
  Future<int> javac2(BuildSpec spec) async {
    var args = '''
-f tool/plugin/compile.xml
-Didea.product=${spec.ideaProduct}
-Didea.version=${spec.ideaVersion}
-Dbasedir=$rootPath
compile
''';
    try {
      return await exec('ant', args.split(RegExp(r'\s')));
    } on ProcessException catch (x) {
      if (x.message == 'No such file or directory') {
        log(
            '\nThe build command requires ant to be installed. '
            '\nPlease ensure ant is on your \$PATH.',
            indent: false);
        exit(x.errorCode);
        // The call to `exit` above does not return, but we return a value from
        // the function here to make the analyzer happy.
        return 0;
      } else {
        throw x;
      }
    }
  }
}
