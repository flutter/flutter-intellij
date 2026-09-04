#!/bin/bash

# This script should execute after a commit.
date
echo "Automatic build started"

# Fail on any error.
set -e


# Code under repo is checked out to ${KOKORO_ARTIFACTS_DIR}/github.
cd ${KOKORO_ARTIFACTS_DIR}/github/dart-intellij-third-party

./third_party/tool/kokoro/build.sh
