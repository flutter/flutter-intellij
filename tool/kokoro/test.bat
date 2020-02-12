rem @echo off

rem dir t:\tmp
rem dir T:\src\github\flutter-intellij-kokoro
rem dir c:\users\root
rem dir t:\
rem dir t:\src
rem dir T:\src\github
rem dir "c:\Program Files"
rem dir "c:\Windows\Downloaded Program Files"
rem dir c:\Users
rem dir c:\windows
rem dir C:\ProgramData
rem dir "C:\Program Files (x86)"
rem dir C:\ProgramData\chocolatey\bin

echo "install dart"
md ..\dart
cd ..\dart
curl https://storage.googleapis.com/dart-archive/channels/stable/release/2.7.1/sdk/dartsdk-windows-ia32-release.zip > dart.zip
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
curl https://www-us.apache.org/dist/ant/binaries/apache-ant-1.10.7-bin.zip > ant.zip
unzip -q ant.zip
cd %~dp0..\..
set PATH=%PATH%;%~dp0..\..\..ant\apache-ant-1.10.7\bin
rem ant -version

set FLUTTER_KEYSTORE_ID=74840
set FLUTTER_KEYSTORE_NAME=flutter-intellij-plugin-auth-token

cd tool\plugin
rem dir /s/o ..\..\..
echo "pub get"
pub get --no-precompile
cd ..\..

dart tool\plugin\bin\main.dart test || goto :error

:; exit 0
exit /b 0

:error
exit /b %errorlevel%