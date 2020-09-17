#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"
./bin/plugin make --channel=dev -o4.0 -u

set +e
pwd
echo "List releases"
ls -lR releases
echo "List build"
ls -lR build
set -e

echo "kokoro build finished"

echo "kokoro deploy start"
#./bin/plugin deploy --channel=dev

echo "kokoro deploy finished"
