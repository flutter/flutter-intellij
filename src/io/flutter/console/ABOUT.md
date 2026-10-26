<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Console

## Overview
Manages the IDE console view for Flutter tasks, providing formatted output, exception folding, and file path hyperlinking. Consoles are shown as content tabs in the platform Messages tool window.

## Interface
- [`FlutterConsole`](FlutterConsole.java) (package-private; used through `FlutterConsoles`)
- [`FlutterConsoleExceptionFolding`](FlutterConsoleExceptionFolding.java)
- `FlutterConsoleExceptionFolding.shouldFoldLine`
- `FlutterConsoleExceptionFolding.shouldBeAttachedToThePreviousLine`
- `FlutterConsoleExceptionFolding.getPlaceholderText`
- [`FlutterConsoleFilter`](FlutterConsoleFilter.java)
- `FlutterConsoleFilter.FlutterConsoleFilter`
- `FlutterConsoleFilter.fileAtPath`
- `FlutterConsoleFilter.applyFilter`
- [`FlutterConsoleFolding`](FlutterConsoleFolding.java): folds long `.../flutter --no-color ...` command lines into a short `flutter ...` placeholder.
- `FlutterConsoleFolding.shouldFoldLine`
- `FlutterConsoleFolding.shouldBeAttachedToThePreviousLine`
- `FlutterConsoleFolding.getPlaceholderText`
- [`FlutterConsoles`](FlutterConsoles.java)
- `FlutterConsoles.displayProcessLater`
- `FlutterConsoles.displayMessage` (with an overload taking `clearContent`)

## Invariants
- There is one shared Flutter console per Module, and one global Flutter console for tasks without a module.
- UI operations like showing a process or displaying a message are always executed on the UI thread.
- Both folding classes are registered as `console.folding` extensions in [`plugin.xml`](../../../../resources/META-INF/plugin.xml), with FlutterConsoleExceptionFolding ordered after FlutterConsoleFolding.
- Exception folding only occurs if FlutterSettings specifies showing structured errors and not including all stack traces. Folding toggles on lines starting with `=======`, and the placeholder shows the first folded line.
- FlutterConsoleFolding does not attach to the previous line to prevent appending to unrelated output.
- FlutterConsoleFilter is attached only to module consoles; the global console has no file or `flutter doctor` hyperlinking.
- File paths are only hyperlinked if they resolve to an existing file under one of the module's content roots (or its `example/` subdirectory). Recognized forms include `Launching <path>`, `open <path>`, `(path:line)`, `file:///...:line:col`, and `path:line:col: Error`.
- Lines starting with "Lost connection to device" are highlighted in the error color with a boxed effect.

## Side Effects
- Adds content tabs (wrapping ConsoleViews) to the Messages tool window via `MessageView` and activates that tool window.
- Spawns external processes, such as executing `open` for Xcode files.
- Starts the 'flutter doctor' process when its hyperlink is clicked.
- Displays error dialogs or notifications if external commands or file openings fail.
