<!--* freshness: { reviewed: '2026-10-25' } *-->
# macos_external

## Overview
Configuration and scripts for the Kokoro `macos_external` CI environment. Used for presubmit/commit build checks (`buildPlugin` only, no tests) and the daily dev-channel release to the JetBrains Marketplace.

## Interface
- [`continuous.cfg`](continuous.cfg), [`presubmit.cfg`](presubmit.cfg): Commit and presubmit jobs. The two files are identical and both run `kokoro_build.sh`.
- [`release.cfg`](release.cfg): Daily dev-channel release job. Runs `kokoro_release.sh`.
- [`kokoro_build.sh`](kokoro_build.sh): Runs [`../../tool/kokoro/build.sh`](../../tool/kokoro/build.sh).
- [`kokoro_release.sh`](kokoro_release.sh): Runs [`../../tool/kokoro/deploy.sh`](../../tool/kokoro/deploy.sh).
- [`kokoro_test.sh`](kokoro_test.sh): Unused. No `.cfg` references it, and its header says it is "currently not invoked".

## Invariants
- Scripts expect to run in a Kokoro environment with KOKORO_ARTIFACTS_DIR defined and source checked out at github/flutter-intellij-kokoro.
- continuous.cfg and presubmit.cfg configure kokoro_build.sh as their build file.
- release.cfg configures kokoro_release.sh as its build file.
- All configurations fetch the flutter-intellij-plugin-jxbrowser-license-key from keystore 74840 before action.
- release.cfg additionally fetches jetbrains-plugin-upload-auth-token from keystore 74840.

## Side Effects
- kokoro_build.sh navigates to the checkout directory and executes ./tool/kokoro/build.sh (a single `buildPlugin` run).
- kokoro_release.sh navigates to the checkout directory and executes ./tool/kokoro/deploy.sh, which builds `build/distributions/Flutter.zip` (`buildPlugin -Pdev`) and uploads it to the JetBrains Marketplace `dev` channel.
- kokoro_test.sh, if invoked, checks KOKORO_JOB_NAME and executes ./tool/kokoro/test.sh for presubmits or ./tool/kokoro/build.sh otherwise.
- release.cfg declares build artifacts under `releases/release_dev/**`. deploy.sh no longer writes to that directory, so this artifact pattern is stale.
