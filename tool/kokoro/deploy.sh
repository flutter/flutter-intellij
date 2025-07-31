#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

./third_party/gradlew buildPlugin -Pdev-version=88.0

echo "kokoro build finished"

echo "kokoro deploy start"
./bin/plugin deploy --channel=dev

echo "kokoro deploy finished"
