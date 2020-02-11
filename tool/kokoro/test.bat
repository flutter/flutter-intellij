rem @echo off

dir "c:\Program Files"
dir c:\windows
dir C:\ProgramData
dir "C:\Program Files (x86)"

dart --version || goto :error

REM "%~dp0" is the directory of this file including trailing backslash
SET PATH=%~dp0bin;%PATH%

java -version
echo "JAVA_HOME=%JAVA_HOME%"

echo "install ant"
md ant
cd ant
curl https://www-us.apache.org/dist/ant/binaries/apache-ant-1.10.7-bin.zip > ant.zip
unzip -q ant.zip
cd ..
set PATH=%PATH%;%~dp0ant\apache-ant-1.10.7\bin
rem ant -version

set FLUTTER_KEYSTORE_ID=74840
set FLUTTER_KEYSTORE_NAME=flutter-intellij-plugin-auth-token

cd tool\plugin
rem dir /s/o ..\..\..
echo "pub get"
pub get --no-precompile || goto :error
cd ..\..

dart tool\plugin\bin\main.dart test || goto :error

:; exit 0
exit /b 0

:error
exit /b %errorlevel%