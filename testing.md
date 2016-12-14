# Plugin Smoke Testing

Manual tests to execute before plugin releases.

## Setup

Pre-reqs: [flutter setup](https://flutter.io/setup/) and [flutter getting started](https://flutter.io/getting-started/).

## Project Creation

Validate basic project creation.

* Create a simple project (`File > New > Project…`, pick `Flutter`).
* Confirm that:
  * Project contents are created.
    * Verify that a run configuration (sharing the project name) is enabled in the run/debug selector.
  * Navigation works.
    * Open `lib/main.dart` and navigate to `ThemeData`.
  * There are no analysis errors or warnings.
  * Pub operations work.
    * Open `pubspec.yaml` and click the "Get" and "Update" links.
  * Flutter operations work.
    * Open `flutter.yaml` and click the "Doctor" link.
  * Code completion works.
    * Make sure analysis has completed. Then change `primarySwatch: Colors.` to some other color and validate that you get completions.

## Project Import

Validate that an externally created project can be imported.

* choose `File > Open...`
* browse to and select `<flutter-root>/examples/flutter_gallery`
* ensure there are no analysis errors or warnings
* ensure that the `flutter_gallery` launch configuration shows up and is selected

## Device Detection.

Validate device selection.

* (OS X) Verify that the simulator can be opened.
  * Disconnect all devices and quit the iOS simulator.
  * Ensure that a menu item to open the iOS simulator is enabled in the device pull down menu.
  * Select "Open iOS Simulator".
  * Verify that the simulator opens.

## Run / Debug

Validate basic application running and debugging.

In the newly created app:
* plugin in an Android device, or open the iOS Simulator
* set a breakpoint on the `_counter++` line
* hit the `'debug'` icon to start the app running
* verify the app appears on the device
* tap the `'+'` icon on the app
* verify that the IDE pauses at the breakpoint
* hit resume in the debugger
* tap the `'+'` icon on the app
* verify that the IDE pauses at the breakpoint, and that the `Variables` pane has the right value for `_counter` 

## Hot Reload

Validate basic hot reload functionality.

Assuming the app state from above (i.e., leave the Debug session running):
* change the `Button tapped ... times.` line to end in an exclamation point
* change the `_counter++` line to `_counter--` to end in an exclamation point
* hit the hot reload button in the debugger UI
* validate that
  1. the state persisted (the same number of clicks in the UI), and
  2. the text changed to end in an exclamation point
  3. the + button decreases the value
* change the text and counter line back
* hit the `Full Application Restart` button
* validate that the text and state resets, and count increases

Keybindings:
* verify that the hot reload keybinding works (on a mac: `cmd-option-;` or `cmd-\`)

## Debugging Sessions

Validate that a sequence of sessions works as expected.

After testing the above, terminate your debugging session and start another.
* validate that a breakpoint is hit
* verify that the reload keybinding works as expected

## Fresh Install Configuration

Verify installation and configuration in a fresh IDEA installation.

* Follow the instructions to [simulate a fresh installation](https://github.com/flutter/flutter-intellij/wiki/Development#simulating-a-fresh-install).
* (If not running in a "runtime workbench", [install the plugins](https://flutter.io/setup/#install-the-plugins).)
* Open "Languages & Frameworks>Flutter" in Preferences and verify that there is no Flutter SDK set.
* Set the Flutter SDK path to a valid SDK location.
  * Verify that invalid locations are rejected (and cannot be applied).
* Verify project creation, run/debug.
