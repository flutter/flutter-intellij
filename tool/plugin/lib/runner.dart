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

  // Use this to compile plugin sources using ant to get forms processed.
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
      } else {
        throw x;
      }
    }
  }

  Future<int> buildPlugin(BuildSpec spec, String version) async {
    final contents = '''
org.gradle.parallel=true
org.gradle.jvmargs=-Xms128m -Xmx1024m -XX:+CMSClassUnloadingEnabled
dartVersion=${spec.dartPluginVersion}
flutterPluginVersion=$version
ide=${spec.ideaProduct}
''';
    final propertiesFile = File("$rootPath/gradle.properties");
    final source = propertiesFile.readAsStringSync();
    propertiesFile.writeAsStringSync(contents);
    var result;
    // Using the Gradle daemon causes a strange problem.
    // --daemon => Invalid byte 1 of 1-byte UTF-8 sequence, which is nonsense.
    // During instrumentation of FlutterProjectStep.form, which is a UTF-8 file.
    try {
      if (Platform.isWindows) {
        result = await exec('.\\gradlew.bat', ['buildPlugin']);
      } else {
        result = await exec('./gradlew', ['buildPlugin']);
      }
    } finally {
      propertiesFile.writeAsStringSync(source);
    }
    return result;
  }
}
