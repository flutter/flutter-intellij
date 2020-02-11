cd %KOKORO_ARTIFACTS_DIR%\github\flutter-intellij-kokoro
curl https://github.com/PowerShell/PowerShell/releases/download/v6.2.4/PowerShell-6.2.4-win-x64.msi > PowerShell-6.2.4-win-x64.msi
msiexec.exe /package PowerShell-6.2.4-win-x64.msi /quiet ADD_EXPLORER_CONTEXT_MENU_OPENPOWERSHELL=1 ENABLE_PSREMOTING=1 REGISTER_MANIFEST=1
powershell.exe -File tool\kokoro\test.ps1
