## 23.2
- updated some Bazel breakpoint logic

## 23.1
- disabled an Android facet's ALLOW_USER_CONFIGURATION setting, to address a continuous indexing issue

## 23.0
- outline view: removed the experimental flag
- outline view: filter the outline view to only show widgets by default
- inspector: several stability and polish improvements
- inspector: now supports inspecting multiple running apps at the same time
- we now show material icons and colors in code completion (requires 2017.3 or AS 3.1)
- running and debugging flutter test adding for Bazel launch configurations
- added an 'Extract method' refactoring
- the preview dart 2 flag can now accept the SDK default, be set to on, or set to off
- Android Studio: we now support 3.1
- Android Studio: fixed an issue where Android Studio was indexing frequently
- experimental: added a live sparkline of the app's memory usage
- experimental: added a live preview area in the Outline view
- experimental: added the ability to format (and organize imports) on save

## 22.2
- when installing the Flutter SDK, use the 'beta' channel instead of 'dev'

## 22.1
- when installing the Flutter SDK, use the 'dev' channel instead of 'alpha'
- fix an issue with the Flutter Outline view on Windows

## 22.0
inspector view:
- support for multiple running applications
- basic speed search for the Inspector Tree
- restore flutter framework toggles after a restart
- expose the observatory timeline view (the dashboard version) (#1744)
- live update of property values triggered each time a flutter frame is drawn. (#1721)
- enum property support and tweaks to property display. (#1695)
- HD inspector Widgets (#1682)
- restore inspector splitter position (#1676)
- open the inspector view at app launch (#1670)

outline view:
- rename 'Add widget padding' assist to 'Add padding' (#1771)
- bind actions to move widget down/up. (#1768)
- rename 'Replace with children' to 'Remove widget'. (#1764)
- add action for 'Replace with children' assist. (#1759)
- update messages for wrapping with Column/Row. (#1745)
- add icons and actions for wrapping into Column and Row. (#1743)
- show build() methods in bold (#1731)
- associate the Center action with the corresponding Quick Assist. (#1726)
- navigate from source to Preview view. (#1710)
- add speed search to the Preview view. (#1696)
- add basic Flutter Preview view. (#1678)

platforms:
- support 2018.1 EAP
- no longer build for 2017.2

miscellaneous:
- fix for displaying the flutter icon for flutter modules
- fix for issue 1772, Switch Bazel flag for launching apps (#1775)
- add support for displaying flutter color shades in the editor ruler (#1770)
- add a flag to enable --preview-dart-2 (#1709)
- smarts to run `flutter build` before trying open Xcode (#1373). (#1694)
- harden error reporting on iOS simulator start failures (#1647). (#1681)

## 21.2
- Fix an NPE when the Flutter SDK version file contains the text 'unknown'


## 21.1
- Fix an NPE when reading the Flutter SDK version file

## 21.0
- select an existing config at launch
- fix test discovery for plugin example tests
- fix discovery of tests in example subdirs
- improve pub root detection for flutter tests
- actionable “restart” debugging console output
- improve console hyperlinking for local files
- fix run config autoselection for plugin projects
- for non-bazel project configurations, don't show the FlutterBazelRunConfigurationType
- update FlutterViewCondition to be bazel project aware
- remove the preference for the Inspector view (it's now on by default)
- rename the Flutter view to Flutter Inspector
- clean up of the Flutter Inspector View icons
- show color properties with a nice color swatch icons
- add a notification for reloaded but not run elements
- show flutter material icons in the inspector
- for Bazel launch configurations, update the android_cpu architecture type from armeabi to armeabi-v7a


## 20.0
- improved console filtering
- improved unit test running support to allow running package:flutter tests
- improved "Open with Xcode..." logic to work better for plugin projects
- fixed project creation to properly respect custom creation options (such as target language)
- fixed an NPE sometimes encountered when deleting projects


## 19.1
- Bazel run configuration updates

## 19.0
- fixed an issue with reload when multiple project windows are open
- fixed running Flutter tests in nested groups
- fixed miscellaneous project wizard issues
- fix to ensure we don't create Flutter library entries for non-Flutter projects
- fixed project name validation in the new project creation wizard to be more performant
- fixed project opening to only open main.dart if no other editors are open
- fix to limit Flutter icon contributions to Flutter projects
- reload on save updated to ignore errors in test files
- IDEA EAP support
- fix to give restarted apps focus on iOS
- miscellaneous Android Studio support fixes
- fixed check for Flutter tests to not mis-identify vanilla Dart tests
- improved error reporting on project creation failures


## 18.4
- Revert to 18.1 to address an NPE in the FlutterInitializer class
- fixed an issue where reload on save could not be disabled

## 18.3
- fixed a build problem that prevented the Android Studio plugin from creating projects

## 18.2
- fixed an issue where reload on save could not be disabled
- fixed an exception that could occur on project creation

## 18.1
- fixed hot reload issue when multiple project windows were open
- fixed 'Open Observatory timeline' action

## 18.0
- Android Studio support
- for flutter launches, support passing in a --flavor param
- reload on save now on by default
- improved and reorganized the Flutter view's toolbar
- analysis toast provides a new hyperlink to open the analysis view
- reloads disallowed while another reload is taking place
- support to show referenced flutter plugin in the project view


## 17.0
- improved new project wizard
- improvements to the reload-on-save behavior
- improved and reorganized the Flutter view's toolbar
- fixes to the Flutter icon decorations in the editor ruler
- fixes to group handling for widget tests
- display a ballon toast if there are analysis issues when running apps
- added a toggle inspect mode toolbar button
- speed improvements to the device switcher pulldown


## 16.0
- device list refresh fixes
- support for flutter run in profile and release modes
- support for reading the android sdk location from flutter tools
- support for discovering and running Flutter widget tests
- Flutter test console improvements
- support for running flutter doctor in a Bazel workspace
- test file icon annotations
- support for locating a missing flutter SDK in .packages files
- open emulator action sorting
- test state icons for Flutter tests
- editor line markers for Flutter tests
- added a new restart daemon action
- open emulator action sorting
- run/debug button enablement improvements
- fix to ensure the `Install SDK…` action is always visible
- support for running a single Flutter test, by name
- install creation progress UI fixes
- project creation fixes for small IDEs
- fixes to android emulator launching


## 15.2
- fix for an exception in the new project wizard in WebStorm (#1234)

## 15.1
- fix for a file watching related NPE on build systems using Bazel (#1191)

## 15.0
- UI for starting android emulators from the device pull-down
- workflow for installing a Flutter SDK from the New Flutter Project wizard
- Flutter SDK configuration inspection improvements
- improved error reporting on project creation failures
- improved app reload feedback
- Flutter View toolbar tweaks
- initial support for running unit tests with `flutter test`
- new action to open iOS resources in Xcode


## 14.0
- user toggleable option to enable more verbose debug logging of Flutter app runs
- fixes to the new Flutter Module workflow
- improved console logging on Flutter app termination
- improved error reporting on Observatory connection and Flutter View open failures
- removed Flutter SDK settings from default projects
- improved project name validation (to align with checks in `flutter create`)
- console hyperlinks for Xcode resources
- fix to inherit Android JDK setting when creating Flutter projects
- fix to ensure Flutter console filtering is only applied to Flutter consoles
- improved device daemon interop
- improved SDK version checking


## 13.1
- project opening improvements
- new action to open the Flutter view
- module name validation on creation
- fix to ensure all open files are saved to disk before running Flutter actions
- improved progress reporting during calls to 'flutter create'
- miscellaneous fixes and analytics improvements

## 13.0
- small IDE support improvements
- android module enablement on project creation
- project explorer icon customizations
- support for Flutter drop frame debugging
- hot reload UX improvements
- Bazel run config refinements
- support for toggling OS in the Flutter View
- Flutter CLI interop fixes (proper env setup)
- color icon improvements
- bump to require 2017.1+


## 12.1
- fix an issue with enabling Dart support for modules from the Flutter settings page

## 12.0
- support for IDEA `2017.1`
- new Flutter `stless`, `stful`, and `stanim` live templates
- new assists for editing the widget hierarchy:
    - move widget up or down
    - re-parent widget or list of widgets
    - convert `child:` keyword to `children:`
- support for specifying "Additional Args" to Flutter application launches
- default run configuration creation on project open (when possible)
- device menu improvements
- miscellaneous bug fixes


## 0.1.11.2
- fix an NPE in the Flutter View when launching an app

## 0.1.11.1
- fix to a use after dispose exception in the Flutter View

## 0.1.11
- Flutter tool window badging when active
- iOS console output folding improvements
- Flutter reload actions added to main "Run" menu
- devices menu fixes
- improved tooltips for pubspec editor notifications


## 0.1.10
- fixes to pubspec timestamp checking
- analytics events for run, debug, and process stop
- fix to `flutter doctor` to better support multiple runs
- fix to the reload action for apps launched from 'run'


## 0.1.9.1
- fix button enablement in the Flutter View
- fix the reload action for apps launched from 'run'

## 0.1.9
- added a 'Flutter' view to allow users to toggle Flutter framework debugging features while running
- fixes to the visibility of the "Tools" menu
- inspection to detect pubspec modifications (that may imply out of date package dependencies)
- key bindings fixes
- support for opening source folders as Flutter projects (using "Open...")
- run and debug button enablement fixes
- fix to bring iOS simulator to front on run/debug
- fix to handle devfs breakpoints for projects without pubspecs


## 0.1.8.1
- improve handling of breakpoints for the bazel launch config

## 0.1.8
- fixed race condition in console reporting on project creation
- improved interaction between Flutter and Dart plugins during project creation (no more unnecessary nags to run pub)
- improvements to version checking
- settings UI refinements
- new "Help > Flutter Plugin" top-level menu
- added reload/restart actions in the main toolbar
- improved console folding for iOS messages
- fixed NPE in project creation


## 0.1.7
- improved console output folding when running iOS apps
- actions for Flutter package get and package update
- a new top level Flutter menu (Tools>Flutter) with common Flutter actions
- updated hot reload and restart icons
- editor annotations showing Flutter colors and icons in the editor ruler
- better console filtering (less noise)
- improved detection of Flutter projects missing a Flutter module type


## 0.1.6
- reload and restart keybinding mapping fixes
- new butter bar with actions for flutter.yaml files
- "run" behavior re-designed to support reload
- improved console output for reloading and restarting
- miscellaneous fixes and stability improvements


## 0.1.5
- console filtering for flutter run output
- improved messaging for incomplete Flutter SDK configurations
- support for new application events produced by Flutter tools
- fixed duplicate service protocol console logging
- Flutter run configuration cleanup
- fixed NPE in showing progress from Flutter tools tasks
- migration away from storing Flutter SDK location in an application library


## 0.1.4.1
- removed an exception notification when we receive unknown events from the flutter tools

## 0.1.4
- first public release


## 0.1.3
- notifications for projects that look like Flutter apps but do not have Flutter enabled
- improved Flutter preference UI and SDK configuration
- IDEA version constraints to ensure that the plugin cannot be installed in incompatible IDEA versions


## 0.1.2
- fixed device selector filtering


## 0.1.1
- removed second (redundant) "open observatory" button
- filtering to ensure the Flutter device selector only appears for Flutter projects
- fixed hangs on app re-runs


## 0.1.0
- initial alpha release
