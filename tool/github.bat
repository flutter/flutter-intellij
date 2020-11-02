rem @echo off

set JAVA_HOME=%JAVA_HOME_11_X64%

echo "install dart"
choco install dart-sdk

goto :next
rem Make this adaptive to the possibility of a file or dir named dart, someday.
md dart
cd dart
set DART_VERSION=2.7.1
curl https://storage.googleapis.com/dart-archive/channels/stable/release/%DART_VERSION%/sdk/dartsdk-windows-ia32-release.zip > dart.zip
unzip -q dart.zip
cd ..
set PATH=.\dart\dart-sdk\bin;%PATH%
:next

dart --version || goto :error

java -version
echo "JAVA_HOME=%JAVA_HOME%"

cd tool\plugin
echo "pub get"
pub get --no-precompile
cd ..\..

echo "run tests"
dart tool\plugin\bin\main.dart test

echo "exit"
