git clone --depth 1 https://github.com/flutter/flutter.git ../flutter
set PATH=%PATH%;..\flutter\bin;..\flutter\bin\cache\dart-sdk\bin
flutter config --no-analytics || goto :error
flutter doctor
set FLUTTER_SDK=../flutter

java -version
echo "JAVA_HOME=%JAVA_HOME%"

echo "install ant"
curl https://www-us.apache.org/dist/ant/binaries/apache-ant-1.10.7-bin.zip > ../ant.zip
(cd ..; unzip ant.tar.gz)
set PATH=%PATH%;../apache-ant-1.10.7/bin
ant --version || goto :error

set FLUTTER_KEYSTORE_ID=74840
set FLUTTER_KEYSTORE_NAME=flutter-intellij-plugin-auth-token

(cd tool/plugin; echo "pub get `pwd`"; pub get --no-precompile)

.\bin\plugin test || goto :error

:; exit 0
exit /b 0

:error
exit /b %errorlevel%