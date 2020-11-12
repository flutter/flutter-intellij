// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:io';

import 'package:grinder/grinder.dart';
import 'package:http/http.dart' as http;
import 'package:meta/meta.dart';

void main(List<String> args) => grind(args);

@Task('Check plugin URLs for liveness')
void checkUrls() async {
  log('checking URLs in FlutterBundle.properties...');
  var lines =
      await new File('src/io/flutter/FlutterBundle.properties').readAsLines();
  for (var line in lines) {
    var split = line.split('=');
    if (split.length == 2) {
      // flutter.io.gettingStarted.url | flutter.analytics.privacyUrl
      if (split[0].toLowerCase().endsWith('url')) {
        var url = split[1];
        var response = await http.get(url);
        log('checking: $url...');
        if (response.statusCode != 200) {
          fail(
              '$url GET failed: [${response.statusCode}] ${response.reasonPhrase}');
        }
      }
    }
  }
  log('OK!');
}

@Task('Create Outline view icons from svgs')
void outlineIcons() async {
  Directory previewIconsDir = getDir('resources/icons/preview');

  log('using svgexport (npm install -g svgexport)');

  for (File file in previewIconsDir
      .listSync()
      .where((entity) => entity is File)
      .cast<File>()
      .where((file) => file.path.endsWith('.svg'))) {
    log('processing ${file.path}...');

    String name = fileName(file);
    name = name.substring(0, name.indexOf('.'));
    _createPng(file, '$name.png', size: 24, forLight: true);
    _createPng(file, '$name@2x.png', size: 48, forLight: true);
    _createPng(file, '${name}_dark.png', size: 24, forLight: false);
    _createPng(file, '$name@2x_dark.png', size: 48, forLight: false);
  }
}

void _createPng(
  File sourceSvg,
  String targetName, {
  @required int size,
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
