rem @echo off

set DART_VERSION=2.7.1
set ANT_VERSION=1.10.7

echo "install dart"
md ..\dart
cd ..\dart
curl https://storage.googleapis.com/dart-archive/channels/stable/release/%DART_VERSION%/sdk/dartsdk-windows-ia32-release.zip > dart.zip
unzip -q dart.zip
REM "%~dp0" is the directory of this file including trailing backslash
cd %~dp0..\..
set PATH=%PATH%;%~dp0..\..\..\dart\dart-sdk\bin

dart --version || goto :error

java -version
echo "JAVA_HOME=%JAVA_HOME%"

echo "install ant"
md ..\ant
cd ..\ant
curl https://www-us.apache.org/dist/ant/binaries/apache-ant-%ANT_VERSION%-bin.zip > ant.zip
unzip -q ant.zip
cd %~dp0..\..
set PATH=%PATH%;%~dp0..\..\..ant\apache-ant-1.10.7\bin
rem ant -version

set FLUTTER_KEYSTORE_ID=74840
set FLUTTER_KEYSTORE_NAME=flutter-intellij-plugin-auth-token

cd tool\plugin
rem dir /s/o ..\..\..
echo "dart pub get"
cmd /c "dart pub get --no-precompile"
cd ..\..

dart tool\plugin\bin\main.dart test > test.log || goto :error
type test.log

:; exit 0
exit /b 0

:error
exit /b %errorlevel%