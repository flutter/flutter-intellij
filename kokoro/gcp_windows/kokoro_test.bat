cd %KOKORO_ARTIFACTS_DIR%\github\flutter-intellij-kokoro

rem Use choco to install dart so we get the latest version without having to edit this script
rem Can't find where choco puts dart-sdk
rem choco -y install dart-sdk
rem call RefreshEnv.cmd

rem Tests don't run on Windows 7
rem call tool\kokoro\test.bat
