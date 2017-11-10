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
  List<String> data = await response.transform(UTF8.decoder).toList();

  // Remove an import and define the Color class.
  String str = data.join('');
  str = str.replaceFirst(
      "import 'dart:ui' show Color;", "import 'colors_main.dart';");
  str = str.replaceFirst("import 'package:flutter/painting.dart';", '');
  File file = new File('tool/colors/colors.dart');
  file.writeAsStringSync(str);

  // Run tool/color/colors_main.dart, pipe output to //resources/flutter/color.properties.
  ProcessResult result = Process
      .runSync(Platform.resolvedExecutable, ['tool/colors/colors_main.dart']);
  if (result.exitCode != 0) {
    fail('${result.stdout}\n${result.stderr}');
  }
  File outFile = new File('resources/flutter/colors.properties');
  outFile.writeAsStringSync(result.stdout);
  log('wrote ${outFile.path}');
}

@Task('Generate Flutter icon information')
icons() async {
  final String kUrl = 'https://raw.githubusercontent.com/flutter/flutter/'
      'master/dev/tools/update_icons.dart';

  // Get color file from flutter.
  HttpClientRequest request = await new HttpClient().getUrl(Uri.parse(kUrl));
  HttpClientResponse response = await request.close();
  List<String> data = await response.transform(UTF8.decoder).toList();
  File file = new File('tool/icons/update_icons.dart');
  file.writeAsStringSync(data.join(''));

  // Run tool/icons/icons_main.dart.
  await Dart.runAsync('tool/icons/icons_main.dart');
}
