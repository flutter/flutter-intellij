#!/bin/bash

# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Echo build info.
echo $FLUTTER_SDK
flutter --version

# Run the gradle build.
# TODO: Tests are currently disabled (#222).
gradle buildPlugin --info

# Print a report for the API used from the Dart plugin
pub get
dart tool/grind.dart api
