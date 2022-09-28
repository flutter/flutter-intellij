#!/bin/bash

source ./tool/kokoro/setup.sh
setup

(cd flutter-idea/testData/sample_tests; echo "dart pub get `pwd`"; dart pub get --no-precompile)

echo "kokoro test start"
./bin/plugin test

echo "kokoro test finished"
