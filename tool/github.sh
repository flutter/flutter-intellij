#!/bin/bash

# Copyright 2020 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

export JAVA_HOME=$JAVA_HOME_11_X64

git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
export PATH="$PATH":`pwd`/../flutter/bin:`pwd`/../flutter/bin/cache/dart-sdk/bin
flutter config --no-analytics
flutter doctor
export FLUTTER_SDK=`pwd`/../flutter

if [ "$IDEA_VERSION" = "4.0" -o "$IDEA_VERSION" = "4.1" ] ; then

  # Install Java 8 if running on 4.0 or 4.1.
  wget -O- https://apt.corretto.aws/corretto.key | sudo apt-key add -
  sudo add-apt-repository 'deb https://apt.corretto.aws stable main'
  sudo apt-get update; sudo apt-get install -y java-1.8.0-amazon-corretto-jdk
  export PATH=/usr/lib/jvm/java-1.8.0-amazon-corretto/jre/bin:$PATH

fi

java -version

# Get packages for the top-level grind script utilities.
echo "pub get `pwd`"
pub get

# Get packages for the test data.
(cd testData/sample_tests; echo "pub get `pwd`"; pub get)

# Set up the plugin tool.
(cd tool/plugin; echo "pub get `pwd`"; pub get)

if [ "DART_BOT" = "$BOT" ] ; then

  # Analyze the Dart code in the repo.
  chmod 000 flutter-idea # symlinks seem to be causing problems
  echo "dart analyze"
  dart analyze
  chmod 755 flutter-idea

  # Ensure that the edits have been applied to template files (and their target
  # files have been regenerated).
  ./bin/plugin generate

  # Show any changed files.
  git status --porcelain

  # Return a failure exit code if there are any diffs.
  git diff --exit-code

  # Run the tests for the plugin tool.
  (cd tool/plugin; dart test/plugin_test.dart)

elif [ "CHECK_BOT" = "$BOT" ] ; then

  # Run some validations on the repo code.
  ./bin/plugin lint

  # Check plugin-referenced urls for liveness.
  dart tool/grind.dart check-urls

elif [ "UNIT_TEST_BOT" = "$BOT" ] ; then

  # Run unit tests.
  ./bin/plugin test

else

  # Run the build.
  ./bin/plugin make --only-version=$IDEA_VERSION

fi
