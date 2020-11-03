@echo on
echo "JAVA_HOME=%JAVA_HOME%"

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
dart tool\plugin\bin\main.dart test

echo "exit"
