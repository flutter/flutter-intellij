# Flutter Plugin Integration Testing

On the advice of Jetbrains engineers, we have switched the integration testing module to be
a Gradle-built plugin. This allows the tests to run from anywhere; previously they only ran
when copied into the GUI testing framework module. Thanks to karashevich@jetbrains for
creating the plugin.

## Usage

1. Prepare the flutter-intellij plugin. Main menu: Build -> Prepare All Plugin Modules for Deployment.
Ensure that `flutter-intellij.zip` appears under the root of the project.

2. Check that the buildPlugin task works normally. Open the Gradle
tool view: View -> Tool Windows -> Gradle. Expamd `Tasks`, then expand `intellij`.
Select `buildPliugin`, right-click and choose `Run ...`. This may take a while initially
as it may have to download some files.

3. For now, we use the built-in terminal to run tests. Open the terminal emulator in IntelliJ, then:
```bash
cd flutter-gui-tests
./gradlew -Dtest.single=TestSuite clean test
```

## Editing

Currently, the tests need to be edited in a minimal flutter-intellij project. Open the flutter-intellij
project in IntelliJ 2019.1 for stand-alone editing, without Dart or IntelliJ sources (see CONTRIBUTING.md).
