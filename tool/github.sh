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

if ! command -v flutter &> /dev/null; then
  echo "Error: flutter command not found on PATH after cloning the repo."
  exit 1
fi
echo "flutter command found: $(which flutter)"

if ! command -v dart &> /dev/null; then
  echo "Error: dart command not found on PATH after cloning the repo."
  exit 1
fi
echo "dart command found: $(which dart)"

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
  (cd src && dart analyze)
  (cd tool/plugin && dart analyze)
  (cd tool/triage && dart pub upgrade && dart analyze)


  # Ensure that the edits have been applied to template files (and their target
  # files have been regenerated).
  ./bin/plugin generate

  # Show any changed files.
  git status --porcelain

  # Return a failure exit code if there are any diffs.
  git diff --exit-code

  # Run the tests for the plugin tool.
  (cd tool/plugin; dart test/plugin_test.dart)

setup_intellij() {
  echo "Setting up IntelliJ IDEA 2025.3 for Inspection/Formatting..."
  # Note: AARCH64 URL provided by user, ensure runner is compatible or use x64 if needed.
  wget -qO idea.tar.gz https://download.jetbrains.com/idea/idea-2025.3.1.1-aarch64.tar.gz
  mkdir idea
  tar -xzf idea.tar.gz -C idea --strip-components=1
  export IDEA_HOME="$(pwd)/idea"
}

if [ "FORMAT_BOT" = "$BOT" ] ; then
  setup_intellij
  # Check for formatting issues
  echo "./tool/format.sh"
  ./tool/format.sh
  git diff --exit-code

elif [ "INSPECT_BOT" = "$BOT" ] ; then
  setup_intellij
  echo "Running verify_inspections.sh..."
  ./tool/verify_inspections.sh

elif [ "CHECK_BOT" = "$BOT" ] ; then
  # Run some validations on the repo code.
  ./bin/plugin lint

  # Check plugin-referenced urls for liveness.
  dart tool/grind.dart check-urls

elif [ "UNIT_TEST_BOT" = "$BOT" ] ; then
  # Run unit tests.
  ./gradlew test

elif [ "VERIFY_BOT" = "$BOT" ] ; then
  # Run the verifier
  echo "Check on space before verifyPluginProjectConfiguration\n"
  df -h
  ./gradlew verifyPluginProjectConfiguration
  echo "Check on space before verifyPluginStructure\n"
  df -h
  ./gradlew verifyPluginStructure
  echo "Check on space before verifyPluginSignature\n"
  df -h
  ./gradlew verifyPluginSignature

  for version in 251 252; do
    echo "Check on space before verifyPlugin for $version\n"
    df -h
    ./gradlew verifyPlugin -PsingleIdeVersion=$version
  done

  echo "Check on space after verifyPlugin"
  df -h

elif [ "INTEGRATION_BOT" = "$BOT" ]; then
  # Run the integration tests
  ./gradlew integration --warning-mode all

else
  # Run the build.
  ./gradlew buildPlugin

fi
