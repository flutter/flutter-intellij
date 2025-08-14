# <img src="https://raw.githubusercontent.com/dart-lang/site-shared/refs/heads/main/src/_assets/image/flutter/icon/64.png" alt="Flutter" width="26" height="26"/> Flutter Plugin for IntelliJ

[![Latest plugin version](https://img.shields.io/jetbrains/plugin/v/9212)](https://plugins.jetbrains.com/plugin/9212-flutter)
[![Build Status](https://github.com/flutter/flutter-intellij/workflows/presubmit/badge.svg)](https://github.com/flutter/flutter-intellij/actions?query=branch%3Amaster+workflow%3Apresubmit)

An IntelliJ plugin for [Flutter](https://flutter.dev/) development. Flutter is a multi-platform
app SDK to help developers and designers build modern apps for iOS, Android and the web.

## Documentation

- [flutter.dev](https://flutter.dev)
- [Installing Flutter](https://docs.flutter.dev/get-started/install)
- [Getting Started with IntelliJ](https://docs.flutter.dev/tools/android-studio)

## Fast development

Flutter's <em>hot reload</em> helps you quickly and easily experiment, build UIs, add features,
and fix bugs faster. Experience sub-second reload times, without losing state, on emulators,
simulators, and hardware for iOS and Android.

<img src="https://user-images.githubusercontent.com/919717/28131204-0f8c3cda-66ee-11e7-9428-6a0513eac75d.gif" alt="Make a change in your code, and your app is changed instantly.">

## Quick start

A brief summary of the [getting started guide](https://docs.flutter.dev/tools/android-studio):

- install the [Flutter SDK](https://docs.flutter.dev/get-started/install)
- run `flutter doctor` from the command line to verify your installation
- ensure you have a supported IntelliJ development environment; either:
    - the latest stable version of [IntelliJ](https://www.jetbrains.com/idea/download), Community or Ultimate Edition (EAP versions are not
      always supported)
    - the latest stable version of [Android Studio](https://developer.android.com/studio) (note: Android Studio Canary versions are
      generally _not_ supported)
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
  [YouTrack tracker](https://youtrack.jetbrains.com/issues?q=Subsystem:%20%7BLang.%20Dart%7D%20%20State:%20Unresolved)

## Known issues

Please note the following known issues:

- [#601](https://github.com/flutter/flutter-intellij/issues/601): IntelliJ will
  read the PATH variable just once on startup. Thus, if you change PATH later to
  include the Flutter SDK path, this will not have an effect in IntelliJ until you
  restart the IDE.
- If you require network access to go through proxy settings, you will need to set the
  `https_proxy` variable in your environment as described in the
  [pub docs](https://dart.dev/tools/pub/troubleshoot#pub-get-fails-from-behind-a-corporate-firewall).
  (See also: [#2914](https://github.com/flutter/flutter-intellij/issues/2914).)

## Dev channel

If you like getting new features as soon as they've been added to the code then you
might want to try out the dev channel. It is updated daily with the latest contents
from the "master" branch. It has minimal testing. Set up instructions are in the wiki's
[dev channel page](./docs/Dev-Channel.md).

## Flutter SDK compatibility

These are the versions of Flutter SDK that current and previous Flutter plugins support:

| Flutter SDK version | Flutter plugin version |
|---------------------|------------------------|
| up to v3.7          | 85.3.2 and earlier     |
| v3.10 and above     | Currently supported    |

Here is more information on the Flutter plugin's support for Flutter
SDKs: https://docs.flutter.dev/tools/sdk#sdk-support-for-flutter-developer-tools.
