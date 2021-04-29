// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// @dart = 2.10

import 'dart:io';

import 'package:plugin_tool/plugin.dart' as plugin;

/// Run from IntelliJ with a run configuration that has the working directory
/// set to the project root directory.
void main(List<String> arguments) async {
  exit(await plugin.main(arguments));
}
