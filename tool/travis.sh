#!/bin/bash

# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Echo commands as they are executed.
set -v

# Echo build info.
echo $FLUTTER_SDK
flutter --version

# Set up the plugin tool.
echo "pub get"
pushd tool/plugin
pub get
popd

if [ "DART_BOT" = true ] ; then
  # analyze the Dart code in the repo
  pub global activate tuneup
  pub global run tuneup

  # run the tests for the plugin tool
  pushd tool/plugin
  dart test/plugin_test.dart
  popd
else
  # Run some validations on the repo code.
  ./bin/plugin lint

  # Run the build.
  ./bin/plugin build --only-version=$IDEA_VERSION
fi
