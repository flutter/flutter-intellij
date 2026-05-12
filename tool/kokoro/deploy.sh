#!/bin/bash

# To run this script locally from the repository root:
# JB_MARKETPLACE_TOKEN="your_real_token" ./tool/kokoro/deploy.sh

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

./gradlew buildPlugin -Pdev --no-configuration-cache

echo "kokoro build finished"

echo "kokoro deploy start"

if [ -n "$JB_MARKETPLACE_TOKEN" ]; then
  TOKEN="$JB_MARKETPLACE_TOKEN"
  echo "Using token from JB_MARKETPLACE_TOKEN environment variable."
else
  KOKORO_TOKEN_FILE="${KOKORO_KEYSTORE_DIR}/${FLUTTER_KEYSTORE_ID}_${FLUTTER_KEYSTORE_NAME}"
  if [ ! -f "$KOKORO_TOKEN_FILE" ]; then
    echo "Error: Keystore token file not found at $KOKORO_TOKEN_FILE"
    echo "Please set JB_MARKETPLACE_TOKEN or ensure KOKORO_KEYSTORE_DIR is set correctly."
    exit 1
  fi
  TOKEN=$(cat "$KOKORO_TOKEN_FILE")
fi

ZIP_FILE="build/distributions/flutter-intellij-kokoro.zip"
if [ ! -f "$ZIP_FILE" ]; then
  ZIP_FILE="build/distributions/flutter-intellij.zip"
fi
if [ ! -f "$ZIP_FILE" ]; then
  echo "Error: Zip file not found at $ZIP_FILE"
  exit 1
fi

echo "Uploading $ZIP_FILE to JetBrains Marketplace..."
curl -if \
  --header "Authorization: Bearer $TOKEN" \
  -F pluginId=9212 \
  -F file=@"$ZIP_FILE" \
  -F channel=dev \
  https://plugins.jetbrains.com/plugin/uploadPlugin

echo "kokoro deploy finished"
