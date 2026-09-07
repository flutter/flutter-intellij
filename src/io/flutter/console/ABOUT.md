<!--* freshness: { reviewed: '2026-08-31' } *-->
# Flutter Console

## Overview
Manages the IDE console view for Flutter tasks, providing formatted output, exception folding, and file path hyperlinking.

## Interface
- `FlutterConsole`
- `FlutterConsoleExceptionFolding`
- `FlutterConsoleExceptionFolding.shouldFoldLine`
- `FlutterConsoleExceptionFolding.shouldBeAttachedToThePreviousLine`
- `FlutterConsoleExceptionFolding.getPlaceholderText`
- `FlutterConsoleFilter`
- `FlutterConsoleFilter.FlutterConsoleFilter`
- `FlutterConsoleFilter.fileAtPath`
- `FlutterConsoleFilter.applyFilter`
- `FlutterConsoleFolding`
- `FlutterConsoleFolding.shouldFoldLine`
- `FlutterConsoleFolding.shouldBeAttachedToThePreviousLine`
- `FlutterConsoleFolding.getPlaceholderText`
- `FlutterConsoles`
- `FlutterConsoles.displayProcessLater`
- `FlutterConsoles.displayMessage`

## Invariants
- There is one shared Flutter console per Module, and one global Flutter console for tasks without a module.
- UI operations like showing a process or displaying a message are always executed on the UI thread.
- Exception folding only occurs if FlutterSettings specifies showing structured errors and not including all stack traces.
- FlutterConsoleFolding does not attach to the previous line to prevent appending to unrelated output.
- File paths in the console are only hyperlinked if the files exist in the file system or module's content roots.

## Side Effects
- Creates and manipulates UI elements including ToolWindows, MessageViews, and ConsoleViews.
- Spawns external processes, such as executing 'open' for external files.
- Starts the 'flutter doctor' process when its hyperlink is clicked.
- Displays error dialogs or messages if external commands or file openings fail.
