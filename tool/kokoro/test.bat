REM Copied from setup.sh
REM TODO(messick) Translate to CMD

REM  git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
REM  export PATH="$PATH":`pwd`/../flutter/bin:`pwd`/../flutter/bin/cache/dart-sdk/bin
REM  flutter config --no-analytics
REM  flutter doctor
REM  export FLUTTER_SDK=`pwd`/../flutter

REM  java -version
REM  echo "JAVA_HOME=$JAVA_HOME"

REM  echo "install ant"
REM  curl https://www-us.apache.org/dist/ant/binaries/apache-ant-1.10.7-bin.tar.gz > ../ant.tar.gz
REM  (cd ..; tar fx ant.tar.gz)
REM  export PATH=$PATH:`pwd`/../apache-ant-1.10.7/bin

REM  export FLUTTER_KEYSTORE_ID=74840
REM  export FLUTTER_KEYSTORE_NAME=flutter-intellij-plugin-auth-token

REM  (cd tool/plugin; echo "pub get `pwd`"; pub get --no-precompile)

REM ./bin/plugin test

java -version
ant -version
curl --version
git --version
