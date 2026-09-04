#!/bin/bash

source ./tool/kokoro/setup.sh
setup

(cd testData/sample_tests; echo "dart pub get `pwd`"; dart pub get --no-precompile)

echo "kokoro test start"
run_gradle_with_retry --max-retries 0 test

echo "kokoro test finished"
