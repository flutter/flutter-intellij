rem @echo off

set JAVA_HOME=%JAVA_HOME_11_X64%
set DART_VERSION=2.7.1

echo "install dart"
rem Make this adaptive to the possibility of a file or dir named dart, someday.
md dart
cd dart
curl https://storage.googleapis.com/dart-archive/channels/stable/release/%DART_VERSION%/sdk/dartsdk-windows-ia32-release.zip > dart.zip
unzip -q dart.zip
cd ..
set PATH=.\dart\dart-sdk\bin;%PATH%

dart --version || goto :error

java -version
echo "JAVA_HOME=%JAVA_HOME%"

cd tool\plugin
echo "pub get"
..\..\dart\dart-sdk\bin\pub get --no-precompile
cd ..\..

echo "run tests"
dart tool\plugin\bin\main.dart test
