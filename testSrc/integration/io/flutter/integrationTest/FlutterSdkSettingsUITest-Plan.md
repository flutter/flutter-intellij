# Test Plan: Flutter SDK Settings UI Test

## Goal
Verify that a user can set the Flutter SDK path in the IDE settings and that the IDE uses the newly configured SDK.

---

## Setup (runs before each test)

1. **Ensure alternate Flutter SDK is installed**
   - Check if `~/.flutter_test_sdk/flutter/bin/flutter` exists.
   - If not, download Flutter 3.24.5 for the current OS/arch and extract it there.
   - Store the path as `alternateSdkPath` for use in the test.
   - This directory is never deleted between runs (Flutter is large).

2. **Launch the IDE**
   - Start IntelliJ IC with the Flutter plugin and Dart plugin installed.
   - The IDE opens to the welcome screen (no project).
   - `FLUTTER_SDK` env var must be set to a valid Flutter SDK for initial plugin operation.

---

## Test: `openSettingsAndQuit` ✅

1. Wait for the welcome screen to appear.
2. Invoke the `ShowSettings` action to open the Settings dialog.
3. Wait for the Settings dialog to appear (up to 20 seconds).
4. Click Cancel to close the dialog without changes.
5. IDE is closed in `@AfterEach`.

---

## Test: `setFlutterSdkPath` (next)

1. Wait for the welcome screen to appear.
2. Create a new flutter project (similar to new project test in NewProjectUITest.kt)
3. Wait for the project window to open (probably about 20 seconds max)
4. Check the current flutter version using flutter doctor. save that value to check at the end
5. Open the Settings dialog.
6. Navigate to **Languages & Frameworks > Flutter** in the settings tree.
7. Read the current SDK path from the Flutter SDK text field.
8. Clear the field and type `alternateSdkPath`.
9. Click OK/Apply to save.
10. Close the Settings dialog.
11. [ Open a project / create a project to land in the IDE frame ]
12. Open the terminal.
13. Run `flutter doctor`.
14. Wait for `Doctor summary` to appear.
15. Parse the Flutter version from the output.
16. Assert that the version matches Flutter 3.24.5 (the alternate SDK). This should not be the same value as it was set to before.

---

## Utilities

| File | Purpose |
|---|---|
| `Setup.kt` | Configures and launches the IDE test context |
| `utils/FlutterTestSdk.kt` | Downloads/caches the alternate Flutter SDK at `~/.flutter_test_sdk` |
| `utils/FlutterDoctor.kt` | Runs `flutter doctor` in the IDE terminal and parses the version |
| `utils/NewProject.kt` | Automates new project creation from the welcome screen |

---

## Open Questions / Notes

- After changing the SDK in settings, does the IDE terminal pick up the new SDK immediately, or does it require a restart / new project?
- Should the test reset the SDK path back to the original after it runs?
- The `flutter doctor` step requires an `ideFrame` (i.e. an open project). We need to decide whether to create a new project or open an existing one for this step.
