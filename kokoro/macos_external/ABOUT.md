<!--* freshness: { reviewed: '2026-08-17' } *-->
# macos_external

## Overview
Configuration and scripts for the Kokoro `macos_external` CI environment. Used for CI activities such as unit tests on presubmit and commit, compilation checks, and daily dev-channel builds.

## Interface
- (No public code symbols exported)

## Invariants
- Scripts expect to run in a Kokoro environment with KOKORO_ARTIFACTS_DIR defined and source checked out at github/flutter-intellij-kokoro.
- continuous.cfg and presubmit.cfg configure kokoro_build.sh as their build file.
- release.cfg configures kokoro_release.sh as its build file.
- All configurations fetch the flutter-intellij-plugin-jxbrowser-license-key from keystore 74840 before action.
- release.cfg additionally fetches jetbrains-plugin-upload-auth-token from keystore 74840.

## Side Effects
- kokoro_build.sh navigates to the checkout directory and executes ./tool/kokoro/build.sh.
- kokoro_release.sh navigates to the checkout directory and executes ./tool/kokoro/deploy.sh.
- kokoro_test.sh checks KOKORO_JOB_NAME and executes ./tool/kokoro/test.sh for presubmits or ./tool/kokoro/build.sh otherwise.
- release.cfg defines build artifacts from the releases/release_dev directory.
