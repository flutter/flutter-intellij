## Flutter Plugin for IntelliJ

An IntelliJ plugin for [Flutter](https://flutter.io/) development. For documentation, see [flutter.io](https://flutter.io/intellij-ide/).

[![Build Status](https://travis-ci.org/flutter/flutter-intellij.svg)](https://travis-ci.org/flutter/flutter-intellij)

## Getting started

See [flutter.io/setup](https://flutter.io/setup/) for instructions about installing the Flutter SDK, configuring your machine for Flutter development, and installing and configuring the IntelliJ plugins.

## Quick-start

A very brief summary of the getting started guide linked from above:

- install the Flutter SDK (see [flutter.io/setup](https://flutter.io/setup/)); run `flutter doctor` from the command line to verify your installation
- ensure you have a supported IntelliJ development environment (IntelliJ 2016.2+, Ultimate or Community)
- open the plugin preferences (Preferences>Plugins on macOS, File>Settings>Plugins on Linux, select "Browse repositories…")
- search for and install the 'Dart' plugin; search for and install the 'Flutter' plugin
- choose the option to restart IntelliJ
- configure the Flutter SDK setting (Preferences on macOS, File>Settings on Linux, select Languages & Frameworks>Flutter, and set the path to the root of your flutter repo)

## Filing issues

Please use our [issue tracker](https://github.com/flutter/flutter-intellij/issues)
for Flutter IntelliJ issues. For more general Flutter issues, you should prefer to use
the Flutter [issue tracker](https://github.com/flutter/flutter/issues). For more
Dart IntelliJ releated issues, you can use JetBrains'
[YouTrack tracker](https://youtrack.jetbrains.com/issues?q=%23Dart%20%23Unresolved%20).

## Known issues

Please note the following known issues:

* The 2017.1 release of the Dart plugin has added the ability to have Dart SDKs configured on a
  per-project basis (previously an SDK had been configured globally). This is great for the Flutter
  plugin as users can now use separate Dart SDKs for Flutter and Dart for web projects. However,
  when opening a project in 2017.1 created with older IntelliJs, you may encounter an issue where
  no Dart SDK is configured for your project. Take these steps to resolve this issue:
   * Open the page `Settings > Languages & Frameworks > Dart`
   * Check the option to `Enable Dart support for the project`
   * As the `Dart SDK path`, select the Dart SDK located inside the Flutter SDK (`<flutter sdk path>/bin/cache/dart-sdk`).
   * Check the project and all modules in the `Enable Dart support for the following modules`-list at the bottom
   * Click `OK`

* [#601](https://github.com/flutter/flutter-intellij/issues/601): IntelliJ will read the PATH
  variable just once on startup. Thus, if you change PATH later to include the Flutter SDK path,
  this will not have an affect in IntelliJ until you restart the IDE.
