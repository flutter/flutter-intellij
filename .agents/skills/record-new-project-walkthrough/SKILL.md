---
name: record-new-project-walkthrough
description: Run the New Flutter Project UI test against a real IntelliJ IDEA build headlessly under Xvfb, record the virtual screen, and produce a step-by-step screenshot walkthrough (Markdown carousel) of the plugin creating a Flutter app.
---

# Skill: Record a New Flutter Project Walkthrough

Use this skill to show, step by step, the locally built Flutter plugin creating a new Flutter app in
a real IDE. The skill runs `NewProjectIU262UITest` (IntelliJ IDEA 2026.2.3, build `262.10968.63`,
Dart `509.0.0`) through the IntelliJ IDE Starter / Driver framework, records the screen, and turns the
recording into an annotated screenshot carousel.

## Prerequisites

- Linux with `xvfb` (`xvfb-run`, `Xvfb`), `x11-utils` (`xdpyinfo`) and `imagemagick` (`import`, `convert`, `compare`).
- A Flutter SDK, either in `FLUTTER_SDK` or as `flutter` on `PATH`.
- The UI test sources from this branch:
  - `testSrc/integration/io/flutter/integrationTest/NewProjectIU262UITest.kt`
  - `setupTestContextIU262` in `testSrc/integration/io/flutter/integrationTest/Setup.kt`
  - the lazy `path.to.build.plugin` and `java.awt.headless` settings on the `integration` task in `build.gradle.kts`.
- No other IDE Starter test IDE running (the script refuses to start if one is).
- The first run downloads the IDE (~1.6 GB) into `build/out/ide-tests/`.

## Workflow

### 1. Record

From the repository root, run:

```bash
FLUTTER_SDK=<path-to-flutter-sdk> .agents/skills/record-new-project-walkthrough/scripts/record_walkthrough.sh
```

This takes about 3 minutes once the IDE is cached. The script:

1. Starts [`capture_xvfb.sh`](scripts/capture_xvfb.sh) in the background. It waits for the Xvfb display that IDE Starter creates and, sampling every 0.5 s, saves a PNG whenever the screen changes.
2. Runs `./gradlew --no-daemon buildPlugin integration --tests io.flutter.integrationTest.NewProjectIU262UITest` with `DISPLAY` and `WAYLAND_DISPLAY` unset, so IDE Starter launches the IDE under `xvfb-run`.
3. Runs [`build_walkthrough.sh`](scripts/build_walkthrough.sh). It skips blank frames, keeps frames where more than 0.5% of the pixels changed (set `MIN_PERCENT` to tune this), trims the black background, and writes `walkthrough.md`.

The output goes to `build/walkthrough/<timestamp>/` (or the directory passed as the first argument):

| File | Contents |
|---|---|
| `walkthrough.md` | Carousel of the key frames, captioned with capture times |
| `step_NN_HHMMSS.png` | Trimmed key frames |
| `frames/` | Every distinct captured frame |
| `gradle.log`, `capture.log` | Raw logs |

The test deletes the project it creates in `~/IdeaProjects`. Set `KEEP_TEST_PROJECT=true` to keep it.

### 2. Verify

- `gradle.log` should contain `Project my_flutter_app_<millis> created and indexed.` and `BUILD SUCCESSFUL`.
- The capture log should report a frame count. Zero frames means the IDE never started under Xvfb; see Troubleshooting.

### 3. Caption

`build_walkthrough.sh` only adds timestamps as captions. View each `step_*.png` and replace the
captions in `walkthrough.md` with what the step shows. A successful run normally contains these steps:

1. IntelliJ IDEA splash screen.
2. Welcome screen (Flutter and Dart plugins loaded).
3. New Project dialog (opens on Java; Flutter is listed under Generators).
4. Flutter selected, with the SDK path filled in from `FLUTTER_SDK`.
5. Flutter project settings page (`FlutterGeneratorPeer`).
6. Project name typed.
7. "Creating Flutter project" progress (runs `flutter create`).
8. Project window opening and indexing.
9. Project tree populated (`android`, `ios`, `lib`, `linux`, `macos`, `test`, `web`, `windows`), with the `main.dart` run configuration in the toolbar.
10. IDE trial tab (IntelliJ IDEA starts a 30-day trial automatically).

Remove near-duplicate steps (for example, a half-painted window). Fast steps, such as typing the
project name right before **Create**, can fall between samples. If you need one, pick it from
`frames/`, or rerun `build_walkthrough.sh` with a lower `MIN_PERCENT`.

### 4. Present

Share `walkthrough.md` and its images. The carousel uses image paths relative to the Markdown file.
Tools that need absolute image paths (for example, agent artifact viewers) need the images copied
next to the artifact and the paths rewritten.

## Targeting another IDE build

To record against a different IntelliJ build, add a context to `Setup.kt` that sets `buildNumber` and a
compatible `dartPluginVersion`. The Dart plugin's `since`/`until` builds must cover the IDE build;
check them with:

```bash
curl -s 'https://plugins.jetbrains.com/api/plugins/6351/updates?size=8'
```

Then point a copy of `NewProjectIU262UITest` at the new context and pass it in with `TEST_CLASS=<fqcn>`.
To list available IDE builds, run `./gradlew printProductsReleases`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| The IDE window appears on the desktop but nothing is clicked | Wayland blocks the `java.awt.Robot` input the Driver uses. The run must be headless: `DISPLAY` and `WAYLAND_DISPLAY` must be unset (the script does this). |
| The test IDE loads Dart but not Flutter | The `integration` task installed an empty `build/distributions/flutter-intellij.zip`. The task must resolve `buildPlugin.archiveFile` lazily so that the `Flutter.zip` name applies. |
| `Plugin 'Dart' ... requires IDE build 261.* or older` | The Dart version doesn't support the IDE build. Pick a compatible version (see above). |
| `Thread leaked: WLKeyboard.KeyRepeatManager` | The test JVM started the Wayland AWT toolkit. The `integration` task must pass `-Djava.awt.headless=true`. |
| `DriverIllegalStateException: No such session` during `waitForIndicators` | IDEA 2026.2 rebuilt frame components after the project opened. The test retries the wait up to 3 times; rerun if it still fails. |
| Timeout waiting for the welcome screen | A license or trial dialog is blocking startup. Under Xvfb, 2026.2 starts a trial automatically; on other builds, check `frames/` for the dialog. |
| `Plugins not loaded` notification | Bundled IDEA plugins that require `com.intellij.modules.ultimate` (AOP, Dev Containers, Kubernetes). Not related to Flutter. |

## Notes

- **Do not commit** generated output; it lives under `build/`.
- The UI tests in `testSrc/integration` are experimental (see `testSrc/integration/README.md`) and are not run in CI.
