# Proposed Actions

When evaluating an issue, check the following conditions and output a JSON array of the applicable tag strings (e.g. `["missing_repro", "missing_flutter_sdk"]`).

## Tags
- `missing_repro`: If the issue does not include clear steps to reproduce the problem.
- `missing_plugin_version`: If the issue does not include the Flutter or Dart IntelliJ plugin version.
- `missing_flutter_sdk`: If the issue is related to Flutter and doesn't specify the Flutter SDK version.
- `missing_dart_sdk`: If the issue is related to Dart and doesn't specify the Dart SDK version.
- `outdated_sdk`: If the author includes an SDK version that is significantly out of date.
- `outdated_plugin`: If the author includes a plugin version that is outdated.
- `duplicate`: If the issue appears to be a duplicate of an existing known issue.
