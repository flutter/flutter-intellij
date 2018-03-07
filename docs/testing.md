# Plugin Smoke Testing

Manual tests to execute before plugin releases.

## Setup

Pre-reqs: Run through the [flutter setup](https://flutter.io/setup/) and
[flutter getting started](https://flutter.io/getting-started/) guides.

* Run `flutter upgrade` in a terminal to get the latest version prior to starting testing.

## Testing matrix

* testers should generally test against IntelliJ CE and Android Studio
* we also support internal IntelliJ; we should have testers assigned to each (IntelliJ CE,
  internal IntelliJ, and Android Studio) for every roll

## Project Creation

Validate basic project creation.

* Create a **simple project** (`File > New > Project...`, pick `Flutter`; on Android Studio, `File > New > New Flutter Project...`).
  * (select an `Application` type project)
* Confirm that:
  * Project contents are created.
    * Verify that a run configuration (`main.dart`) is enabled in the run/debug selector.
  * Navigation works.
    * Open `lib/main.dart` and navigate to `ThemeData`.
  * There are no analysis errors or warnings.
  * Pub operations work.
    * Open `pubspec.yaml` and click the "Packages get" and "Packages upgrade" links.
  * Flutter operations work.
    * From the `Tools` menu, select `Flutter` > `Flutter Doctor`.
  * Code completion works.
    * Change `primarySwatch: Colors` to some other color and validate that you
      get completions.

* Create a **plugin project** (`File > New > Project...`, pick `Flutter`; on Android Studio, `File > New > New Flutter Project...`), specify "Plugin" as the project type and select "Swift" for the iOS language.
* Confirm that:
  * Project contents are created.
    * Verify that `<project root>/ios/Classes/Swift<Project Name>Plugin.swift` exists.
    * Verify that a run configuration (`<Project Name>.dart`) is enabled in the run/debug selector.
    
## Project Open

Validate that our example projects can be opened.

* close any open projects in IDEA
* choose `File > Open...`
* browse to and select `<flutter-root>/examples/flutter_gallery`
* ensure there are no analysis errors or warnings
* test that code completion is working as expected
* ensure that the `main.dart` launch configuration shows up and is selected

Validate that a `flutter create` project can opened.

* close any open projects in IDEA
* run `flutter create` in a terminal to create a new project `testcreate`
* choose `File > Open...` and browse to and select the 'testcreate' project
  * or, type `idea .` or `studio .` from the CLI if you have the CLI launcher installed
* ensure there are no analysis errors or warnings
* test that code completion is working as expected
* ensure that the `main.dart` launch configuration shows up and is selected

## Device Detection

Validate device selection.

### Any OS

* Physical Android device
  * Plug in a physical Android device
  * Ensure that a menu item appears in the device pull down for the device.
  
* Emulated Android device
  * Start an Android emulator
  * Ensure that a menu item appears in the device pull down for the device.

### macOS only

* Verify that the simulator can be opened.
  * Quit any open iOS simulator.
  * From the device pull-down, select "Open iOS Simulator" and verify that the simulator opens.

## Run / Debug

Validate basic application running and debugging.

In the newly created app:
* plug in an Android device (or open the iOS Simulator)
* set a breakpoint on the `_counter++` line
* hit the `'debug'` icon to start the app running
* verify the app appears on the device
* tap the `'+'` icon on the app
* verify that the IDE pauses at the breakpoint, and that the `Variables` pane has
  the right value for `_counter`
* hit resume in the debugger

## Hot Reload

Validate basic hot reload functionality.

Assuming the app state from above (i.e., leave the Debug session running):
* modify the text for the `'You have pushed the button this many times:'` line
* change the `_counter++` line to `_counter--`
* hit the hot reload button in the debugger UI
* validate that
  1. the state persisted (the same number of clicks in the UI), and
  2. the text changed to end in an exclamation point
  3. the + button decreases the value

Keybindings:
* verify that the hot reload keybinding works (on a mac: `cmd-\`)
* verify that the reload-on-save feature works (hitting cmd-s / ctrl-s triggers a reload)

## Hot Restart

* change the text and counter line back
* hit the `Full Application Restart` button (or hit the Debug button again, or cmd-shift-s / ctrl-shift-s)
* validate that the text and state resets, and the count increases

## Debugging Sessions

Validate that a sequence of sessions works as expected.

After testing the above, terminate your debugging session and start another.
* validate that a breakpoint is hit
* verify that the reload keybinding works as expected

## Project Open Verification

Verify that projects without Flutter project metadata open properly and are given the Flutter module type.

* create a new Flutter project and delete IntelliJ metadata:
  * `flutter create foo_bar`
  * `cd foo_bar`
  * `rm -rf .idea`
  * `rm foo_bar.iml`
* open project ("File > Open")
* verify that the project has the Flutter module type (the device pull-down displays) and analyzes cleanly

## Fresh Install Configuration

Verify installation and configuration in a fresh IDEA installation.

* Follow the instructions to
  [simulate a fresh installation](https://github.com/flutter/flutter-intellij/wiki/Development#simulating-a-fresh-install).
* (If not running in a "runtime workbench", [install the plugins](https://flutter.io/setup/#install-the-plugins).)
* Open "Languages & Frameworks>Flutter" in Preferences and verify that there is
  no Flutter SDK set.
* Set the Flutter SDK path to a valid SDK location.
  * Verify that invalid locations are rejected (and cannot be applied).
* Verify project creation, run/debug.
