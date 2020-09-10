#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"
./bin/plugin make --channel=dev

echo "kokoro build finished"
