#!/bin/bash

source ./tool/kokoro/setup.sh
setup

curl https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_osx-x64_bin.tar.gz > ../java.tar.gz
(cd ..; tar fx java.tar.gz)
export JAVA_HOME_OLD=$JAVA_HOME
export JAVA_HOME=`pwd`/../jdk-11.0.2.jdk/Contents/Home

echo "kokoro build start"

./bin/plugin make --channel=dev

echo "kokoro build finished"

echo "kokoro deploy start"
./bin/plugin deploy --channel=dev

echo "kokoro deploy finished"
