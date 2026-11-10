#!/bin/bash

source ./tool/kokoro/setup.sh
setup
setup_flutter

echo "kokoro test start"
run_gradle_with_retry --max-retries 0 test

echo "kokoro test finished"
