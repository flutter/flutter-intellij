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

* [#316](https://github.com/flutter/flutter-intellij/issues/316): Opening existing flutter projects with the plugin can lead to confusing messages if you do not install the 'Flutter' plugin first (see [setup](https://flutter.io/setup/#flutter-intellij-ide-plugins)).
