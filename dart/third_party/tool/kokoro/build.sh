#!/bin/bash

source ./third_party/tool/kokoro/setup.sh
setup

echo "kokoro build start"

cd third_party

run_gradle_with_retry buildPlugin

echo "kokoro build finished"
