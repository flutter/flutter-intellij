#!/bin/bash

# Build two distribution files:
#   artifacts/flutter-intellij.jar
#   artifacts/flutter-studio.zip

# fast fail the script on failures.
set -e

# clean the build
rm -rf build/*

# build for Android Studio
# TODO(devoncarew): The version range below (supported for Android Studio) is
# a temporary measure.
ant \
  -Ddart.plugin.version=171.4424.10 \
  -Didea.version=171.4408382 \
  -Didea.product=android-studio-ide \
  -DSINCE=171.1 -DUNTIL="173.*"
mv build/flutter-studio.zip artifacts

# build for IntelliJ
# Note: built against IntelliJ 2017.1 for runtime compatibility with Android Studio.
rm -rf build/*
ant \
  -Ddart.plugin.version=172.3317.48 \
  -Didea.version=2017.1 \
  -Didea.product=ideaIC
mv build/flutter-intellij.jar artifacts
