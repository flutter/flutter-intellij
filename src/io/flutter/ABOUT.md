<!--* freshness: { reviewed: '2026-08-31' } *-->
# Flutter IntelliJ Core

## Overview
Core entry points, constants, messaging, and project initialization logic for the Flutter IntelliJ plugin.

## Interface
- `FlutterBundle`
- `FlutterConstants`
- `FlutterErrorReportSubmitter`
- `FlutterInitializer`
- `FlutterMessages`
- `FlutterProjectActivity`
- `FlutterStudioStartupActivity`
- `FlutterUtils`
- `ObservatoryConnector`
- `ProjectOpenActivity`
- `./actions/ABOUT.md`
- `./analytics/ABOUT.md`
- `./android/ABOUT.md`
- `./console/ABOUT.md`
- `./dart/ABOUT.md`
- `./deeplinks/ABOUT.md`
- `./devtools/ABOUT.md`
- `./editor/ABOUT.md`
- `./font/ABOUT.md`
- `./inspections/ABOUT.md`
- `./jxbrowser/ABOUT.md`
- `./logging/ABOUT.md`
- `./module/ABOUT.md`
- `./project/ABOUT.md`
- `./propertyeditor/ABOUT.md`
- `./pub/ABOUT.md`
- `./refactoring/ABOUT.md`
- `./run/ABOUT.md`
- `./samples/ABOUT.md`
- `./sdk/ABOUT.md`
- `./settings/ABOUT.md`
- `./survey/ABOUT.md`
- `./template/ABOUT.md`
- `./test/ABOUT.md`
- `./toolwindow/ABOUT.md`
- `./utils/ABOUT.md`
- `./view/ABOUT.md`
- `./vmService/ABOUT.md`
- `./widgetpreview/ABOUT.md`

## Invariants
- ProjectOpenActivity executes earlier during project startup (before indexing), while FlutterInitializer executes later (after index is up to date).
- FlutterProjectActivity provides a fail-safe execution wrapper for project startup activities by catching and logging exceptions.
- FlutterConstants maintains definitive sets of Dart keywords, package dependencies, and reload reason strings.
- ObservatoryConnector defines a standard interface for connecting to an observatory-based debugger.

## Side Effects
- FlutterErrorReportSubmitter creates a markdown bug report as a scratch file and writes the GitHub URL into the text of the scratch file, opening it in the IDE for the user to submit manually.
- FlutterInitializer modifies the project environment by starting daemon services (DeviceService, DevToolsService), fixing module settings without a reload, checking SDK versions, and triggering IDE notifications.
- FlutterInitializer listens to IDE theme changes and forwards them to the Dart Tooling Daemon (DTD).
- ProjectOpenActivity modifies project structure by initializing JxBrowser, excluding Android framework detection in non-Android Studio IDEs, and showing 'pub get' notifications.
- FlutterStudioStartupActivity applies Android Studio specific configuration, potentially altering the active ProjectView pane on first open.
- FlutterMessages dispatches warning, error, and info notifications to the IntelliJ messaging bus and shows UI dialogs.
