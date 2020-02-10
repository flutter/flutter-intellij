#!/bin/bash

source ./tool/kokoro/setup.sh
setup
(cd testData/sample_tests; echo "pub get `pwd`"; pub get --no-precompile)

echo "kokoro test start"
./bin/plugin test

echo "kokoro test finished"

#TODO(messick) Temporary
echo "kokoro build start"
# echo "KOKORO_KEYSTORE_DIR $KOKORO_KEYSTORE_DIR"
# ls -l $KOKORO_KEYSTORE_DIR/74840_flutter-intellij-plugin-auth-token
# $KOKORO_KEYSTORE_DIR /tmpfs/src/keystore
# -rw-r--r--  1 kbuilder  admin  61 Feb 10 10:45 /tmpfs/src/keystore/74840_flutter-intellij-plugin-auth-token

./bin/plugin build --channel=dev

echo "kokoro build finished"
