<!--* freshness: { reviewed: '2026-10-25' } *-->
# third_party

## Overview
Third-party libraries and IntelliJ plugin dependencies.

## Subdirectories & Boundaries
- `intellij-plugins-dart`: A single test utility copied from the IntelliJ Dart plugin, [`DartTestUtils.java`](intellij-plugins-dart/testSrc/com/jetbrains/lang/dart/util/DartTestUtils.java). It is registered as a test source only in `flutter-intellij-community.iml`, and `build.gradle.kts` does not reference it. The Gradle unit tests use their own copy, `testSrc/unit/io/flutter/ide/DartTestUtils.java`.
- `lib`: Checked-in jars:
  - [`jxbrowser/jxbrowser-8.9.0.jar`](lib/jxbrowser/jxbrowser-8.9.0.jar), [`jxbrowser/jxbrowser-swing-8.9.0.jar`](lib/jxbrowser/jxbrowser-swing-8.9.0.jar): JxBrowser, an embedded browser compiled against by the plugin (wired in `build.gradle.kts`).
  - [`verifier-cli-1.398-all.jar`](lib/verifier-cli-1.398-all.jar): IntelliJ Plugin Verifier CLI used by `./gradlew verifyPlugin` at build time. It is not bundled into the plugin.
  - [`java-string-similarity-2.0.0.jar`](lib/java-string-similarity-2.0.0.jar) (license: [`java-string-similarity-LICENSE.txt`](lib/java-string-similarity-LICENSE.txt)), [`weberknecht-0.1.5.jar`](lib/weberknecht-0.1.5.jar): Not referenced by `build.gradle.kts`.
