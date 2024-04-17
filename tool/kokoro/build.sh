#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

./bin/plugin verify

./bin/plugin make --channel=dev

echo "kokoro build finished"
