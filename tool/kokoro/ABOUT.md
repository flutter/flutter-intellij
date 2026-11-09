<!--* freshness: { reviewed: '2026-11-06' } *-->
# kokoro

## Overview
Continuous integration and deployment scripts for Kokoro environments.

## Interface
- [`build.sh`](build.sh): Runs `setup`, then `run_gradle_with_retry buildPlugin`.
- [`deploy.sh`](deploy.sh): Builds the dev channel plugin (`buildPlugin -Pdev --no-configuration-cache`) and uploads `build/distributions/Flutter.zip` with `curl` to plugin ID `9212`, `channel=dev`. It does not use the Dart `plugin deploy` command in [`../plugin`](../plugin/README.md).
- [`setup.sh`](setup.sh): Defines `setup`, `setup_flutter`, and these helpers, which the build, deploy, and test scripts source:
  - `setup` prepares Java, exports the keystore variables, and runs `gradlew --version`. It does not provision Flutter or run pub, since `buildPlugin` needs neither.
  - `setup_flutter` provisions the pinned Flutter SDK, runs `flutter config`/`flutter doctor` (bounded), exports `FLUTTER_SDK`, and runs `run_pub_get_with_retry tool/plugin`. Only `test.sh` calls it.
  - `run_gradle_with_retry [--max-retries N] [--delay-secs N] <args>` (defaults: 2 retries, 15 s delay; exit codes >=128 are not retried).
  - `run_pub_get_with_retry [--max-retries N] [--timeout-secs N] [--delay-secs N] <dir>` runs `dart pub get --no-precompile` in `<dir>`, adding `--verbose` on retries (defaults: 1 retry, 300 s per attempt, 15 s delay; a timeout is retried, other exit codes >=128 are not).
  - `run_with_timeout <secs> <command...>` is a portable `timeout`, since macOS lacks coreutils. It kills the command's process tree and returns 124 on timeout.
- [`test.sh`](test.sh): Runs `setup` and `setup_flutter`, then `run_pub_get_with_retry testData/sample_tests` and `run_gradle_with_retry --max-retries 0 test`.

## Invariants
- Scripts must be run from the repository root.
- A valid JetBrains Marketplace token must be available for deployment. It comes from the `JB_MARKETPLACE_TOKEN` env var, or else from the keystore file `$KOKORO_KEYSTORE_DIR/${FLUTTER_KEYSTORE_ID}_${FLUTTER_KEYSTORE_NAME}`.
- Deployment requires the plugin zip file to exist at `build/distributions/Flutter.zip`.
- `run_gradle_with_retry` uses `./gradlew`. If that is not executable it falls back to `./third_party/gradlew`, which does not exist in this repo.

## Side Effects
- Modifies environment variables (`PATH`, `JAVA_HOME`, `JAVA_HOME_OLD`, `JAVA_OPTS`, `FLUTTER_SDK`, etc.).
- Exports the keystore names `FLUTTER_KEYSTORE_ID` (74840), `FLUTTER_KEYSTORE_NAME` and `FLUTTER_KEYSTORE_JXBROWSER_KEY_NAME`, plus `NO_FS_ROOTS_ACCESS_CHECK=true`.
- May download and extract a custom Java JDK if `USE_CUSTOM_JAVA=1`.
- `setup_flutter` (test.sh only) provisions the Flutter SDK by sourcing [`../provision_flutter.sh`](../provision_flutter.sh), runs `flutter config --no-analytics` and `flutter doctor`, and fetches Dart dependencies via `run_pub_get_with_retry tool/plugin`, bounded by a per-attempt timeout.
- Executes Gradle build commands with retries.
- Uploads the built plugin zip to JetBrains Marketplace via `curl`.
