<!--* freshness: { reviewed: '2026-08-17' } *-->
# gcp_windows

## Overview
Configuration and scripts for the Kokoro `gcp_windows` CI environment. Note that this environment is currently ignored/deprecated as per the parent Kokoro configuration.

## Interface
- `kokoro_test.bat`
- `presubmit.cfg`

## Invariants
- Build file is defined as flutter-intellij-kokoro/kokoro/gcp_windows/kokoro_test.bat in presubmit configuration.
- Execution relies on the KOKORO_ARTIFACTS_DIR environment variable being set.

## Side Effects
- Changes the current working directory to `%KOKORO_ARTIFACTS_DIR%\github\flutter-intellij-kokoro`.
