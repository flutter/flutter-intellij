# Open an existing Flutter project that has no `.idea/` directory

**Goal:** as a Flutter developer, I want to open a freshly cloned Flutter
project (no `.idea/` directory) in IntelliJ IDEA and have it open as a Flutter
project.
**Source:** https://github.com/flutter/flutter-intellij/issues/8845 (closed; the
fix is hidden from this spec and reverted by the runner). MVP test T4, run
through the `bug-to-spec` path.
**Seam gate result:** `ui` — the bug is in project *opening*: which
`ProjectOpenProcessor` claims the folder and whether a project frame appears.
That only happens in a real IDE process; a platform test starts with a project
already open. No existing spec shares this fixture (the project is opened
without its `.idea/`).

## Target seam

- Project open path for a Flutter folder in IntelliJ IDEA: the plugin's
  `projectOpenProcessor` registration in `plugin.xml`
  (`io.flutter.project.FlutterProjectOpenProcessor`, per the report) and/or the
  startup activity that configures a Flutter module after open
  (`io.flutter.FlutterInitializer`).
- `io.flutter.utils.FlutterModuleUtils` (module type / `hasFlutterModule`).

**Why not a unit test:** the failure is that *no project opens at all*. The
observable is a project frame (or its absence) after the IDE is asked to open a
folder, plus the module state the plugin leaves behind; both need the IDE's
project-opening machinery, not a fixture.

## Fixture

- Base project: `flutter create` counter app, then **`.idea/` deleted** before
  the IDE opens it (the report's `rm -rf test_app/.idea`). Applied in
  `prepareProject()`; there is no file overlay to copy.
- Device: none needed.
- Product: `IU` (the report is IntelliJ IDEA-specific; Android Studio has its
  own opener and was fixed separately per the report).
- `ide.version`: **`252.28238.7`** (IntelliJ IDEA 2025.2, the build in the
  report). The bug depends on the platform's `File > Open` processor order:
  with the fix reverted it reproduces on 252 and does **not** on IU 2026.2
  (262.10968.63), where the test passes on both builds. Runs on 252 need
  `DART_PLUGIN_VERSION=506.1.0` (Dart 510+ requires 253+) and LSP4IJ, which
  the harness installs.

## Preconditions

- Flutter SDK configured (`FLUTTER_SDK`).
- The project folder exists on disk with `pubspec.yaml` and `lib/main.dart`
  and **no** `.idea/` directory (checked in O1).
- No project is open.
- The IDE is started with no project (welcome screen). The folder is opened
  through the welcome screen's *Open* button and the file chooser, which is
  the user's *File > Open* path. (An earlier draft passed the folder on the
  IDE's command line instead; on IU 2026.2 that path does **not** reproduce
  the bug, so it is not equivalent and must not be used as a shortcut here.)

## Steps

| # | Task | User interaction | Drives |
|---|---|---|---|
| 1 | Open | On the welcome screen, click *Open* | `welcomeScreen { openProjectButton.click() }` |
| 2 | Open | **(trigger)** In the Open dialog, select the project folder and click OK | `FileChooserDialogUi.selectFolder(projectDir)` then OK (row-walk; the 2026.2 chooser has no path field) |
| 3 | Open | Wait for the project window and indexing | `waitForProjectReady()` (after O2 has confirmed a project opened) |
| 4 | Confirm | Open `lib/main.dart` from the Project view | `openProjectFile("lib/main.dart")` |

## Expected outcome

The project opens in a project window, is indexed, and is recognised as a
Flutter project: the Flutter module type is set, the Dart SDK is enabled, and
the Flutter toolbar (device selector) is present.

**Requirements:** the project window appears and indexing finishes within
3 minutes of step 2.

## Oracle

- **O1 — Pre-state** (before step 1; the IDE is on the welcome screen):
  - On disk: `projectDir/pubspec.yaml` and `projectDir/lib/main.dart` exist and
    `projectDir/.idea` does **not**.
- **O2 — Attribution** (after step 2):
  - A project opens within 3 minutes of step 2 (the plugin's opener is first
    in line for a Flutter folder, so no project opening is the plugin's
    failure; the report's symptom).
  - `FlutterModuleUtils.hasFlutterModule(project)` is true: our plugin set the
    module up for this project.
  - The Flutter device selector is on the main toolbar: the plugin's project
    UI is active for this project.
- **O3 — Positive postconditions** (after step 4):
  - The project window is indexed.
  - `lib/main.dart` is open in an editor and shows the Flutter template
    (`MaterialApp`).
  - The Project view lists `pubspec.yaml`.
- **O4 — Negative / analysis postconditions:**
  - The welcome screen is not showing (the report's symptom: "nothing happens").
  - No "No Flutter SDK configured." editor banner on `lib/main.dart`.
  - No `SEVERE` `idea.log` entries with `io.flutter` frames (harness, green runs).

## Test level

`ui` — `ExistingProjectUITest` subclass, `@Tag("agent-generated")`.

## Certification plan

- **Red build:** `HEAD` with the fix reverted by the runner (closed issue):
  `build-broken-plugin.sh --repo flutter --revert e965bb8d9`, plus a one-line
  patch restoring the `ProjectManager` import the reverted code needs.
  Expected failure: `O2` (no project opens within 3 minutes, or it opens
  without the Flutter module), after steps 1–2 and `O1` complete. Both are the
  report's "project should open … with Flutter module type set" not happening.
- **Green build:** `HEAD`, `N_green = 3`.
- **N_red:** 1 (the report describes a deterministic failure).
- **IDE:** `252.28238.7` for both red and green (see Fixture). On 262 the red
  build survives (`mutant-survived`), so a certification there proves nothing.
- **Witness:** done 2026-11-13 by hand on both builds via `launch-ide.sh`;
  broken: welcome screen stays after OK, fixed: project opens.
