## Flutter Plugin for IntelliJ

An IntelliJ plugin for [Flutter](https://flutter.io/) development; for user documentation,
see [flutter.io](https://flutter.io/intellij-ide/).

[![Build Status](https://travis-ci.org/flutter/flutter-intellij.svg)](https://travis-ci.org/flutter/flutter-intellij)

## Getting started

See [flutter.io/setup](https://flutter.io/setup/) for instructions about installing
the Flutter SDK, configuring your machine for Flutter development, and installing
and configuring the IntelliJ plugins.

See [flutter.io/intellij-ide](https://flutter.io/intellij-ide/) for detailed
instructions about developing Flutter apps using IntelliJ.

### Quick-start

A brief summary of the getting started guide linked from above:

- install the Flutter SDK (see [flutter.io/setup](https://flutter.io/setup/))
- run `flutter doctor` from the command line to verify your installation
- ensure you have a supported IntelliJ development environment (IntelliJ 2017.1 or 2017.2, Community or Ultimate)
- open the plugin preferences (Preferences>Plugins on macOS, File>Settings>Plugins on Linux, select "Browse repositories…")
- search for and install the 'Flutter' plugin
- choose the option to restart IntelliJ
- configure the Flutter SDK setting (Preferences on macOS, File>Settings on Linux,
  select Languages & Frameworks>Flutter, and set the path to the root of your flutter repo)

## Filing issues

Please use our [issue tracker](https://github.com/flutter/flutter-intellij/issues)
for Flutter IntelliJ issues.

- for more general Flutter issues, you should prefer to use the Flutter
  [issue tracker](https://github.com/flutter/flutter/issues)
- for more Dart IntelliJ releated issues, you can use JetBrains'
  [YouTrack tracker](https://youtrack.jetbrains.com/issues?q=%23Dart%20%23Unresolved%20).

## Known issues

Please note the following known issues:

- [#1150](https://github.com/flutter/flutter-intellij/issues/1150): the device
  chooser can open Android emulators (and the iOS simulator on MacOS). However,
  in order to locate the Android SDK, the Flutter plugin relies on the `ANDROID_HOME`
  environment variable being set; we're working to relax this requirement.
- We are seeing occasional timeouts when trying to connect to Observatory when
  debugging against the iOS simulator; restarting the simulator should get you
  back on track.
- [#601](https://github.com/flutter/flutter-intellij/issues/601): IntelliJ will
  read the PATH variable just once on startup. Thus, if you change PATH later to
  include the Flutter SDK path, this will not have an affect in IntelliJ until you
  restart the IDE.
