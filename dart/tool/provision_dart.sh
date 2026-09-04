#!/bin/bash
# Copyright 2024 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fail on any error.
set -e

# Provision the pinned Dart SDK if not present
if [ ! -d "../dart-sdk" ]; then
  OS_NAME=$(uname -s | tr '[:upper:]' '[:lower:]')
  ARCH_NAME=$(uname -m)
  
  if [ "$ARCH_NAME" = "aarch64" ] || [ "$ARCH_NAME" = "arm64" ]; then
    ARCH="arm64"
  else
    ARCH="x64"
  fi

  # Pinned Dart SDK version. This constant is automatically checked and updated weekly
  # by the .github/workflows/update_dart.yaml GitHub Actions workflow.
  DART_VERSION="3.13.0"
  
  echo "Provisioning Dart SDK version ${DART_VERSION} for ${OS_NAME}-${ARCH}..."
  
  if [ "$OS_NAME" = "darwin" ]; then
    OS="macos"
  elif [ "$OS_NAME" = "linux" ]; then
    OS="linux"
  elif [[ "$OS_NAME" == mingw* ]] || [[ "$OS_NAME" == cygwin* ]] || [[ "$OS_NAME" == msys* ]]; then
    OS="windows"
  else
    echo "Unsupported OS: $OS_NAME"
    exit 1
  fi
  
  URL="https://storage.googleapis.com/dart-archive/channels/stable/release/${DART_VERSION}/sdk/dartsdk-${OS}-${ARCH}-release.zip"
  
  curl -fLO "$URL"
  unzip -q "dartsdk-${OS}-${ARCH}-release.zip" -d ../
  rm "dartsdk-${OS}-${ARCH}-release.zip"
else
  echo "../dart-sdk already exists, skipping download."
fi
