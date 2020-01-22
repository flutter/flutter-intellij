#!/bin/bash

# Fail on any error.
#set -e

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

sudo apt-get update
sudo apt-get install ant

ant -version
curl --version
zip --version

./bin/plugin build --channel=dev

echo "kokoro build finished"
