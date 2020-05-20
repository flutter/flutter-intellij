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
echo "travis_fold:start:pub_get"
echo "pub get `pwd`"
pub get
echo "travis_fold:end:pub_get"

# Get packages for the test data.
echo "travis_fold:start:pub_get"
(cd testData/sample_tests; echo "pub get `pwd`"; pub get)
echo "travis_fold:end:pub_get"

# Set up the plugin tool.
echo "travis_fold:start:pub_get"
(cd tool/plugin; echo "pub get `pwd`"; pub get)
echo "travis_fold:end:pub_get"

if [ "$DART_BOT" = true ] ; then

  # analyze the Dart code in the repo
  echo "travis_fold:start:activate_tuneup"
  echo "pub global activate tuneup"
  pub global activate tuneup
  echo "travis_fold:end:activate_tuneup"
  pub global run tuneup

  # ensure that the edits have been applied to template files (and their target
  # files have been regenerated)
  ./bin/plugin generate

  # show any changed file
  git status --porcelain

  # return a failure exit code if there are any diffs
  git diff --exit-code

  # run the tests for the plugin tool
  (cd tool/plugin; dart test/plugin_test.dart)

elif [ "$CHECK_BOT" = true ] ; then

  # Run some validations on the repo code.
  ./bin/plugin lint

  # Check plugin-referenced urls for liveness.
  dart tool/grind.dart check-urls

elif [ "$UNIT_TEST_BOT" = true ] ; then

  # Run unit tests.
  ./bin/plugin test

else

  # Run the build.
  ./bin/plugin make --only-version=$IDEA_VERSION

fi
