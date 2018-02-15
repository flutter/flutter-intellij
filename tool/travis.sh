#!/bin/bash

# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Echo build info.
echo $FLUTTER_SDK
flutter --version

# Set up the plugin tool.
echo "pub get"
(cd tool/plugin; pub get)

# Run some validations on the repo code.
echo "plugin lint"
./bin/plugin lint

# Run the build.
bin/plugin build --only-version=$IDEA_VERSION
