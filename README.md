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

## Known issues

Please note the following known issues:

* [#565](https://github.com/flutter/flutter-intellij/issues/565): IntelliJ/WebStorm has a single setting containing the path to the Dart SDK. This can be an issue if you work on both Flutter projects, and [AngularDart projects](https://webdev.dartlang.org/) as they tend to use different Dart SDK versions. In that case, after using Flutter you will have to manually reconfigure IntelliJ/WebStorm back to the correct Dart SDK path:

   - Open an existing Dart project / create a new one
   - Go to Preferences > Languages & Frameworks > Dart
   - Enter the Dart SDK path (i.e., where your main Dart SDK is located, not the one bundled with Flutter)

* [#601](https://github.com/flutter/flutter-intellij/issues/601): IntelliJ will read the PATH variable just once on startup. Thus, if you change PATH later to include the Flutter SDK path, this will not have an affect in IntelliJ until you restart the IDE.
