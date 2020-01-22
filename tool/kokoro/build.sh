#!/bin/bash

source ./setup.sh
setup

echo "kokoro build start"
./bin/plugin build --channel=dev

echo "kokoro build finished"
