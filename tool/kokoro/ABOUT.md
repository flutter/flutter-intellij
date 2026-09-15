<!--* freshness: { reviewed: '2026-08-31' } *-->
# kokoro

## Overview
Continuous integration and deployment scripts for Kokoro environments.

## Interface
- `build.sh`
- `deploy.sh`
- `setup.sh` (defines the `run_gradle_with_retry` helper sourced by build, deploy, and test scripts)
- `test.bat`
- `test.sh`

## Invariants
- Scripts must be run from the repository root.
- A valid JetBrains Marketplace token must be available for deployment (via `JB_MARKETPLACE_TOKEN` env var or keystore file).
- Deployment requires the plugin zip file to exist at `build/distributions/Flutter.zip`.

## Side Effects
- Modifies environment variables (`PATH`, `JAVA_HOME`, `JAVA_HOME_OLD`, `JAVA_OPTS`, `FLUTTER_SDK`, etc.).
- May download and extract a custom Java JDK if `USE_CUSTOM_JAVA=1`.
- Provisions the Flutter SDK and modifies Flutter configuration.
- Fetches Dart dependencies via `dart pub get` in `tool/plugin`.
- Executes Gradle build commands with retries.
- Uploads the built plugin zip to JetBrains Marketplace via `curl`.
