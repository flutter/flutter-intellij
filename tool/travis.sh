#!/bin/bash

# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Echo build info.
echo $FLUTTER_SDK
flutter --version

# Print a report for the API used from the Dart plugin
echo -en 'travis_fold:start:pub.get\\r'
echo "pub get"
pub get
echo -en 'travis_fold:end:pub.get\\r'

dart tool/grind.dart api

# Run the ant build.
if [ "$UNIT_TEST" = "true" ]
then
  if [ -z "$DART_PLUGIN_VERSION" ]
  then
    ant build test -Didea.product=$IDEA_PRODUCT -Didea.version=$IDEA_VERSION
  else
    ant build test -Didea.product=$IDEA_PRODUCT -Didea.version=$IDEA_VERSION -Ddart.plugin.version=$DART_PLUGIN_VERSION
  fi
else
  if [ -z "$DART_PLUGIN_VERSION" ]
  then
    ant build -Didea.product=$IDEA_PRODUCT -Didea.version=$IDEA_VERSION
  else
    ant build -Didea.product=$IDEA_PRODUCT -Didea.version=$IDEA_VERSION -Ddart.plugin.version=$DART_PLUGIN_VERSION
  fi
fi
