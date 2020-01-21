#!/bin/bash

# Fail on any error.
set -e

# Display commands being run. Only do this while debugging and be careful
# that no confidential information is displayed.
# set -x

./bin/plugin test

echo "kokoro test finished"
