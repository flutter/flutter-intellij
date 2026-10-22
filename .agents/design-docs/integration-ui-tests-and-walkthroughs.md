# Integration UI tests and walkthrough recordings

Context for the IntelliJ IDE Starter UI tests and the `record-test-walkthrough` skill on the
`integration-test-iu262` branch. Read this before continuing the work.

## Goal

Automated UI tests that drive a real IntelliJ IDEA with the locally built Flutter plugin, and a way to **record any
of those tests as a step-by-step screenshot walkthrough** that a person can browse later to see what the plugin
does and what to look for at each step.

## Where the work lives

| Item | Location |
|---|---|
| Branch (local only, never pushed) | `integration-test-iu262` in `~/repos/flutter-intellij-working`, kept rebased on `main` |
| UI tests | `testSrc/integration/io/flutter/integrationTest/` |
| How to run them (for humans) | [`testSrc/integration/README.md`](../../testSrc/integration/README.md), "Running the Tests Yourself" |
| Recording skill (for agents) | [`.agents/skills/record-test-walkthrough/SKILL.md`](../skills/record-test-walkthrough/SKILL.md) and its `scripts/` |
| Recorded walkthroughs | `~/walkthroughs/` (outside the repo); `~/walkthroughs/index.html` lists them, older sets are in `archive-*/` |
| Downloaded test IDE | `build/out/ide-tests/` (IntelliJ IDEA 2026.2.3, build `262.10968.63`, about 1.6 GB) |

Related local branch: `external-ide-action-group-bgt`, a one-line change making
`FlutterExternalIdeActionGroup.getActionUpdateThread()` return `BGT` (the group is unregistered in `plugin.xml`, so it
has no runtime effect). Also kept rebased on `main`.

## What exists

### Tests (IntelliJ IDEA 2026.2.3 + Dart plugin 509.0.0)

| Test | Flow |
|---|---|
| `NewProjectIU262UITest` | Welcome screen -> New Project -> Flutter -> Next -> name -> Create -> project opens |
| `PubGetUITest` | Add `http: ^1.2.0` to `pubspec.yaml`, run Flutter Pub Get, check `pubspec.lock`, `package_config.json`, and the pub cache |
| `RunWidgetTestUITest` | Run `test/widget_test.dart` from the editor (Ctrl+Shift+F10), wait for "1 test passed" |
| `ExtractWidgetUITest` | Extract Flutter Widget on `Text('You have pushed...')` -> `CounterLabel` |
| `StatelessWidgetLiveTemplateUITest` | Type `stless` + Tab in `lib/greeting.dart`, name it `Greeting` |
| `RunAppAndHotReloadUITest` | Select a device, run, edit the title, hot reload, stop; `FLUTTER_TEST_DEVICE=chrome` (default) or `emulator` |

