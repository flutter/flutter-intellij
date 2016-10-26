## Flutter Plugin for IntelliJ

An IntelliJ plugin for [Flutter](https://flutter.io/) development. 

[![Build Status](https://travis-ci.org/flutter/flutter-intellij.svg)](https://travis-ci.org/flutter/flutter-intellij)

For information about contributing code to the plugin please see [CONTRIBUTING.md](CONTRIBUTING.md).

## Getting started

To try the plugin, see the [setup](https://flutter.io/setup/#flutter-intellij-ide-plugins) and [how-to](https://flutter.io/intellij-ide/) instructions.

## Known issues

Please note the following known issues:

* [#316](https://github.com/flutter/flutter-intellij/issues/316): Opening existing flutter projects with the plugin can lead to confusing messages if you do not install the 'Flutter' plugin first (see [setup](https://flutter.io/setup/#flutter-intellij-ide-plugins)).

* [#332](https://github.com/flutter/flutter-intellij/issues/332): The 'hot reload' and 'restart' buttons should not be pushed repeately prior to the operation completing. If pushed fast several times in a row, a crash may be experienced.

* [#349:](https://github.com/flutter/flutter-intellij/issues/349): The plugin supports running the app without debugging support (Run>Run) and with debugging support (Run>Debug). Currently a 'stop' button will be enabled in the toolbar for both cases, however note that the button is only functional when debugging.

* [#305](https://github.com/flutter/flutter-intellij/issues/305): When stopped during a debugging session, you are free to make edits to the source code, and you can even push those changes to the target with hot reload. However, note that currently we do not update the breakpoints on the target if these move as a result of the edit, and they may thus get out of sync.

* [#371](https://github.com/flutter/flutter-intellij/issues/371): If you see a prompt to update the 'Dart SDK', you can ignore this, as the Dart SDK is bundled with the Flutter SDK, and is automatically updated when you update Flutter.

