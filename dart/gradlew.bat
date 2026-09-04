@echo off
:: NOTE: Any file paths passed as arguments should be relative to the 'third_party' 
:: directory (or fully qualified).

call "%~dp0third_party\gradlew.bat" --project-dir "%~dp0third_party" %*