- All tests except `NewProjectIU262UITest` extend `ExistingProjectUITest`. It creates the project with
  `flutter create` (never the plugin's wizard), opens the IDE on it, waits for indexing, dismisses IDE interruption
  dialogs, and deletes the project afterwards (`KEEP_TEST_PROJECT=true` keeps it).
- Each test's class KDoc follows a fixed shape: **Shows**, Steps, Expected result, and optionally Implementation
  notes. The recorder puts that text at the top of the walkthrough.
- Tests announce each user-visible step with `WalkthroughSteps.step(title, expect)`. The recorder uses this to
  caption screenshots and show "What to look for", and pauses between steps while recording.

### Recorder (`.agents/skills/record-test-walkthrough/scripts/`)

- `record_walkthrough.sh`: starts its own X display (`--mode headless` = Xvfb, `--mode visible` = Xephyr window),
  runs the test with `DISPLAY` pointing at it, captures frames, builds the walkthrough, and scans the IDE log for
  license and trial events.
- `capture_display.sh`: saves a frame whenever the display changes (0.5 s sampling, millisecond timestamps).
- `build_walkthrough.sh`: picks key frames, matches them to test steps, keeps at least one frame per step, and
  flags screenshots that are probably not useful.
- `render_walkthrough.py`: writes the self-contained `walkthrough.html` and the Markdown `walkthrough.md`.
- `extract_test_summary.py`, `build_index.py`: the test summary and `~/walkthroughs/index.html`.

## Key decisions and why

- **IntelliJ IDEA 2026.2.3 (unified "Ultimate" distribution).** It's the newest stable build the plugin supports
  (`untilBuild` was removed upstream; build 263 is an EAP). The Community edition stopped at 2025.2. The trade-off
  is IntelliJ's licensing; see below.
- **Dart plugin 509.0.0** for build 262. Dart 505 only supports builds up to 261. Dart 507+ replaced the
  `weberknecht` WebSocket classes; upstream already handles that.
- **Never drive the IDE on the Wayland desktop.** Wayland blocks the synthetic input (`java.awt.Robot` via XTest)
  that the Driver uses: the IDE opens, but clicks and keys never arrive. Tests run on a real X server instead:
  Xvfb (headless), Xephyr (visible), or IDE Starter's own `xvfb-run` when `DISPLAY` is unset. `Setup.kt` forces
  `-Dawt.toolkit.name=XToolkit` for the IDE.
- **`./gradlew --no-daemon`** for test runs, because daemons keep the environment they started with (`DISPLAY`,
  `FLUTTER_SDK`, ...).
- **`build.gradle.kts` fixes for the `integration` task.**
  - `path.to.build.plugin` is resolved lazily. Before, it pointed at an empty `flutter-intellij.zip` instead of
    `Flutter.zip`, so the plugin never loaded in any integration test.
  - `-Djava.awt.headless=true` on the test JVM. Otherwise a Wayland keyboard thread gets reported as a leaked
    thread and fails the test.
- **Robust UI actions.** Ctrl+Alt+W didn't open Extract Widget in 2026.2, so the test invokes the action with the
  editor as context. Device selection uses the popup's speed search and presses Escape when nothing matches, so
  text can't land in the editor. A running Android emulator is listed under its AVD name ("Medium Phone (mobile)").
  `waitForIndicators` can hit stale references while the frame rebuilds, so it is retried.
- **Walkthrough content.**
  - A test summary at the top, taken from the KDoc.
  - Per-step "What to look for", from the step announcements.
  - Screenshots flagged as possibly not useful (splash screen, IDE still drawing, trial promo tab, license screen,
    near-duplicates). They are highlighted, never removed automatically.
- **Walkthroughs are saved to a user-chosen folder, default `~/walkthroughs`.** The skill asks for it first, so
  recordings survive `./gradlew clean`, never get committed, and aren't tied to an agent conversation.

## IntelliJ licensing (non-deterministic)

On some runs IntelliJ auto-starts a 30-day trial (it logs `Starting a free trial automatically` and opens a "Trial"
promotion tab, which gets flagged). On others it stops at the license screen (it logs
`#LicenseManager - License required`), which blocks the IDE. The recorder detects the latter, prints
`LICENSE SCREEN DETECTED`, writes `license-screen.txt`, and shows a red warning in the walkthrough. The user then
has to clear it in IntelliJ themselves: run `./gradlew runTarget -Pide=Ultimate -PideV=2026.2.3`, sign in, start
the trial, activate a license, or choose free, then record again. Agents must not try to click through it.

## Environment notes (this machine)

- Flutter SDK: `~/develop/flutter` (3.44.7). Export `FLUTTER_SDK` for test runs.
- Android emulator: AVD `Medium_Phone` (API 37, 16 KB page-size image). Always stop it with `adb emu kill`: an
  unclean exit makes the next boot wait on a crash-report dialog. The test passes
  `-crash-report-mode disabled -no-metrics` to prevent that.
- Chrome is at `/usr/bin/google-chrome` (used by the Chrome variant and for checking rendered walkthroughs).
- Desktop session is Wayland, so use the recorder's `--mode visible` to watch tests live.

## Status (2026-10-22, Windows)

- The work continues on the `cross-platform-ui-tests` branch (pushed to the `cj-radcliff/flutter-intellij-working`
  fork), which makes the tests portable (`utils/Platform.kt`).
- `PubGetUITest` passes on Windows 11 (`.\gradlew.bat --no-daemon integration --tests ...PubGetUITest`), with no
  Windows-specific changes beyond `Platform.kt`. It now adds `http: ^1.2.0` and checks `pubspec.lock`,
  `package_config.json`, and the pub cache. The other tests have not been run on Windows yet.
- Decision: **walkthrough recording stays Linux only** for now. Windows (and macOS) run the tests but don't record.
  The details for a later port are in
  [Follow-up: recording on Windows and macOS](#follow-up-recording-on-windows-and-macos).

## Status (2026-10-20)

- All six tests pass headless and visible. `RunAppAndHotReloadUITest` also passes on the Android emulator (visible).
- The latest full set of 13 walkthroughs (with step pauses, flags, and license checks) is in `~/walkthroughs/`.
  The license screen did not appear in any of those runs.
- Next focus: `RunWidgetTestUITest`, one test at a time. Open options:
  - Trim or collapse the 8 startup screenshots, 6 of which are flagged.
  - Show more of the test runner (test tree, output).
  - Use the gutter run icon instead of Ctrl+Shift+F10, add a failing test, or run in debug mode.

## Working rules for this branch

- Local only: don't push, don't send for review.
- Keep `integration-test-iu262` (and `external-ide-action-group-bgt`) rebased on `main`:
  `git switch integration-test-iu262 && git rebase main`. When `main` moves forward (for example after syncing the
  fork with `upstream/main`), rebase again.
- Commit with descriptive messages; validate the skill with `tool/validate_skills.sh` (needs `dart` on `PATH`) and
  compile tests with `./gradlew --no-daemon compileIntegrationKotlin`.

## History

The work started in `~/repos/flutter-intellij` on 2026-10-15 and moved here on 2026-10-20. Both branches were
fetched into this repo, rebased onto `main`, and deleted from the old repo, together with the downloaded test IDE
(`build/out/ide-tests/`).

## Follow-up: recording on Windows and macOS

Not started; recording is Linux only for now (decided 2026-10-22). Notes for picking this up later:

- **Why the recorder is Linux only today.**
  - `record_walkthrough.sh` starts Xvfb or Xephyr and points `DISPLAY` at it.
  - `capture_display.sh` grabs that display with ImageMagick `import`, which only works on X11.
  - `build_walkthrough.sh` picks key frames, trims, and flags screenshots with ImageMagick `compare`, `convert`, and
    `identify`, all driven from bash.
- **Already portable, keep as is.** `render_walkthrough.py`, `extract_test_summary.py`, `build_index.py`,
  `WalkthroughSteps` (`WALKTHROUGH_STEPS_FILE`, `WALKTHROUGH_STEP_PAUSE_MS`), and the step announcements and KDoc in
  the tests.
- **Platform limit.** Windows and macOS have no equivalent of Xvfb that accepts `java.awt.Robot` input, so only a
  "visible" mode is possible there. The IDE runs on the real, unlocked desktop, and the user can't use the machine
  while it records. A VM, Windows Sandbox, or an RDP session could make it unattended, at the cost of extra setup.
- **Preferred approach: port the three shell scripts to one Python recorder** using Pillow (frame diff, trim, the
  flag rules) and `mss` (screen capture).
  - Windows and macOS: capture the real desktop.
  - Linux: keep starting Xvfb or Xephyr as today, so Linux recordings don't change.
  - Run Gradle with `gradlew.bat` on Windows.
  - Output stays the same: `walkthrough.html`, `walkthrough.md`, `flags.txt`, and so on.
- **Alternatives considered.**
  - Capture inside the test JVM through the Driver. One code path for all OSes, but the test JVM runs with
    `-Djava.awt.headless=true`, so the capture would have to happen in the IDE process.
  - A PowerShell port with `System.Drawing`. Windows only.
- **Main risk.** Matching today's key-frame threshold (`--min-percent 0.5`) and the flag heuristics (splash screen,
  gray-level standard deviation below 0.045, trial tab, near-duplicates) closely enough. Check against an existing
  Linux recording.
- **Windows machine notes (2026-10-22).** Python 3.14 is installed, but not Pillow or `mss`
  (`pip install pillow mss`). ImageMagick is not installed. Start with `PubGetUITest`, which is known to pass there.
- **Docs to update when done.** `testSrc/integration/README.md` ("Linux only" notes) and the skill's prerequisites
  and description.
