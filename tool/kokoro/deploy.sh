#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"
./bin/plugin build --channel=dev

echo "kokoro build finished"

echo "kokoro deploy start"
./bin/plugin deploy --channel=dev

echo "kokoro deploy finished"
