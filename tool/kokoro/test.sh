#!/bin/bash

# Fail on any error.
set -e

# Display commands being run. Only do this while debugging and be careful
# that no confidential information is displayed.
# set -x

git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
export PATH="$PATH":`pwd`/../flutter/bin:`pwd`/../flutter/bin/cache/dart-sdk/bin
flutter config --no-analytics
flutter doctor
export FLUTTER_SDK=`pwd`/../flutter

java -version
echo "JAVA_HOME=$JAVA_HOME"

echo "install ant"
curl https://www-us.apache.org/dist//ant/binaries/apache-ant-1.10.7-bin.tar.gz > ../ant.tar.gz
(cd ..; tar fx ant.tar.gz)
export PATH=$PATH:`pwd`/../apache-ant-1.10.7/bin

echo "pub get `pwd`"; pub get --no-precompile
(cd testData/sample_tests; echo "pub get `pwd`"; pub get --no-precompile)
(cd tool/plugin; echo "pub get `pwd`"; pub get --no-precompile)

echo "kokoro test start"
./bin/plugin test

echo "kokoro test finished"


echo "kokoro build start"
./bin/plugin build --channel=dev

echo "kokoro build finished"
