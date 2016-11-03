# Plugin Smoke Testing

Manual tests to execute before plugin releases.

## Project Creation

Validate basic project creation.

* Follow the instructions to [create a simple project](https://flutter.io/intellij-ide/#creating-a-new-project).
* Confirm that:
  * Project contents are created.
    * Verify that a run configuration (sharing the project name) is enabled in the run/debug selector.
  * Navigation works. 
    * Open `lib/main.dart` and navigate to `ThemeData`.
  * There are no analysis errors or warnings.
  * Pub operations work.
    * Open `pubspec.yaml` and click the "Get" and "Update" actions.
    
## Device Detection.

Validate device selection.

* (OS X) Verify that the simulator can be opened.
  * Disconnect all devices and quit the iOS simulator.
  * Ensure that a menu item to open the iOS simulator is enabled in the device pull down menu.
  * Select "Open iOS Simulator".
  * Verify that the simulator opens.

## Project Import

Validate that an externally created project can be imported. (TODO.)

## Run / Debug

(TODO.)

## Fresh Install Configuration

Verify installation and configuration in a fresh IDEA installation.

* Follow the instructions to [simulate a fresh installation](https://github.com/flutter/flutter-intellij/wiki/Development#simulating-a-fresh-install).
* (If not running in a "runtime workbench", [install the plugins](https://flutter.io/setup/#install-the-plugins).)
* Open "Languages & Frameworks>Flutter" in Preferences and verify that there is no Flutter SDK set.
* Set the Flutter SDK path to a valid SDK location.
  * (TODO: add error cases for invalid locations and incomplete installs.)
* Verify project creation, run/debug.  

