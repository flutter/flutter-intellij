#!/bin/bash

# Copyright 2018 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Get git.
apt-get -y update
apt-get -y install git-core curl unzip

# Get Flutter.
git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
export PATH="$PATH":`pwd`/../flutter/bin:`pwd`/../flutter/bin/cache/dart-sdk/bin
flutter config --no-analytics
flutter doctor
export FLUTTER_SDK=`pwd`/../flutter

# Echo build info.
echo $FLUTTER_SDK
flutter --version

# Get packages for the top-level grind script utilities
pub get

# Set up the plugin tool.
(cd tool/plugin; pub get)

# For testing cloudbuild, just run the Dart build variant.
export DART_BOT=true

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

  # Run the build.
  ./bin/plugin build --only-version=$IDEA_VERSION
fi
