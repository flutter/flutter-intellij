#!/bin/bash

# Initialize everything required by the plugin tool.
setup() {
  # Fail on any error.
  set -e

  # Display commands being run. Only do this while debugging and be careful
  # that no confidential information is displayed.
  # set -x

  # Set to 0 to use the system java, 1 to download/install a different version of java
  export USE_CUSTOM_JAVA=0

  echo "System Java version:"
  java --version
  # JAVA_HOME_OLD is used by runner.dart
  export JAVA_HOME_OLD=$JAVA_HOME
  echo "export JAVA_HOME_OLD=$JAVA_HOME"

  if [ "$USE_CUSTOM_JAVA" = 1 ] ; then
    echo "curl https://download.oracle.com/java/17/archive/jdk-17.0.4.1_macos-x64_bin.tar.gz > ../java.tar.gz"
    curl https://download.oracle.com/java/17/archive/jdk-17.0.4.1_macos-x64_bin.tar.gz > ../java.tar.gz
    (cd ..; tar fx java.tar.gz)
    echo "Custom java version:"
    java --version

    export JAVA_HOME=`pwd`/../jdk-17.0.4.1.jdk/Contents/Home
    echo "JAVA_HOME=$JAVA_HOME"
    export PATH=$PATH:$JAVA_HOME/bin
  fi

  export JAVA_OPTS=" -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true"

  # Clone and configure Flutter to the latest stable release
  git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
  export PATH="$PATH":`pwd`/../flutter/bin:`pwd`/../flutter/bin/cache/dart-sdk/bin
  flutter config --no-analytics
  flutter doctor

  export FLUTTER_SDK=`pwd`/../flutter
  export FLUTTER_KEYSTORE_ID=74840
  export FLUTTER_KEYSTORE_NAME=jetbrains-plugin-upload-auth-token
  export FLUTTER_KEYSTORE_JXBROWSER_KEY_NAME=flutter-intellij-plugin-jxbrowser-license-key
  export NO_FS_ROOTS_ACCESS_CHECK=true

  (cd tool/plugin; echo "dart pub get `pwd`"; dart pub get --no-precompile)
  ./gradlew --version
}
