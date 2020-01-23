#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"
./bin/plugin build --channel=dev

# TODO(messick) Save build artifacts.

echo "kokoro build finished"
