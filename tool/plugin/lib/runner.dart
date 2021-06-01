// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// @dart = 2.10

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
    writeJxBrowserKeyToFile();
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
        rethrow;
      }
    }
  }

  void writeJxBrowserKeyToFile() {
    final jxBrowserKey =
        readTokenFromKeystore('FLUTTER_KEYSTORE_JXBROWSER_KEY_NAME');
    final propertiesFile =
        File("$rootPath/resources/jxbrowser/jxbrowser.properties");
    if (jxBrowserKey.isNotEmpty) {
      final contents = '''
jxbrowser.license.key=$jxBrowserKey
''';
      propertiesFile.writeAsStringSync(contents);
    }
  }

  Future<int> buildPlugin(BuildSpec spec, String version) async {
    writeJxBrowserKeyToFile();
    return await runGradleCommand(['buildPlugin'], spec, version, 'false');
  }

  Future<int> runGradleCommand(
    List<String> command,
    BuildSpec spec,
    String version,
    String testing,
  ) async {
    var javaVersion = ['4.1'].contains(spec.version) ? '1.8' : '11';
    final contents = '''
name = "flutter-intellij"
org.gradle.parallel=true
org.gradle.jvmargs=-Xms128m -Xmx1024m -XX:+CMSClassUnloadingEnabled
javaVersion=$javaVersion
dartVersion=${spec.dartPluginVersion}
flutterPluginVersion=$version
ide=${spec.ideaProduct}
testing=$testing
buildSpec=${spec.version}
''';
    final propertiesFile = File("$rootPath/gradle.properties");
    final source = propertiesFile.readAsStringSync();
    propertiesFile.writeAsStringSync(contents);
    int result;
    // Using the Gradle daemon causes a strange problem.
    // --daemon => Invalid byte 1 of 1-byte UTF-8 sequence, which is nonsense.
    // During instrumentation of FlutterProjectStep.form, which is a UTF-8 file.
    try {
      if (Platform.isWindows) {
        if (spec.version == '4.1') {
          log('CANNOT BUILD ${spec.version} ON WINDOWS');
          return 0;
        }
        result = await exec('.\\gradlew.bat', command);
      } else {
        if (spec.version == '4.1') {
          return await runShellScript(command, spec);
        } else {
          result = await exec('./gradlew', command);
        }
      }
    } finally {
      propertiesFile.writeAsStringSync(source);
    }
    return result;
  }

  Future<int> runShellScript(List<String> command, BuildSpec spec) async {
    var script = '''
#!/bin/bash
export JAVA_HOME=\$JAVA_HOME_OLD
./gradlew ${command.join(' ')}
''';
    var systemTempDir = Directory.systemTemp;
    var dir = systemTempDir.createTempSync();
    var file = File("${dir.path}/script");
    file.createSync();
    file.writeAsStringSync(script);
    try {
      return await exec('bash', [(file.absolute.path)]);
    } finally {
      dir.deleteSync(recursive: true);
    }
  }
}
