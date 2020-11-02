@echo on

set JAVA_HOME=%JAVA_HOME_11_X64%

echo "install dart"
choco install dart-sdk > choco.log
call RefreshEnv.cmd

echo "dart version"
dart --version

echo "java version"
java -version
echo "JAVA_HOME=%JAVA_HOME%"

cd tool\plugin
echo "pub get"
call pub get --no-precompile
cd ..\..

echo "run tests"
dart tool\plugin\bin\main.dart test

echo "exit"
