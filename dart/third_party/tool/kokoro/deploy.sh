#!/bin/bash

# To run this script locally from the repository root:
# JB_MARKETPLACE_TOKEN="your_real_token" ./third_party/tool/kokoro/deploy.sh

source ./third_party/tool/kokoro/setup.sh
setup

echo "kokoro build start"

cd third_party

VERSION=$(run_gradle_with_retry -q printVersion -Pdev --no-configuration-cache | tail -n 1)

run_gradle_with_retry buildPlugin -Pdev --info

echo "kokoro build finished"

echo "kokoro deploy start"

DART_KEYSTORE_ID=74840
DART_KEYSTORE_NAME=jetbrains-plugin-upload-auth-token

if [ -n "$JB_MARKETPLACE_TOKEN" ]; then
  TOKEN="$JB_MARKETPLACE_TOKEN"
  echo "Using token from JB_MARKETPLACE_TOKEN environment variable."
else
  KOKORO_TOKEN_FILE="${KOKORO_KEYSTORE_DIR}/${DART_KEYSTORE_ID}_${DART_KEYSTORE_NAME}"
  if [ ! -f "$KOKORO_TOKEN_FILE" ]; then
    echo "Error: Keystore token file not found at $KOKORO_TOKEN_FILE"
    echo "Please set JB_MARKETPLACE_TOKEN or ensure KOKORO_KEYSTORE_DIR is set correctly."
    exit 1
  fi
  TOKEN=$(cat "$KOKORO_TOKEN_FILE")
fi

ZIP_FILE="build/distributions/Dart.zip"
if [ ! -f "$ZIP_FILE" ]; then
  echo "Error: Zip file not found at $ZIP_FILE"
  exit 1
fi

echo "Uploading $ZIP_FILE to JetBrains Marketplace..."
curl -i \
  --header "Authorization: Bearer $TOKEN" \
  -F pluginId=6351 \
  -F file=@"$ZIP_FILE" \
  -F channel=dev \
  https://plugins.jetbrains.com/plugin/uploadPlugin

echo "kokoro deploy finished"
