rem @echo off
git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
set PATH=%PATH%;..\flutter\bin\cache\dart-sdk\bin

java -version
echo "JAVA_HOME=%JAVA_HOME%"

echo "install ant"
md ant
cd ant
curl https://www-us.apache.org/dist/ant/binaries/apache-ant-1.10.7-bin.zip > ant.zip
unzip -q ant.zip
cd ..
set PATH=%PATH%;ant\apache-ant-1.10.7\bin
rem ant -version

set FLUTTER_KEYSTORE_ID=74840
set FLUTTER_KEYSTORE_NAME=flutter-intellij-plugin-auth-token

cd tool\plugin
dir /s/o ..\..\..
echo "pub get"
..\..\..\flutter\bin\cache\dart-sdk\bin\pub get --no-precompile || goto :error
cd ..\..

dart tool\plugin\bin\main.dart test || goto :error

:; exit 0
exit /b 0

:error
exit /b %errorlevel%