#!/bin/bash

source ./tool/kokoro/setup.sh
setup

curl https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_osx-x64_bin.tar.gz > ../java.tar.gz
(cd ..; tar fx java.tar.gz)
export JAVA_HOME=`pwd`/../jdk-11.0.2.jdk/Contents/Home
export PATH=$PATH:$JAVA_HOME/bin
echo "JAVA_HOME=$JAVA_HOME"

(cd flutter-idea/testData/sample_tests; echo "dart pub get `pwd`"; dart pub get --no-precompile)

echo "kokoro test start"
./bin/plugin test

echo "kokoro test finished"
