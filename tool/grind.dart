// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:convert';
import 'dart:io';

import 'package:grinder/grinder.dart';

main(List<String> args) => grind(args);

@Task('Generate a report of the API usage for the Dart plugin')
api() {
  String imports = run(
    'git',
    // Note: extra quotes added so grep doesn't match this file.
    arguments: ['grep', 'import com.jetbrains.' 'lang.dart.'],
    quiet: true,
  );

  // path:import

  Map<String, List<String>> usages = {};

  imports.split('\n').forEach((String line) {
    if (line
        .trim()
        .isEmpty)
      return;

    int index = line.indexOf(':');
    String place = line.substring(0, index);
    String import = line.substring(index + 1);
    if (import.startsWith('import ')) import = import.substring(7);
    if (import.endsWith(';')) import = import.substring(0, import.length - 1);
    usages.putIfAbsent(import, () => []);
    usages[import].add(place);
  });

  // print report
  List<String> keys = usages.keys.toList();
  keys.sort();

  log('travis_fold:start:apis.used');
  log('${keys.length} separate Dart plugin APIs used:');
  log('');

  for (String import in keys) {
    log('$import:');
    List<String> places = usages[import];
    places.forEach((String place) => log('  $place'));
    log('');
  }
  log('travis_fold:end:apis.used');
}

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
