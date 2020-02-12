cd %KOKORO_ARTIFACTS_DIR%\github\flutter-intellij-kokoro

rem Use choco to install dart so we get the latest version without having to edit this script
rem Can't find where choco puts dart-sdk
choco -y install dart-sdk
rem call RefreshEnv.cmd

dir /s/o t:\ > dir.log
findstr dart-sdk dir.log

call tool\kokoro\test.bat
