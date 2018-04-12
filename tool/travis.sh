#!/bin/bash

# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Echo build info.
echo $FLUTTER_SDK
flutter --version

# Get packages for the top-level grind script utilities
pub get

# Set up the plugin tool.
(cd tool/plugin; pub get)

if [ "$DART_BOT" = true ] ; then
  # analyze the Dart code in the repo
  pub global activate tuneup
  pub global run tuneup

  # ensure that the edits have been applied to template files (and they're target
  # files have been regenerated)
  ./bin/plugin generate

  # show any changed file
  git status --porcelain

  # return a failure exit code if there are any diffs
  git diff --exit-code

  # run the tests for the plugin tool
  (cd tool/plugin; dart test/plugin_test.dart)
else
  # Run some validations on the repo code.
  ./bin/plugin lint

if [ "$IDEA_VERSION" = "3.1" ] ; then
  # The 3.1 sources have a class defined in a different package than 3.2 and later.
  # Here we adjust the import statement for that change.
  sed -i 's/npw\.model\.NewModuleModel/npw.module.NewModuleModel/' flutter-studio/src/io/flutter/module/FlutterDescriptionProvider.java
fi

  # Run the build.
  ./bin/plugin build --only-version=$IDEA_VERSION
fi
