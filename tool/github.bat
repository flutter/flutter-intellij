rem @echo off

set JAVA_HOME=%JAVA_HOME_11_X64%

choco install dart-sdk > choco.log
refreshenv

dart --version

java -version
echo "JAVA_HOME=%JAVA_HOME%"

cd tool\plugin
echo "pub get"
pub get --no-precompile
cd ..\..

echo "run tests"
dart tool\plugin\bin\main.dart test

echo "exit"
