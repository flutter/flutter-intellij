#!/bin/bash

# Copyright 2020 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Log Java information that can be used whenever Java needs to be updated
echo "ls /usr/lib/jvm"
ls /usr/lib/jvm
echo "System Java version:"
java --version
echo "export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64"
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
echo "ls $JAVA_HOME"
ls $JAVA_HOME
# Path is not used by the build, only by java --version
echo "export PATH=$JAVA_HOME/bin:\$PATH"
export PATH=$JAVA_HOME/bin:$PATH

# Clone and configure Flutter to the latest stable release
git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
export PATH="$PATH":`pwd`/../flutter/bin:`pwd`/../flutter/bin/cache/dart-sdk/bin
flutter config --no-analytics
flutter doctor
export FLUTTER_SDK=`pwd`/../flutter

echo "java --version"
java --version

# Get packages for the top-level grind script utilities.
echo "pub get `pwd`"
dart pub get

# Get packages for the test data.
(cd testData/sample_tests; echo "dart pub get `pwd`"; dart pub get)

# Set up the plugin tool.
(cd tool/plugin; echo "dart pub get `pwd`"; dart pub get)

# Color constants.
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m' # None (Reset)

if [ "DART_BOT" = "$BOT" ] ; then
  # Analyze the Dart code in the repo.
  echo "dart analyze"
  (cd src; dart analyze)
  (cd tool/plugin; dart analyze)
  (cd tool/triage; dart pub upgrade && dart analyze)

  # Ensure that the edits have been applied to template files (and their target
  # files have been regenerated).
  ./bin/plugin generate

  # Show any changed files.
  git status --porcelain

  # Return a failure exit code if there are any diffs.
  git diff --exit-code

  # Run the tests for the plugin tool.
  (cd tool/plugin; dart test/plugin_test.dart)

elif [ "CHECK_BOT" = "$BOT" ] ; then
  # Run some validations on the repo code.
  ./bin/plugin lint

  # Check plugin-referenced urls for liveness.
  dart tool/grind.dart check-urls

elif [ "UNIT_TEST_BOT" = "$BOT" ] ; then
  # Run unit tests.
  ./gradlew test

elif [ "VERIFY_BOT" = "$BOT" ] ; then
  EXIT_STATUS=0

  for version in 251 252; do
    echo "${BOLD}Running verifyPlugin for $version...${NC}"
    ./gradlew verifyPlugin -PsingleIdeVersion=$version || true

    BASELINE="$GITHUB_WORKSPACE/tool/baseline/$version/verifier-baseline.txt"
    REPORT=$(find build/reports/pluginVerifier -name "report.md" | head -n 1)

    if [ -f "$REPORT" ]; then
      grep "^*" "$REPORT" | sort > current_issues.tmp

      if [ -f "$BASELINE" ]; then
        NEW_ERRORS=$(comm -13 <(sort "$BASELINE") current_issues.tmp)

        if [ -n "$NEW_ERRORS" ]; then
          echo -e "${RED}${BOLD}Error: New verification issues found for version $version:${NC}"
          echo "$NEW_ERRORS"
          EXIT_STATUS=1
        else
          echo -e "${GREEN}Verification passed for version $version (no new issues).${NC}"
        fi
      else
        echo "${YELLOW}Warning: No baseline file found at $BASELINE. Skipping comparison.${NC}"
      fi
    fi
  done

  echo "Check on space after verifyPlugin"
  df -h

  if [ $EXIT_STATUS -ne 0 ]; then
    echo -e "${RED}${BOLD}Build failed: New verification issues were detected.${NC}"
    exit 1
  fi

elif [ "INTEGRATION_BOT" = "$BOT" ]; then
  # Run the integration tests
  ./gradlew integration --warning-mode all

else
  # Run the build.
  ./gradlew buildPlugin

fi
