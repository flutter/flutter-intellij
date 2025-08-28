/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import 'dart:convert';
import 'dart:io';

/// Pretty-prints the contents of json from the file in arg 0 to the file in
/// arg 1.
///
/// Usage:
/// $ dart bin/pretty_print.dart json_file_raw.txt json_file.txt
void main(List<String> args) {
  final fileContents = File(args[0]).readAsStringSync();
  final json = jsonDecode(fileContents);
  File(args[1]).writeAsStringSync(
    JsonEncoder.withIndent('  ').convert(json),
  );
  print('Contents of ${args[0]} pretty-printed to ${args[1]}');
}
