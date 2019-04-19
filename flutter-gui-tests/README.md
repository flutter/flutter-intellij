# Flutter Plugin Integration Testing

On the advice of Jetbrains engineers, we have switched the integration testing module to be
a Gradle-built plugin. This allows the tests to run from anywhere; previously they only ran
when copied into the GUI testing framework module. Thanks to karashevich@jetbrains for
creating the plugin.

## Usage

1. Prepare the flutter-intellij plugin. Run `bin/plugin build` in a terminal. If you do not
want to wait for all the distros to be build, consult product-matrix.json to find which version
sets isTestTarget to true. Then you can use it as the value of the -o option to the build command.
For example: `bin/plugin build -o3.5`

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

If you want to test recent changes be sure to repeat Step 1 in Usage so you are testing the latest build.

## Notes

If the buildPlugin task fails, check for a new version of the Gradle plugin with id org.jetbrains.intellij.
This is likely to be needed if a new version of IntelliJ is downloaded automatically.