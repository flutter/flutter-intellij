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

  # Download and configure Flutter to the pinned stable release if not present
  if [ ! -d "../flutter" ]; then
    OS_NAME=$(uname -s | tr '[:upper:]' '[:lower:]')
    FLUTTER_VERSION="3.22.0"
    
    echo "Provisioning Flutter SDK version ${FLUTTER_VERSION} for ${OS_NAME}..."
    if [ "$OS_NAME" = "darwin" ]; then
      curl -O "https://storage.googleapis.com/flutter_infra_release/releases/stable/macos/flutter_macos_${FLUTTER_VERSION}-stable.zip"
      unzip -q "flutter_macos_${FLUTTER_VERSION}-stable.zip" -d ../
      rm "flutter_macos_${FLUTTER_VERSION}-stable.zip"
    else
      curl -O "https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz"
      tar xf "flutter_linux_${FLUTTER_VERSION}-stable.tar.xz" -C ../
      rm "flutter_linux_${FLUTTER_VERSION}-stable.tar.xz"
    fi
  else
    echo "../flutter already exists, skipping download."
  fi
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
