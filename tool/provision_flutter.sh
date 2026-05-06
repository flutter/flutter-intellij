#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fail on any error.
set -e

# Provision the pinned Flutter SDK if not present
if [ ! -d "../flutter" ]; then
  OS_NAME=$(uname -s | tr '[:upper:]' '[:lower:]')
  # Pinned Flutter SDK version. This constant is automatically checked and updated weekly
  # by the .github/workflows/update_flutter.yaml GitHub Actions workflow.
  FLUTTER_VERSION="3.41.0"
  
  echo "Provisioning Flutter SDK version ${FLUTTER_VERSION} for ${OS_NAME}..."
  if [ "$OS_NAME" = "darwin" ]; then
    curl -fLO "https://storage.googleapis.com/flutter_infra_release/releases/stable/macos/flutter_macos_${FLUTTER_VERSION}-stable.zip"
    unzip -q "flutter_macos_${FLUTTER_VERSION}-stable.zip" -d ../
    rm "flutter_macos_${FLUTTER_VERSION}-stable.zip"
  else
    curl -fLO "https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz"
    tar xf "flutter_linux_${FLUTTER_VERSION}-stable.tar.xz" -C ../
    rm "flutter_linux_${FLUTTER_VERSION}-stable.tar.xz"
  fi
else
  echo "../flutter already exists, skipping download."
fi
