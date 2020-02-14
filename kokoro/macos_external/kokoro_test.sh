#!/bin/bash

# This should execute as a presubmit.

# Fail on any error.
set -e

# Display commands being run. Only do this while debugging and be careful
# that no confidential information is displayed.
# set -x

# Code under repo is checked out to ${KOKORO_ARTIFACTS_DIR}/github.
# The final directory name in this path is determined by the scm name specified
# in the job configuration.
cd ${KOKORO_ARTIFACTS_DIR}/github/flutter-intellij-kokoro

if [[ $KOKORO_JOB_NAME =~ .*presubmit ]]; then
  ./tool/kokoro/test.sh
else
  echo "The build branch should not be reached in a test run."
  ./tool/kokoro/build.sh
fi
