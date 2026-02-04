#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro verify start"

./gradlew verifyPluginProjectConfiguration
./gradlew verifyPluginStructure
./gradlew verifyPluginSignature

for version in 251 252 253; do
  echo "Check on space before verifyPlugin for $version\n"
  df -h
  ./gradlew verifyPlugin -PsingleIdeVersion=$version
done

echo "kokoro verify finished"

echo "kokoro test start"

./gradlew test

echo "kokoro test finished"

echo "kokoro build start"

./gradlew buildPlugin -Pdev

echo "kokoro build finished"

echo "kokoro deploy start"
./bin/plugin deploy --channel=dev

echo "kokoro deploy finished"
