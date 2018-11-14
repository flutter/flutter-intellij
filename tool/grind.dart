// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:convert';
import 'dart:io';

import 'package:grinder/grinder.dart';

main(List<String> args) => grind(args);

@Task()
@Depends(colors, icons)
generate() => null;

@Task('Generate Flutter color information')
colors() async {
  final String kUrl = 'https://raw.githubusercontent.com/flutter/flutter/'
      'master/packages/flutter/lib/src/material/colors.dart';

  // Get color file from flutter.
  HttpClientRequest request = await new HttpClient().getUrl(Uri.parse(kUrl));
  HttpClientResponse response = await request.close();
  List<String> data = await response.transform(utf8.decoder).toList();

  // Remove an import and define the Color class.
  String str = data.join('');
  str = str.replaceFirst(
      "import 'dart:ui' show Color;", "import 'colors_main.dart';");
  str = str.replaceFirst("import 'package:flutter/painting.dart';", '');
  File file = new File('tool/colors/colors.dart');
  file.writeAsStringSync(str);

  // Run tool/color/colors_main.dart, pipe output to //resources/flutter/color.properties.
  ProcessResult result = Process.runSync(
      Platform.resolvedExecutable, ['tool/colors/colors_main.dart']);
  if (result.exitCode != 0) {
    fail('${result.stdout}\n${result.stderr}');
  }
  File outFile = new File('resources/flutter/colors.properties');
  outFile.writeAsStringSync(result.stdout);
  log('wrote ${outFile.path}');
}

@Task('Generate Flutter icon information')
icons() async {
  // Run tool/icons/update_icons.dart.
  await Dart.runAsync('tool/icons/update_icons.dart');
}

@Task('Create Outline view icons from svgs')
outlineIcons() async {
  Directory previewIconsDir = getDir('resources/icons/preview');

  log('using svgexport (npm install -g svgexport)');

  for (File file in previewIconsDir
      .listSync()
      .where((file) => file.path.endsWith('.svg'))) {
    log('processing ${file.path}...');

    String name = fileName(file);
    name = name.substring(0, name.indexOf('.'));
    _createPng(file, '$name.png', size: 28, forLight: true);
    _createPng(file, '$name@2x.png', size: 56, forLight: true);
    _createPng(file, '${name}_dark.png', size: 28, forLight: false);
    _createPng(file, '$name@2x_dark.png', size: 56, forLight: false);
  }
}

void _createPng(
  File sourceSvg,
  String targetName, {
  int size: 28,
  bool forLight: false,
}) {
  File targetFile = joinFile(sourceSvg.parent, [targetName]);

  String color = forLight ? '#7a7a7a' : '#9e9e9e';

  String originalContent = sourceSvg.readAsStringSync();
  String newContent =
      originalContent.replaceAll('<svg ', '<svg fill="$color" ');

  sourceSvg.writeAsStringSync(newContent);

  try {
    ProcessResult result = Process.runSync('svgexport', [
      sourceSvg.path,
      targetFile.path,
      '100%',
      '$size:$size',
    ]);

    if (result.exitCode != 0) {
      print(
          'Error resizing image with imagemagick: ${result.stdout}\n${result.stderr}');
      exit(1);
    }
  } finally {
    sourceSvg.writeAsStringSync(originalContent);
  }
}
