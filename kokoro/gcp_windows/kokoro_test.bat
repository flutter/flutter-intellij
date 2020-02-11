cd %KOKORO_ARTIFACTS_DIR%\github\flutter-intellij-kokoro

rem Use choco to install dart so we get the latest version without having to edit this script
choco install -help
choco -d --verbose --trace -y --log-file=%~dp0choco.log install dart-sdk
type  %~dp0choco.log
call RefreshEnv.cmd

rem Run the test script in a new shell so it gets the updated environment and path.
cmd.exe /c "call tool\kokoro\test.bat"
