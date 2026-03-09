# Test Plan: Flutter SDK Settings UI Test

## Goal
Verify that a user can set the Flutter SDK path in the IDE settings and that the IDE uses the newly configured SDK.

---

## Setup (runs before each test)

1. **Ensure alternate Flutter SDK is installed**
   - Check if `~/.flutter_test_sdk/flutter/bin/flutter` exists.
   - If not, download Flutter 3.38.10 for the current OS/arch and extract it there.
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

## Test: `setFlutterSdkPath`

1. Wait for the welcome screen to appear.
2. Create a new Flutter project to land in the IDE frame.
3. Wait for the project window to open and indicators to clear.
3b. Lower `pubspec.yaml` sdk constraint to `>=3.10.9 <4.0.0`. The project is created with
    Flutter 3.41.4 (Dart 3.11.1) which sets `>=3.11.0` — incompatible with the alternate
    Flutter 3.38.10 (Dart 3.10.9) we are about to switch to.
4. Open the Settings dialog via `ShowSettings` action.
5. Navigate to **Languages & Frameworks > Flutter** in the settings tree.
6. Detect the version of the flutter sdk already set. This is in text  below the sdk text box. This is initialVersion
7. Locate the Flutter SDK field — it is a `ComboBox` with an `ExtendableTextField` editor.
8. **Set the field value directly** using `JTextFieldUI.text = alternateSdkPath` (avoids keyboard selection issues).
9. **Assert the field value** equals `alternateSdkPath` before saving (verifies the value was written correctly).
10. Detect the version of the flutter sdk that is now set. This is in text below the sdk text box. It should not equal the old value, it should equal the new value. This is newVersion
11. Assert `newVersion != initialVersion`.
12. Assert `newVersion == "3.38.10"`.
13. Click OK to save.
14. Reopen the settings again, and ensure the path you set before is still set, and its still the same version
15. Click cancel to close.


**Key implementation note:** The SDK field is `com.intellij.openapi.ui.ComboBox` with a `BasicComboBoxEditor` wrapping an `ExtendableTextField`. Use `JTextFieldUI` from the driver SDK to set/read the value programmatically.

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
