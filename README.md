<p align="center">
  <img src="https://flutter.io/images/flutter-mark-square-100.png" alt="Flutter"/>
</p>

<p align="center">
  An IntelliJ plugin for <a href="https://flutter.io/">Flutter</a> development.
</p>

<p align="center">
  <a href="https://gitter.im/flutter/flutter?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge"><img src="https://badges.gitter.im/flutter/flutter.svg" alt="Join Gitter Chat Channel"></a>
  <a href="https://travis-ci.org/flutter/flutter-intellij"><img src="https://travis-ci.org/flutter/flutter-intellij.svg" alt="Build Status"</a>
</p>

# Flutter Plugin for IntelliJ

- [Documentation](#documentation)
- [Fast development](#fast-development)
- [Quick-start](#quick-start)
- [Filing issues](#filing-issues), and
- [Known issues](#known-issues)

## Documentation

- [flutter.io](https://flutter.io)
- [Installing Flutter](https://flutter.io/setup/)
- [Getting started with IntelliJ](https://flutter.io/intellij-ide/)

## Fast development

Flutter's <em>hot reload</em> helps you quickly and easily experiment, build UIs, add features,
and fix bugs faster. Experience sub-second reload times, without losing state, on emulators,
simulators, and hardware for iOS and Android.

<img src="https://user-images.githubusercontent.com/919717/28131204-0f8c3cda-66ee-11e7-9428-6a0513eac75d.gif" alt="Make a change in your code, and your app is changed instantly.">

## Quick-start

A brief summary of the [getting started guide](https://flutter.io/intellij-ide/):

- install the [Flutter SDK](https://flutter.io/setup/)
- run `flutter doctor` from the command line to verify your installation
- ensure you have a supported [IntelliJ development environment](https://www.jetbrains.com/idea/download)
  (IntelliJ 2017.1 or 2017.2, Community or Ultimate Edition)
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
- for more Dart IntelliJ releated issues, you can use JetBrains'
  [YouTrack tracker](https://youtrack.jetbrains.com/issues?q=%23Dart%20%23Unresolved%20)

## Known issues

Please note the following known issues:

- [#601](https://github.com/flutter/flutter-intellij/issues/601): IntelliJ will
  read the PATH variable just once on startup. Thus, if you change PATH later to
  include the Flutter SDK path, this will not have an affect in IntelliJ until you
  restart the IDE.
- [#1150](https://github.com/flutter/flutter-intellij/issues/1150): the device
  chooser can open Android emulators (and the iOS simulator on MacOS). However,
  in order to locate the Android SDK, the Flutter plugin relies on the `ANDROID_HOME`
  environment variable being set; we're working to relax this requirement.
- We are seeing occasional timeouts when trying to connect to Observatory when
  debugging against the iOS simulator; restarting the simulator should get you
  back on track.
