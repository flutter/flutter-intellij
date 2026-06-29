#!/bin/bash
source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

run_gradle_with_retry buildPlugin

echo "kokoro build finished"
