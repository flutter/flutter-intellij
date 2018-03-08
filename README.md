# <img src="https://flutter.io/images/flutter-mark-square-100.png" alt="Flutter" width="26" height="26" /> Flutter Plugin for IntelliJ

[![Join Gitter Chat Channel -](https://badges.gitter.im/flutter/flutter.svg)](https://gitter.im/flutter/flutter?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/flutter/flutter-intellij.svg)](https://travis-ci.org/flutter/flutter-intellij)

An IntelliJ plugin for [Flutter](https://flutter.io/) development. Flutter is a new mobile
app SDK to help developers and designers build modern mobile apps for iOS and Android.

_Android Studio and M18.3:_ Using version 18.3 of the plugin with Android Studio? See this
[known issue](https://github.com/flutter/flutter-intellij#known-issues) for help upgrading from that
version.

## Documentation

- [flutter.io](https://flutter.io)
- [Installing Flutter](https://flutter.io/setup/)
- [Getting Started with IntelliJ](https://flutter.io/intellij-ide/)

## Fast development

Flutter's <em>hot reload</em> helps you quickly and easily experiment, build UIs, add features,
and fix bugs faster. Experience sub-second reload times, without losing state, on emulators,
simulators, and hardware for iOS and Android.

<img src="https://user-images.githubusercontent.com/919717/28131204-0f8c3cda-66ee-11e7-9428-6a0513eac75d.gif" alt="Make a change in your code, and your app is changed instantly.">

## Quick-start

A brief summary of the [getting started guide](https://flutter.io/intellij-ide/):

- install the [Flutter SDK](https://flutter.io/setup/)
- run `flutter doctor` from the command line to verify your installation
- ensure you have a supported [IntelliJ development environment](https://www.jetbrains.com/idea/download), either:
  - IntelliJ 2017.3 or 2018.1, Community or Ultimate Edition, or
  - Android Studio 3.0 (note: Android Studio 3.1 Beta is currently _not_ supported)
- open the plugin preferences
  - `Preferences > Plugins` on macOS, `File > Settings > Plugins` on Linux, select "Browse repositories…"
- search for and install the 'Flutter' plugin
- choose the option to restart IntelliJ
- configure the Flutter SDK setting
  - `Preferences` on macOS, `File>Settings` on Linux, select `Languages & Frameworks > Flutter`, and set
    the path to the root of your flutter repo

## Filing issues

Please use our [issue tracker](https://github.com/flutter/flutter-intellij/issues)
for Flutter IntelliJ issues.

- for more general Flutter issues, you should prefer to use the Flutter
  [issue tracker](https://github.com/flutter/flutter/issues)
- for more Dart IntelliJ related issues, you can use JetBrains'
  [YouTrack tracker](https://youtrack.jetbrains.com/issues?q=%23Dart%20%23Unresolved%20)

## Known issues

Please note the following known issues:

- If you are using version 18.3 of the Flutter plugin with Android Studio, it will not
  upgrade to newer versions of the plugin. In order to upgrade, you'll need to uninstall
  the plugin and re-install it (this can be done from the plugins preference page).
- [#601](https://github.com/flutter/flutter-intellij/issues/601): IntelliJ will
  read the PATH variable just once on startup. Thus, if you change PATH later to
  include the Flutter SDK path, this will not have an affect in IntelliJ until you
  restart the IDE.
- We are seeing occasional timeouts when trying to connect to Observatory when
  debugging against the iOS simulator; restarting the simulator should get you
  back on track.
