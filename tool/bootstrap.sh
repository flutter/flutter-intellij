#!/bin/bash

# Copyright 2019 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

if [[ $TRAVIS_OS_NAME == "windows" ]]; then

  choco install jdk11 -params "source=false"
  java -version

  choco install ant
  echo $(ant -version)

else

  sudo apt-get update
  sudo apt-get install ant

  echo $(ant -version)

fi
