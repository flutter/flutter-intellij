#!/bin/bash

# Initialize everything required by the plugin tool.
setup() {
  # Fail on any error.
  set -e

  # Display commands being run. Only do this while debugging and be careful
  # that no confidential information is displayed.
  # set -x

  # If we move to branch-based builds we might not be able to use such a shallow clone.
  git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
  export PATH="$PATH":`pwd`/../flutter/bin:`pwd`/../flutter/bin/cache/dart-sdk/bin
  flutter config --no-analytics
  flutter doctor
  export FLUTTER_SDK=`pwd`/../flutter

  export JAVA_HOME_OLD=$JAVA_HOME
  curl https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_osx-x64_bin.tar.gz > ../java.tar.gz
  (cd ..; tar fx java.tar.gz)
  export JAVA_HOME=`pwd`/../jdk-11.0.2.jdk/Contents/Home
  export PATH=$PATH:$JAVA_HOME/bin
  export JAVA_OPTS=" -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true"
  echo "JAVA_HOME=$JAVA_HOME"
  java -version

  export FLUTTER_KEYSTORE_ID=74840
  export FLUTTER_KEYSTORE_NAME=flutter-intellij-plugin-auth-token
  export FLUTTER_KEYSTORE_JXBROWSER_KEY_NAME=flutter-intellij-plugin-jxbrowser-license-key
  export NO_FS_ROOTS_ACCESS_CHECK=true

  (cd tool/plugin; echo "dart pub get `pwd`"; dart pub get --no-precompile)
  ./third_party/gradlew --version
}
