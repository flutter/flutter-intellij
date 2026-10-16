---
name: record-test-walkthrough
description: Record any automated UI test (for example an IntelliJ IDE Starter integration test) as a step-by-step screenshot walkthrough, running headless under Xvfb or visibly in a Xephyr window, and save it as a self-contained HTML page and Markdown.
---

# Skill: Record a Test Walkthrough

Use this skill to turn an automated UI test into a browsable, step-by-step walkthrough. The automated
test is what runs; this skill only provides a display for it and records that display.

- **The test** drives the UI, for example an IDE Starter / Driver test in `testSrc/integration`.
  `NewProjectIU262UITest` (creates a Flutter app in IntelliJ IDEA 2026.2.3) is one example.
- **The recorder** ([`record_walkthrough.sh`](scripts/record_walkthrough.sh)) starts its own X display,
  runs the test with `DISPLAY` pointing at it, captures the display, and builds the walkthrough.

## Prerequisites

- Linux with `xvfb` (headless), `xserver-xephyr` (visible), `x11-utils` (`xdpyinfo`), `imagemagick` (`import`, `convert`, `compare`) and `python3`.
- An automated test that shows UI on `$DISPLAY`. For IDE Starter tests:
  - The test must run in a fresh JVM with the environment passed through. `--test` uses `./gradlew --no-daemon`.
  - IDE Starter launches the IDE on `$DISPLAY` when it is set, and only falls back to its own `xvfb-run` when it is not.
  - Flutter tests need `FLUTTER_SDK`. The recorder defaults it to the SDK of `flutter` on `PATH`.
- The first IDE Starter run downloads the IDE into `build/out/ide-tests/`.

## Workflow

### 1. Choose the test and mode

| Mode | Display | Use when |
|---|---|---|
| `headless` (default) | Xvfb, nothing on screen | Unattended runs, CI-like recordings, working in parallel |
| `visible` | Xephyr window on your desktop | Watching the test live or debugging it. Don't click inside the window while it runs. |

Both modes are real X servers, so tests that send input through `java.awt.Robot` work even on a Wayland
desktop. Running a test directly against a Wayland session does not work, because Wayland blocks
synthetic input to normal windows.

### 2. Record

From the repository root:

```bash
# A Gradle test (default task: integration)
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh \
  --test io.flutter.integrationTest.NewProjectIU262UITest

# Watch it live
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh --mode visible \
  --test io.flutter.integrationTest.NewProjectIU262UITest

# Any other command that opens UI on $DISPLAY
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh --title "My flow" -- ./gradlew --no-daemon integration --tests 'io.flutter.integrationTest.SomeTest'
```

| Option | Meaning |
|---|---|
| `--mode headless\|visible` | Display type (default `headless`) |
| `--test <fqcn>` | Test class or `class.method`, run as `./gradlew --no-daemon <task> --tests <fqcn>` |
| `--task <name>` | Gradle test task for `--test` (default `integration`) |
| `--out <dir>` | Output directory (default `build/walkthrough/<test>-<timestamp>`). Use a directory outside `build/` to survive `./gradlew clean`. |
| `--title <text>` | Walkthrough title (default `<test> walkthrough`) |
| `--size <WxH>` | Screen size (default `1920x1080`). Use a smaller size in visible mode if the window doesn't fit your screen. |
| `--min-percent <n>` | Key-frame change threshold (default `0.5`) |

The recorder exits with the test's exit status. It:

1. Starts Xvfb or Xephyr on a free display (`:90` and up).
2. Starts [`capture_display.sh`](scripts/capture_display.sh), which samples the display every 0.5 s and saves a PNG whenever it changes.
3. Runs the test with `DISPLAY` set to that display and `WAYLAND_DISPLAY` unset, teeing output to `test.log`.
4. Runs [`build_walkthrough.sh`](scripts/build_walkthrough.sh), which skips blank frames, keeps frames where more than 0.5% of the pixels changed, trims the black background, and writes default captions.
5. Runs [`render_walkthrough.py`](scripts/render_walkthrough.py) to produce the browsable files.

### 3. Outputs

