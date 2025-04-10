// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io';

import 'package:args/command_runner.dart';

import 'build_spec.dart';
import 'globals.dart';
import 'util.dart';

class BuildCommandRunner extends CommandRunner<int> {
  BuildCommandRunner()
      : super(
          'plugin',
          'A script to build and test the Flutter IntelliJ plugin.',
        ) {
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

  void writeJxBrowserKeyToFile() {
    final jxBrowserKey = readTokenFromKeystore(
      'FLUTTER_KEYSTORE_JXBROWSER_KEY_NAME',
    );
    final propertiesFile = File(
      "$rootPath/resources/jxbrowser/jxbrowser.properties",
    );
    if (jxBrowserKey.isNotEmpty) {
      final contents = '''
jxbrowser.license.key=$jxBrowserKey
''';
      propertiesFile.writeAsStringSync(contents);
    }
  }

  Future<int> buildPlugin(BuildSpec spec, String version) async {
    writeJxBrowserKeyToFile();
    return await runGradleCommand(
      ['buildPlugin', '--stacktrace'],
      spec,
      version,
      'false',
    );
  }

  Future<int> runGradleCommand(
    List<String> command,
    BuildSpec spec,
    String version,
    String testing,
  ) async {
    final contents = '''
name = "flutter-intellij
buildSpec=${spec.version}
flutterPluginVersion=$version
ideaProduct=${spec.ideaProduct}
ideaVersion=${spec.ideaVersion}
baseVersion=${spec.baseVersion}
dartPluginVersion=${spec.dartPluginVersion}
androidPluginVersion=${spec.androidPluginVersion}
sinceBuild=${spec.sinceBuild}
untilBuild=${spec.untilBuild}
testing=$testing
javaVersion=${spec.javaVersion}
kotlin.stdlib.default.dependency=false
org.gradle.parallel=true
org.gradle.jvmargs=-Xms1024m -Xmx4048m
''';
    final propertiesFile = File("$rootPath/gradle.properties");
    final source = propertiesFile.existsSync()
        ? propertiesFile.readAsStringSync()
        : null;
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
        result = await exec('.\\third_party\\gradlew.bat', command);
      } else {
        if (spec.version == '4.1') {
          return await runShellScript(command, spec);
        } else {
          result = await exec('./third_party/gradlew', command);
        }
      }
    } finally {
      if (source != null) propertiesFile.writeAsStringSync(source);
    }
    return result;
  }

  Future<int> runShellScript(List<String> command, BuildSpec spec) async {
    var script = '''
#!/bin/bash
export JAVA_HOME=\$JAVA_HOME_OLD
./third_party/gradlew ${command.join(' ')}
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
