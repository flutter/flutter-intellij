#!/bin/bash

source ./tool/kokoro/setup.sh
setup

run_pub_get_with_retry testData/sample_tests

echo "kokoro test start"
run_gradle_with_retry --max-retries 0 test

echo "kokoro test finished"
