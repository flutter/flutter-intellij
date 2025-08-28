#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

./gradlew buildPlugin

echo "kokoro build finished"
