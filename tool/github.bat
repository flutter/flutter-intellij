@echo on

echo "install dart"
choco install dart-sdk > choco.log
call RefreshEnv.cmd

echo "dart version"
dart --version

cd tool\plugin
echo "pub get"
call pub get --no-precompile
cd ..\..

echo "run tests"
set JAVA_HOME=%JAVA_HOME_11_X64%
echo "JAVA_HOME=%JAVA_HOME%"
dart tool\plugin\bin\main.dart test

echo "exit"
