// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'package:plugin/plugin.dart' as plugin;

/// Run from IntelliJ with a run configuration that has the working directory
/// set to the project root directory.
main(List<String> arguments) async {
  await plugin.main(arguments);
}
