#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

./gradlew buildPlugin -Pdev

echo "kokoro build finished"

echo "kokoro deploy start"

KOKORO_TOKEN_FILE="${KOKORO_KEYSTORE_DIR}/${FLUTTER_KEYSTORE_ID}_${FLUTTER_KEYSTORE_NAME}"
if [ ! -f "$KOKORO_TOKEN_FILE" ]; then
  echo "Error: Keystore token file not found at $KOKORO_TOKEN_FILE"
  exit 1
fi
TOKEN=$(cat "$KOKORO_TOKEN_FILE")

ZIP_FILE="build/distributions/flutter-intellij-kokoro.zip"
if [ ! -f "$ZIP_FILE" ]; then
  echo "Error: Zip file not found at $ZIP_FILE"
  exit 1
fi

echo "Uploading $ZIP_FILE to JetBrains Marketplace..."
curl -if --fail \
  --header "Authorization: Bearer $TOKEN" \
  -F pluginId=9212 \
  -F file=@"$ZIP_FILE" \
  -F channel=dev \
  https://plugins.jetbrains.com/plugin/uploadPlugin

echo "kokoro deploy finished"
