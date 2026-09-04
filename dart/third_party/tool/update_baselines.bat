@echo off
setlocal enabledelayedexpansion

:: Script to create or update baselines based on current verification reports.
:: Run this from the repository root.

if not exist "third_party" (
    echo Error: This script must be run from the repository root directory.
    exit /b 1
)

echo Running plugin verification...
if exist "third_party\build\reports\pluginVerifier" (
    rd /s /q "third_party\build\reports\pluginVerifier"
)

pushd third_party
call gradlew.bat verifyPlugin
popd

set "versions=253 261 262"

for %%v in (%versions%) do (
    echo Processing baseline for %%v...
    set "BASELINE=third_party\tool\baseline\%%v\verifier-baseline.txt"
    set "REPORT="
    
    :: Find the first report.md in a directory matching the version using PowerShell for robustness
    for /f "usebackq delims=" %%f in (`powershell -NoProfile -Command "Get-ChildItem -Path 'third_party\build\reports\pluginVerifier' -Filter 'report.md' -Recurse | Where-Object { $_.FullName -match '-%%v\.' } | Select-Object -ExpandProperty FullName -First 1"`) do (
        set "REPORT=%%f"
    )
    
    if defined REPORT (
        echo Extracting issues from !REPORT!
        if not exist "third_party\tool\baseline\%%v" mkdir "third_party\tool\baseline\%%v"
        
        :: Extract lines starting with * and sort them (case-sensitive)
        powershell -NoProfile -Command "Get-Content -LiteralPath '!REPORT!' | Where-Object { $_ -match '^\*' -and $_ -notmatch 'com\.intellij\.platform\.dartlsp' } | Sort-Object -CaseSensitive | Set-Content -LiteralPath '!BASELINE!'"
        
        echo Updated baseline at !BASELINE!
    ) else (
        echo Warning: Report does not exist for version %%v. Skipping.
    )
)

echo Done updating baselines.
