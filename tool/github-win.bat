rem @echo off

set DART_VERSION=2.7.1

echo "install dart"
rem Make this adaptive to the possibility of a file or dir named dart, someday.
md dart
cd dart
curl https://storage.googleapis.com/dart-archive/channels/stable/release/%DART_VERSION%/sdk/dartsdk-windows-ia32-release.zip > dart.zip
unzip -q dart.zip
cd ..
set PATH=.\dart\dart-sdk\bin

dart --version || goto :error

java -version
echo "JAVA_HOME=%JAVA_HOME%"

cd tool\plugin
echo "pub get"
cmd /c "pub get --no-precompile"
cd ..\..

dart tool\plugin\bin\main.dart test > test.log || goto :error
type test.log

:; exit 0
exit /b 0

:error
exit /b %errorlevel%
