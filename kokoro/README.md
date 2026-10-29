Kokoro Job Config

This is the configuration for the `flutter-intellij-kokoro` job. The
[`macos_external`](macos_external/ABOUT.md) config is used to do CI activities:

- Presubmit and commit ([`presubmit.cfg`](macos_external/presubmit.cfg),
  [`continuous.cfg`](macos_external/continuous.cfg)): run
  [`kokoro_build.sh`](macos_external/kokoro_build.sh), which calls
  [`tool/kokoro/build.sh`](../tool/kokoro/build.sh) to build the plugin once
  with `./gradlew buildPlugin`. No tests run here. Unit tests and plugin
  verification run in the GitHub Actions
  [presubmit workflow](../.github/workflows/presubmit.yaml).
- Daily dev-channel release ([`release.cfg`](macos_external/release.cfg)):
  runs [`kokoro_release.sh`](macos_external/kokoro_release.sh), which calls
  [`tool/kokoro/deploy.sh`](../tool/kokoro/deploy.sh). That builds the plugin
  with `-Pdev` and uploads it to the JetBrains Marketplace `dev` channel
  (fully automated).

[`kokoro_test.sh`](macos_external/kokoro_test.sh) (which calls
[`tool/kokoro/test.sh`](../tool/kokoro/test.sh)) is not referenced by any
`.cfg` and is currently not invoked. The [`gcp_windows`](gcp_windows/ABOUT.md)
directory no longer contains any job config.

Kokoro jobs run on the internal Google infrastructure. See
https://developers.google.com/marketing/engineering/tools/kokoro
for more about it. The jobs fetch secrets from Kokoro keystore 74840: the
JxBrowser license key (all jobs) and the JetBrains Marketplace upload token
(release job only).