| File | Contents |
|---|---|
| `walkthrough.html` | **Self-contained page for browsing later.** Images are embedded, so the single file can be copied, archived, or attached. Slideshow with Prev/Next (arrow keys, Home/End), thumbnails, and a "Show all steps" view (`A`). |
| `walkthrough.md` | Standard Markdown, one heading and image per step, with relative image links |
| `captions.txt` | One caption per step, the source for both files above |
| `step_NN_HHMMSS.png` | Trimmed key frames |
| `frames/` | Every distinct captured frame |
| `test.log`, `capture.log`, `xserver.log` | Raw logs |

### 4. Verify

- `Test exit status: 0`, plus the test's own success output in `test.log` (for Gradle: `BUILD SUCCESSFUL`).
- `capture.log` reports a frame count. If it's zero, the UI never appeared on the recorder's display. Check that the test honors `DISPLAY` and runs in a fresh process (see Troubleshooting).

### 5. Caption

Default captions are capture times. View each `step_*.png`, replace the matching lines in
`captions.txt` (one line per step, in order; lines starting with `#` are ignored), then re-render:

```bash
python3 .agents/skills/record-test-walkthrough/scripts/render_walkthrough.py <walkthrough-dir>
```

Use `--title` to change the title. To drop a near-duplicate step, delete its `step_*.png` and its
caption line. Fast steps can fall between samples; pick them from `frames/`, or rerun
`build_walkthrough.sh <frames-dir> <walkthrough-dir> <lower-percent>` (this resets `captions.txt`).

### 6. Present

- To browse later, open `walkthrough.html` in a browser (`xdg-open <walkthrough-dir>/walkthrough.html`).
- To share as Markdown, send `walkthrough.md` together with the `step_*.png` files.
- Tools that need absolute image paths (for example, agent artifact viewers) need the images copied next to the artifact and the paths rewritten.

## Example: New Flutter project test

`NewProjectIU262UITest` opens IntelliJ IDEA 2026.2.3 with the locally built plugin, clicks **New Project
→ Flutter → Next**, types a project name, clicks **Create**, waits for indexing, and deletes the project
afterwards (`KEEP_TEST_PROJECT=true` keeps it). A recording normally shows:

1. Splash screen.
2. Welcome screen.
3. New Project dialog (opens on Java; Flutter is listed under Generators).
4. Flutter selected, with the SDK path filled in from `FLUTTER_SDK`.
5. Flutter project settings page (`FlutterGeneratorPeer`).
6. "Creating Flutter project" progress.
7. Project window opening and indexing.
8. Project tree populated, with the `main.dart` run configuration in the toolbar.
9. IDE trial tab (IntelliJ IDEA starts a 30-day trial automatically).

To create a similar test for another flow or IDE build, copy it and its `Setup.setupTestContextIU262`
context. The Dart plugin version must support the IDE build; check with
`curl -s 'https://plugins.jetbrains.com/api/plugins/6351/updates?size=8'`. List IDE builds with
`./gradlew printProductsReleases`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Zero frames, or only a black screen | The UI went to another display. Gradle daemons keep their original environment, so use `--no-daemon`. Make sure the test doesn't override `DISPLAY`. |
| Test clicks do nothing when run outside the recorder on Wayland | Expected: Wayland blocks `java.awt.Robot` input. Record with `--mode visible` instead. |
| IDE Starter test loads Dart but not Flutter | The `integration` task installed an empty `flutter-intellij.zip`. It must resolve `buildPlugin.archiveFile` lazily so that the `Flutter.zip` name applies. |
| `Plugin 'Dart' ... requires IDE build 261.* or older` | The Dart plugin version doesn't support the IDE build. |
| `Thread leaked: WLKeyboard.KeyRepeatManager` | The test JVM started the Wayland AWT toolkit. The `integration` task passes `-Djava.awt.headless=true`. |
| `DriverIllegalStateException: No such session` | IDEA rebuilt frame components; `NewProjectIU262UITest` retries its final wait. Rerun if it persists. |
| Timeout waiting for the welcome screen | A license or trial dialog is blocking startup. Check `frames/`. |
| Xephyr window is larger than the screen | Pass a smaller `--size`, for example `1600x900`. |

## Notes

- **Do not commit** generated output; it lives under `build/` by default.
- The UI tests in `testSrc/integration` are experimental (see `testSrc/integration/README.md`) and are not run in CI.
