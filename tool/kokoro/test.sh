#!/bin/bash

source ./tool/kokoro/setup.sh
setup

(cd testData/sample_tests; echo "dart pub get `pwd`"; dart pub get --no-precompile)

echo "kokoro test start"
./gradlew test

echo "kokoro test finished"
