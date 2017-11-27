// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io';

import 'package:plugin/plugin.dart' as plugin;

/// Run from IntelliJ with a run configuration that has the working directory
/// set to the project root directory.
Future<int> main(List<String> arguments) async {
  var result = await plugin.main(arguments);
  exit(result);
  return result; // Not reached.
}
