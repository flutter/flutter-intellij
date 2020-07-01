// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as path;

const flutterPackageSourceUrl =
    'https://raw.githubusercontent.com/flutter/flutter/'
    'master/packages/flutter/lib/src';
const materialColorsUrl = '$flutterPackageSourceUrl/material/colors.dart';
const cupertinoColorsUrl = '$flutterPackageSourceUrl/cupertino/colors.dart';
final materialFile = File('tool/colors/flutter/colors_material.dart');
final cupertinoFile = File('tool/colors/flutter/colors_cupertino.dart');
final generatedFilesPath = 'tool/colors/generated';

void main(List<String> args) async {
  // Verify that we're running from the project root.
  if (path.basename(Directory.current.path) != 'flutter-intellij') {
    print('Please run this script from the directory root.');
    exit(1);
  }

  print('Generating dart files:');
  await generateDartFiles();
}

Future<void> generateDartFiles() async {
  // TODO: Use the files from the local flutter checkout instead of a download
  // download material/colors.dart and cupertino/colors.dart
  await Future.wait([
    downloadFile(materialColorsUrl, materialFile),
    downloadFile(cupertinoColorsUrl, cupertinoFile)
  ]);

  // parse into metadata
  List<String> materialColors = extractColorNames(materialFile);
  List<String> cupertinoColors = extractColorNames(cupertinoFile);

  // generate .properties files
  generateDart(materialColors, 'colors_material.dart', 'Colors');
  generateDart(cupertinoColors, 'colors_cupertino.dart', 'CupertinoColors');
}

Future<void> downloadFile(String url, File file) async {
  RegExp imports = new RegExp(r'(?:^import.*;\n{1,})+', multiLine: true);
  HttpClient client = new HttpClient();
  try {
    HttpClientRequest request = await client.getUrl(Uri.parse(url));
    HttpClientResponse response = await request.close();
    List<String> data = await utf8.decoder.bind(response).toList();
    String contents =
        data.join('').replaceFirst(imports, "import '../stubs.dart';\n\n");

    file.writeAsStringSync(
      '// This file was downloaded by update_colors.dart.\n\n'
      '$contents',
    );
  } finally {
    client.close();
  }
}

// The pattern below is meant to match lines like:
//   'static const Color black45 = Color(0x73000000);'
//   'static const MaterialColor cyan = MaterialColor('
final RegExp regexpColor1 =
    new RegExp(r'static const \w*Color (\S+) = \w*Color\(');
// The pattern below is meant to match lines like:
//   'static const CupertinoDynamicColor activeBlue = systemBlue;'
final RegExp regexpColor2 = new RegExp(r'static const \w*Color (\S+) = \w+;');
// The pattern below is meant to match lines like:
//   'static const CupertinoDynamicColor systemGreen = CupertinoDynamicColor.withBrightnessAndContrast('
final RegExp regexpColor3 =
    new RegExp(r'static const \w*Color (\S+) = \w+.\w+\(');

List<String> extractColorNames(File file) {
  String data = file.readAsStringSync();

  List<String> names = [
    ...regexpColor1.allMatches(data).map((Match match) => match.group(1)),
    ...regexpColor2.allMatches(data).map((Match match) => match.group(1)),
    ...regexpColor3.allMatches(data).map((Match match) => match.group(1)),
  ];

  // Remove any duplicates.
  return Set<String>.from(names).toList();
}

void generateDart(List<String> colors, String filename, String className) {
  StringBuffer buf = StringBuffer();
  buf.writeln('''
// Generated file - do not edit.

import '../stubs.dart';
import '../flutter/${filename}';

final Map<String, Color> colors = <String, Color>{''');

  for (String colorName in colors) {
    buf.writeln("  '${colorName}': ${className}.${colorName},");
  }

  buf.writeln('};');

  File out = File('$generatedFilesPath/$filename');
  out.writeAsStringSync(buf.toString());

  print('wrote ${out.path}');
}
