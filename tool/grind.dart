/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import 'package:grinder/grinder.dart';

main(List<String> args) => grind(args);

@Task('Generate a report of the API usage for the Dart plugin')
api() {
  String imports = run(
      'git',
      arguments: ['grep', 'import com.jetbrains.lang.dart.'],
      quiet: true
  );

  // path:import

  Map<String, List<String>> usages = {};

  imports.split('\n').forEach((String line) {
    if (line.trim().isEmpty) return;

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

  log('${keys.length} separate Dart plugin APIs used');
  log('');

  for (String import in keys) {
    log('$import:');
    List<String> places = usages[import];
    places.forEach((String place) => log('  $place'));
    log('');
  }
}
