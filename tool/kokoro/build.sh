#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

./bin/plugin make --channel=dev -o4.0 -u -m1
./bin/plugin make --channel=dev -o4.1 -u -m2

curl https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_osx-x64_bin.tar.gz > ../java.tar.gz
(cd ..; tar fx java.tar.gz)
export JAVA_HOME=`pwd`/../jdk-11.0.2.jdk/Contents/Home
export PATH=$PATH:$JAVA_HOME/bin
echo "JAVA_HOME=$JAVA_HOME"

./bin/plugin make --channel=dev -o4.2 -u -m3
./bin/plugin make --channel=dev -o2020.3 -u -m4

echo "kokoro build finished"
