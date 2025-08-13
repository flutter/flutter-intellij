#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro verify start"

./gradlew verifyPluginProjectConfiguration
./gradlew verifyPluginStructure
./gradlew verifyPluginSignature
./gradlew verifyPlugin

echo "kokoro verify finished"

echo "kokoro test start"

./gradlew test

echo "kokoro test finished"

echo "kokoro build start"

./gradlew buildPlugin -Pdev-version=88.0

echo "kokoro build finished"

echo "kokoro deploy start"
./bin/plugin deploy --channel=dev

echo "kokoro deploy finished"
