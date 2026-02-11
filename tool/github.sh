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
  # Define the baseline file location
  BASELINE="tool/verifier-baseline.txt"

  for version in 251 252; do
    echo "Running verifyPlugin for $version..."
    ./gradlew verifyPlugin -PsingleIdeVersion=$version || true

    # Path to the generated report
    REPORT="build/reports/pluginVerifier/report.md"

    if [ -f "$REPORT" ]; then
      # Extract all lines starting with '*' (the actual issues) from the report
      # This captures Compatibility Problems, Deprecated API usages, and Internal API usages.
      grep "^*" "$REPORT" | sort > current_issues.tmp

      if [ -f "$BASELINE" ]; then
        # Identify lines in the current report that are not in the baseline
        NEW_ERRORS=$(comm -13 <(sort "$BASELINE") current_issues.tmp)

        if [ -n "$NEW_ERRORS" ]; then
          echo "Error: New verification issues found for IDE version $version:"
          echo "$NEW_ERRORS"
          exit 1
        fi
      else
        echo "Warning: No baseline file found at $BASELINE. Skipping comparison."
      fi
    fi
  done

elif [ "INTEGRATION_BOT" = "$BOT" ]; then
  # Run the integration tests
  ./gradlew integration --warning-mode all

else
  # Run the build.
  ./gradlew buildPlugin

fi
