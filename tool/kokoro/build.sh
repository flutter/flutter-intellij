#!/bin/bash

# Fail on any error.
set -e

# Display commands being run. Only do this while debugging and be careful
# that no confidential information is displayed.
# set -x

if [ "$1" == "release" ]; then
  # do release build
  echo "release build"
else
  # do dev build
  echo "dev build"
fi
echo "kokoro build finished"
