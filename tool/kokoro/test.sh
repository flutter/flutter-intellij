#!/bin/bash

source ./tool/kokoro/setup.sh
setup

(cd testData/sample_tests; echo "pub get `pwd`"; pub get --no-precompile)

echo "kokoro test start"
./bin/plugin test

echo "kokoro test finished"
