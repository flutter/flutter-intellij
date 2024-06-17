This is a general page to help diagnose issues.

## SDK settings page

You can validate that you have a Flutter SDK configured for your project by going to `Settings > Languages & Frameworks > Flutter` and confirm that there is a setting for the `Flutter SDK path` field.

## Flutter doctor

Flutter doctor is a great tool to validate settings. You can run it from the command-line via `flutter doctor`, and from IntelliJ via `Tools > Flutter > Flutter Doctor`. Please validate that the results for each look reasonable, don't report issues, and agree with each other in terms of the installed toolchains and paths.

## Running apps

If you see issues when running apps - apps not coming up or buttons not becoming enabled - you can run in verbose mode. Open the Flutter preferences (`Settings > Languages & Frameworks > Flutter`), and select `'Enable verbose logging'`. The next time you run an app, it will log verbose information to the run console; this may help in diagnosing where the problem is.

## Analysis issues

The Dart specific smarts for the flutter-intellij plugin are powered by Dart's analysis server. It's a server process that provides errors and warnings as you type, code completion, refactorings, and other IDE-level features. This series of steps can help provide us with the information necessary to debug issues with the analysis server:

- in IntelliJ, open the registry (`cmd-shift-A` and search for `'Registry'`)
- search for the key `dart.additional.server.arguments`
- change the value to `--instrumentation-log-file=/path/to/log/file` (and point it to a path for your machine)
- close the dialog (a restart may be necessary)

The next time IntelliJ runs, the analysis server will log diagnostic information to the given path.

## Restarting

If all else fails, try:

- restarting IntelliJ
- restarting the iOS simulator
- checking Android devices for a 'authorize debugging' dialog
