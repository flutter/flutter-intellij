<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter IntelliJ Core

## Overview
Core entry points, constants, messaging, and project initialization logic for the Flutter IntelliJ plugin.

## Interface
- [`FlutterBundle`](FlutterBundle.java): message bundle backed by [`FlutterBundle.properties`](FlutterBundle.properties).
- [`FlutterConstants`](FlutterConstants.java)
- [`FlutterErrorReportSubmitter`](FlutterErrorReportSubmitter.java)
- [`FlutterInitializer`](FlutterInitializer.java)
- [`FlutterMessages`](FlutterMessages.java)
- [`FlutterProjectActivity`](FlutterProjectActivity.kt) (Kotlin)
- [`FlutterProjectDisposable`](FlutterProjectDisposable.java)
- [`FlutterStudioStartupActivity`](FlutterStudioStartupActivity.java)
- [`FlutterUtils`](FlutterUtils.java)
- [`ObservatoryConnector`](ObservatoryConnector.java)
- [`ProjectOpenActivity`](ProjectOpenActivity.java)
- [`actions`](actions/ABOUT.md)
- [`analytics`](analytics/ABOUT.md)
- [`android`](android/ABOUT.md)
- [`console`](console/ABOUT.md)
- [`dart`](dart/ABOUT.md)
- [`deeplinks`](deeplinks/ABOUT.md)
- [`devtools`](devtools/ABOUT.md)
- [`editor`](editor/ABOUT.md)
- [`font`](font/ABOUT.md)
- [`inspections`](inspections/ABOUT.md)
- [`jxbrowser`](jxbrowser/ABOUT.md)
- [`logging`](logging/ABOUT.md)
- [`module`](module/ABOUT.md)
- [`project`](project/ABOUT.md)
- [`propertyeditor`](propertyeditor/ABOUT.md)
- [`pub`](pub/ABOUT.md)
- [`refactoring`](refactoring/ABOUT.md)
- [`run`](run/ABOUT.md)
- [`samples`](samples/ABOUT.md)
- [`sdk`](sdk/ABOUT.md)
- [`settings`](settings/ABOUT.md)
- [`survey`](survey/ABOUT.md)
- [`template`](template/ABOUT.md)
- [`test`](test/ABOUT.md)
- [`toolwindow`](toolwindow/ABOUT.md)
- [`utils`](utils/ABOUT.md)
- [`view`](view/ABOUT.md)
- [`vmService`](vmService/ABOUT.md)
- [`widgetpreview`](widgetpreview/ABOUT.md)

## Invariants
- ProjectOpenActivity executes earlier during project startup (before indexing), while FlutterInitializer executes later (after index is up to date). Both are registered as `postStartupActivity` in [`plugin.xml`](../../../resources/META-INF/plugin.xml); the earlier ordering comes from ProjectOpenActivity implementing `DumbAware`.
- FlutterStudioStartupActivity is registered only in [`studio-contribs.xml`](../../../resources/META-INF/studio-contribs.xml), so it runs only in Android Studio.
- FlutterProjectActivity provides a fail-safe execution wrapper for project startup activities by catching and logging exceptions.
- FlutterConstants holds the Dart keyword set, Flutter package dependency names, and reload reason strings, plus the Flutter settings page ID, the path separator, and `URL_*` documentation links.
- ObservatoryConnector defines a standard interface for connecting to an observatory-based debugger.

## Side Effects
- FlutterErrorReportSubmitter builds a markdown bug report (including `flutter --version` output from a subprocess with a 3s timeout and the last 200 lines of the plugin log, `dash.log` (`PluginLogger.LOG_FILE_NAME`)), writes it with the GitHub new-issue URL to a `bug-report.md` scratch file, and opens it in the IDE for the user to submit manually.
- FlutterInitializer (startup work for projects that declare Flutter):
  - Starts daemon services (DeviceService, DevToolsService) and disables the Gradle migration notification.
  - Fixes module settings without a reload, sets the project SDK from `ANDROID_HOME` when unset, opens `main.dart`, and auto-creates its run configuration.
  - Initializes tool windows (Inspector, DevTools, DevTools Extensions), run/survey notifications, reload-on-save, format-on-save, and the plugins library watcher.
  - Adds the Android module where applicable, checks SDK versions, and shows the Android Studio "Try Gemini" notification.
- FlutterInitializer forwards theme changes (editor color scheme and UI settings, debounced by 1s) to the Dart Tooling Daemon (DTD), only when the SDK supports DTD. `setUpDtdAnalytics` is currently a stub.
- ProjectOpenActivity (only for projects that declare Flutter) initializes JxBrowser, excludes Android framework detection in non-Android Studio IDEs, sets the project type to `io.flutter` for non-Android projects in Android Studio, queries the `android-studio-dir` Flutter config on a pooled thread, and shows 'pub get' notifications.
- FlutterStudioStartupActivity adds Gradle listeners, runs add-to-app detection, fixes the module type and enables the Dart SDK for unconfigured Flutter modules, and switches to the Project view pane on first open.
- FlutterMessages dispatches warning, error, and info notifications to the IntelliJ messaging bus and shows UI dialogs.
