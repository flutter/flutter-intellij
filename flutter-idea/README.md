NEEDS TO BE UPDATED a/o 9 NOV 21

# Gradle Module Structure

This module is a mirror of flutter-intellij (the non-Gradle version) that has sources structured
in a way that the IntelliJ plugin for Gradle expects. A new module was required because the
flutter-studio module has dependencies on a number of classes defined in this module and there was a
circular dependency when those definitions were rooted in flutter-intellij.

The original flutter-intellij structure has been preserved as a courtesy to the engineers that
already have it checked out. The original metadata files are checked in to the repository and will
be used when the project is opened. To use the Gradle structure, import
flutter-intellij/build.gradle instead of opening the project. That will rebuild the metadata
files according to the Gradle structure.

## Building the plugin

Make sure the proper IDE is unpacked in the artifacts directory. Open a terminal and do
`./gradlew clean buildPlugin`

The plugin will be found in build/distributions/flutter-intellij-NN.zip.

## Working with Android Studio sources

If this project is to be used in a full-source configuration with Android Studio (as described in the
flutter-studio module), then some adjustments need to be made after importing the Gradle modules.
Select the flutter-intellij module and select the Paths tab. For each of these projects change
`Compiler output` to `Inherit project compile output path`:
- `flutter-intellij.main`
- `flutter-intellij.test`
- `flutter-intellij.flutter-idea.main`
- `flutter-intellij.flutter-idea.test`
- `flutter-intellij.flutter-studio.main`
- `flutter-intellij.flutter-studio.test`

This needs to be done every time the Gradle module is imported or re-imported.
There doesn't seem to be any config setting in the IntelliJ plugin for Gradle to configure this.
