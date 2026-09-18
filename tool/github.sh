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

# Download and configure Flutter to the pinned stable release if not present
source ./tool/provision_flutter.sh
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

  # Validate agent skills.
  dart tool/grind.dart lint-skills

  # Check plugin and documentation URLs for liveness.
  dart tool/grind.dart check-urls

elif [ "UNIT_TEST_BOT" = "$BOT" ] ; then
  # Run unit tests.
  ./gradlew test

elif [ "VERIFY_BOT" = "$BOT" ] ; then
  # `verifyPlugin` gates on severity via `failureLevel` in build.gradle.kts.
  # Every verification below records its own status rather than aborting the
  # bot, so that a failure in one still reports the others, and so that the
  # baseline check always runs to explain *which* issues are new -- that
  # diagnosis is the whole point, and it used to be skipped precisely when the
  # verifier failed.
  EXIT_STATUS=0

  verify() {
    local name="$1"
    shift
    echo -e "${BOLD}Running $name...${NC}"
    echo "Check on space before $name"
    df -h
    if ! "$@"; then
      EXIT_STATUS=1
      echo -e "${RED}${BOLD}$name failed.${NC}"
      echo "::error title=$name failed::See the ${BOT} job log for details."
    fi
  }

  verify verifyPluginProjectConfiguration ./gradlew verifyPluginProjectConfiguration
  verify verifyPluginStructure ./gradlew verifyPluginStructure
  verify verifyPluginSignature ./gradlew verifyPluginSignature

  # One IDE at a time: with `singleIdeVersion` set, each run deletes its IDE
  # afterwards (see build.gradle.kts), which is what keeps the bot from running
  # out of disk. The reports accumulate, so the baseline check below sees all
  # of them.
  #
  # The exit status is deliberately ignored. `failureLevel` in build.gradle.kts
  # judges an issue by severity alone, with no notion of whether we have
  # already accepted it, so a baselined issue in an enabled category would fail
  # every run with no way to suppress it. The baseline check below is the
  # verdict: it fails on anything new, in any category. Note that this only
  # discards the *status* -- a problem serious enough to matter still appears
  # in the report, and so is still caught if it is new.
  for version in 252 253 261; do
    echo -e "${BOLD}Running verifyPlugin for $version...${NC}"
    echo "Check on space before verifyPlugin for $version"
    df -h
    ./gradlew verifyPlugin -PsingleIdeVersion=$version || true
  done

  echo "Check on space after verifyPlugin"
  df -h

  # Diffs every report produced above against tool/baseline/<IDE branch>/.
  # New problems are surfaced as job annotations and in the job summary.
  ./tool/check_verifier_baselines.sh check || EXIT_STATUS=1

  if [ $EXIT_STATUS -ne 0 ]; then
    exit 1
  fi

elif [ "INTEGRATION_BOT" = "$BOT" ]; then
  # Run the integration tests
  ./gradlew integration --warning-mode all

else
  # Run the build.
  ./gradlew buildPlugin

fi
