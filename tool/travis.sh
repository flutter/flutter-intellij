#!/bin/bash

# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Download and setup Flutter.
git clone https://github.com/flutter/flutter.git --depth 1
export PATH="$PATH":flutter/bin
flutter precache
export FLUTTER_SDK=flutter

# Run the gradle build.
gradle build --info
