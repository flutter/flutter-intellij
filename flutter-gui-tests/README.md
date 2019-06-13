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

On a Mac, you must grant permission to control your computer to the JVM. Open System Preferences then select
Security & Privacy. Unlock it and click the + button. Navigate to the JVM used to run the integration tests
and add it to the list. One way to find the JVM is to run the tests, let it hang, and search the output of
`ps xa` for LATEST-EAP. If that points to a JVM in a .gradle directory (which the Mac will not allow you to
navigate to) you can hard link to it in some random directory then add that file.

If you get errors from Gradle sync failing try using Java 11 instead of Java 8 in the project that manages
the test plugin. For example, create an IntelliJ platform plugin SDK in Project Structure and point its base
SDK to a Java 11 installation. Then set that as the default SDK for the project.