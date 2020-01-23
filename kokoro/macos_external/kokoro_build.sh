#!/bin/bash

# This script should execute during the scheduled builds.
date
echo "Automatic build started"

# Fail on any error.
set -e

# Display commands being run. Only do this while debugging and be careful
# that no confidential information is displayed.
# set -x

# Code under repo is checked out to ${KOKORO_ARTIFACTS_DIR}/github.
# The final directory name in this path is determined by the scm name specified
# in the job configuration.
cd ${KOKORO_ARTIFACTS_DIR}/github/flutter-intellij-kokoro

./tool/kokoro/build.sh
