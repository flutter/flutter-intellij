Kokoro Job Config

This is the configuration for the `flutter-intellij-kokoro` job. Currently, the
`gcp_windows` config is ignored; when this project was under active development
the Windows support was not adequate. The `macos_external` config is used
to do CI activities. It runs unit tests on presubmit and commit, checks the code
for compilation problems in all supported IDEs, and performs a weekly dev-channel
build (fully automated).

Kokoro jobs run on the internal Google infrastructure. See
https://developers.google.com/marketing/engineering/tools/kokoro
for more about it. It has no special permissions.

It is maintained by the Dart DevTools team. Currently, the primary contacts
are stevemessick and helin24 (GitHub ids).