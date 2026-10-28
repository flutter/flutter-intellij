# Integration Tests

> **Note:** The tests in this directory are **experimental**.

## Current Status

- **No CI Integration:** These integration tests do not currently run in our Continuous Integration (CI) environment. `tool/github.sh` has an `INTEGRATION_BOT` branch, but the GitHub presubmit matrix does not include it.
- **Brittle & Environment-Dependent:** While these tests executed successfully on the creator's local setup when written, steps have not been taken to ensure reproducibility across different systems. Additionally, they are not routinely checked or maintained to confirm they still pass.
- **Manual Execution:** They can be run manually, but execution results may vary or fail depending on local configuration.

## Running

Run `./gradlew integration`. This directory is the `integration` source set in [`build.gradle.kts`](../../build.gradle.kts), and the tests use the [IntelliJ IDE Starter](https://github.com/JetBrains/intellij-ide-starter) framework with JUnit 5. The `integration` task runs them one at a time (`maxParallelForks = 1`) with a 1g–4g heap, against the plugin zip built by `buildPlugin`.

## Contents

All files are under `io/flutter/integrationTest/`:

- [`Setup.kt`](io/flutter/integrationTest/Setup.kt): Configures the IDE Starter test context: paths, plugin installation, VM options, and OS-specific settings.
- [`NewProjectUITest.kt`](io/flutter/integrationTest/NewProjectUITest.kt): UI test for the new Flutter project flow.
- [`DeepLinksToolWindowTest.kt`](io/flutter/integrationTest/DeepLinksToolWindowTest.kt): UI test for the Deep Links tool window.
- [`vmService/VmServiceTest.kt`](io/flutter/integrationTest/vmService/VmServiceTest.kt): Launches a `flutter test` process and exchanges VM Service messages with it over WebSocket.
- [`utils/NewProject.kt`](io/flutter/integrationTest/utils/NewProject.kt): Helpers for creating new projects in UI tests.

## Guidance & Strategy

- **Do Not Rely on These Tests:** Do not depend on these integration tests to verify plugin functionality or test for regressions.
- **Ongoing Evaluation:** We are currently evaluating:
  - The overall reliability and stability of the integration testing framework.
  - How effectively we can maintain these tests over time.
  - How best to incorporate integration testing into our overall plugin maintenance and testing strategy.

## Running the Tests Yourself

The UI tests start a real IntelliJ IDEA (downloaded on first run into `build/out/ide-tests/`) with your locally
built Flutter plugin, and drive it with the IntelliJ IDE Starter / Driver framework: they click, type, and read the
UI like a user would.

### Prerequisites

- Linux, Windows, or macOS, with a Flutter SDK. Point `FLUTTER_SDK` at it (for example
  `export FLUTTER_SDK=~/develop/flutter`, or `$env:FLUTTER_SDK = "C:\path\to\flutter"` in PowerShell).
- For recordings (optional, **Linux only**): `xvfb`, `xserver-xephyr`, `x11-utils`, `imagemagick`, and `python3`.
- About 2 GB of disk for the first IDE download.

### Run one test

From the repository root, on Linux:

```bash
unset DISPLAY WAYLAND_DISPLAY
./gradlew --no-daemon integration --tests io.flutter.integrationTest.RunWidgetTestUITest
```

On Windows (PowerShell):

```powershell
.\gradlew.bat --no-daemon integration --tests io.flutter.integrationTest.RunWidgetTestUITest
```

On macOS, run the same `./gradlew` command as on Linux, without the `unset` line.

- **Why `unset DISPLAY WAYLAND_DISPLAY` (Linux):** IDE Starter then runs the IDE on a hidden virtual display (Xvfb).
  On a Wayland desktop, a test pointed at your real display opens the IDE, but its clicks and keystrokes never
  arrive, because Wayland blocks synthetic input.
- **Windows and macOS have no hidden display:** the IDE opens on your desktop. Keep the desktop unlocked and don't
  click or type in the IDE while the test runs.
- **Why `--no-daemon`:** a running Gradle daemon keeps the environment it started with, so the test might not see
  `FLUTTER_SDK` or the display settings above.
- A run takes about 2 to 3 minutes once the IDE is downloaded. The test result is in
  `build/reports/tests/integration/index.html`, and the IDE's own log is in
  `build/out/ide-tests/tests/<IDE build>/<TestName>/log/idea.log`.
- On Windows, test projects are created under `%USERPROFILE%\ft` (a short path, to stay under the 260-character path
  limit).

### Watch a test, or record it as a walkthrough

Recording walkthroughs is supported on **Linux only**, so record on a Linux machine even if you develop on Windows
or macOS. On Windows and macOS you can still watch a test, because it runs on your desktop.

To see what a test does, use the walkthrough recorder. It runs the test on its own display and saves a step-by-step
screenshot walkthrough (a single self-contained HTML page) to `~/walkthroughs/`:

```bash
# Watch it live: a window opens on your desktop with the IDE inside (don't click in it while it runs)
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh --mode visible --size 1600x900 \
  --test io.flutter.integrationTest.RunWidgetTestUITest

# Or record it without showing anything on screen
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh \
  --test io.flutter.integrationTest.RunWidgetTestUITest

# Browse all recorded walkthroughs
xdg-open ~/walkthroughs/index.html
```

Run the recorder with `--help` for all options. For full details, see
[`.agents/skills/record-test-walkthrough/SKILL.md`](../../.agents/skills/record-test-walkthrough/SKILL.md).
For the background, design decisions, and current status of these tests and the recorder, see
[`.agents/design-docs/integration-ui-tests-and-walkthroughs.md`](../../.agents/design-docs/integration-ui-tests-and-walkthroughs.md).

### Available tests

All of these run against IntelliJ IDEA 2026.2.3. Except for `NewProjectIU262UITest`, they start with the IDE already
open on a Flutter app created with `flutter create`.

| Test | What it shows |
|---|---|
| `NewProjectIU262UITest` | Creating a Flutter app with the plugin's New Project wizard |
| `PubGetUITest` | Adding a dependency to `pubspec.yaml` and running **Flutter Pub Get** |
| `RunWidgetTestUITest` | Running `test/widget_test.dart` from the editor and seeing it pass in the test runner |
| `ExtractWidgetUITest` | The **Extract Flutter Widget...** refactoring |
| `StatelessWidgetLiveTemplateUITest` | Expanding the `stless` live template |
| `RunAppAndHotReloadUITest` | Running the app on a device and hot reloading a change |

Each test's class comment explains what it shows, step by step.

### Useful environment variables

| Variable | Effect |
|---|---|
| `FLUTTER_SDK` | Flutter SDK used by the tests (required) |
| `KEEP_TEST_PROJECT=true` | Keep the test's Flutter project instead of deleting it afterwards |
| `KEEP_IDE_OPEN_MINUTES=<n>` | Leave the IDE running for `n` minutes after the test, for inspection |
| `FLUTTER_TEST_DEVICE=chrome\|emulator` | Device for `RunAppAndHotReloadUITest` (default `chrome`) |
| `FLUTTER_TEST_AVD=<name>` | Android emulator for `FLUTTER_TEST_DEVICE=emulator` (default `Medium_Phone`) |

### If something goes wrong

- **IntelliJ shows its license screen and the test hangs.** IntelliJ's licensing isn't deterministic. Start the same
  IntelliJ version yourself (`./gradlew runTarget -Pide=Ultimate -PideV=2026.2.3`), sign in and start the trial,
  activate a license, or choose the free option, then run the test again.
- **The test can't find the Flutter SDK.** Check that `FLUTTER_SDK` is exported in the same shell, and use `--no-daemon`.
- **Nothing happens in the IDE window.** You're probably running against your Wayland desktop; `unset DISPLAY
  WAYLAND_DISPLAY` first, or use the recorder's `--mode visible`.
- **The emulator variant can't find the device.** If an emulator was killed uncleanly, it may be stuck behind a crash
  dialog. Shut it down with `adb emu kill` and try again.
