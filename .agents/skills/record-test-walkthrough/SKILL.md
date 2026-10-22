---
name: record-test-walkthrough
description: Write IntelliJ IDE Starter UI tests for the Flutter plugin (for example from TestTracker test cases) and record any automated UI test as a step-by-step screenshot walkthrough, running headless under Xvfb, visibly in a Xephyr window, or on the Wayland desktop, saved as a self-contained HTML page and Markdown.
---

# Skill: Record a Test Walkthrough

Use this skill to turn an automated UI test into a browsable, step-by-step walkthrough, and to write new UI tests for
the Flutter plugin (see [Writing a test from a TestTracker test case](#writing-a-test-from-a-testtracker-test-case)).
The automated test is what runs; the recorder only provides a display for it and records that display.

- **The test** drives the UI, for example an IDE Starter / Driver test in `testSrc/integration`.
  `NewProjectIU262UITest` (creates a Flutter app in IntelliJ IDEA 2026.2.3) is one example.
- **The recorder** ([`record_walkthrough.sh`](scripts/record_walkthrough.sh)) starts its own X display,
  runs the test with `DISPLAY` pointing at it, captures the display, and builds the walkthrough.

## Prerequisites

- Linux with `xvfb` (headless), `xserver-xephyr` (visible), `x11-utils` (`xdpyinfo`), `imagemagick` (`import`, `convert`, `compare`) and `python3`.
- An automated test that shows UI on `$DISPLAY`, with a doc comment that explains what it shows (see
  [Test summary](#test-summary)). For IDE Starter tests:
  - The test must run in a fresh JVM with the environment passed through. `--test` uses `./gradlew --no-daemon`.
  - IDE Starter launches the IDE on `$DISPLAY` when it is set, and only falls back to its own `xvfb-run` when it is not.
  - Flutter tests need `FLUTTER_SDK`. The recorder defaults it to the SDK of `flutter` on `PATH`.
- The first IDE Starter run downloads the IDE into `build/out/ide-tests/`.

## Workflow

### 1. Ask where to save the walkthrough

Before recording, ask the user which folder to save walkthroughs in, and suggest `~/walkthroughs` as the default
(or `$WALKTHROUGH_DIR` if it's set). Use the default whenever the user doesn't name another folder. Each run is saved
in its own subfolder, `<folder>/<test>-<timestamp>/`. Pass the answer with `--dest <folder>`, or omit `--dest` to
use the default. The default is outside the repository, so walkthroughs survive `./gradlew clean`, never end up
in a commit, and are easy to find later.

### 2. Choose the test and mode

| Mode | Display | Use when |
|---|---|---|
| `headless` (default) | Xvfb, nothing on screen | Unattended runs, CI-like recordings, working in parallel |
| `visible` | Xephyr window on your desktop | Watching the test live or debugging it. Don't click inside the window while it runs. |

Both modes are real X servers, so tests that send input through `java.awt.Robot` work even on a Wayland
desktop.

To watch a test directly on a Wayland desktop instead (no recording), run it from a desktop terminal, where
`DISPLAY` and `WAYLAND_DISPLAY` are both set:

```bash
FLUTTER_SDK=~/develop/flutter ./gradlew --no-daemon integration --tests io.flutter.integrationTest.ColorGutterPreviewUITest
```

The IDE then runs on XWayland, which only accepts synthetic input through the xdg-desktop-portal RemoteDesktop
interface. `Setup.kt` detects this (`isWaylandDesktop()`) and passes `-Dawt.robot.screenshotMethod=dbusRemoteDesktop`,
so `java.awt.Robot` uses the portal. GNOME asks once to allow remote interaction; the JDK keeps a restore token in
`~/.java/robot/`. The test moves the real pointer, so don't touch the mouse or keyboard. Not yet verified on Cory's
desktop. The recorder can't capture this mode; it always uses its own display.

### 3. Record

From the repository root:

```bash
# A Gradle test (default task: integration)
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh \
  --test io.flutter.integrationTest.NewProjectIU262UITest

# Save into a folder the user chose instead of ~/walkthroughs
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh --dest ~/Documents/flutter-walkthroughs \
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
| `--dest <folder>` | Folder to save walkthroughs in; each run gets `<folder>/<test>-<timestamp>/` (default `$WALKTHROUGH_DIR`, else `~/walkthroughs`) |
| `--out <dir>` | Exact output directory for this run (overrides `--dest`) |
| `--title <text>` | Walkthrough title (default `<test> walkthrough`) |
| `--summary <text\|@file>` | What the test shows, in Markdown (default for `--test`: the test class's doc comment) |
| `--size <WxH>` | Screen size (default `1920x1080`). Use a smaller size in visible mode if the window doesn't fit your screen. |
| `--min-percent <n>` | Key-frame change threshold (default `0.5`) |
| `--step-pause <seconds>` | Pause after each announced test step so its result is captured, 0 to 5 (default `3`) |
| `--ide-log <path>` | IDE log to scan for license and trial events (default: the newest IDE Starter `idea.log`) |

The recorder exits with the test's exit status. It:

1. Starts Xvfb or Xephyr on a free display (`:90` and up).
2. Starts [`capture_display.sh`](scripts/capture_display.sh), which samples the display every 0.5 s and saves a PNG whenever it changes.
3. Runs the test with `DISPLAY` set to that display and `WAYLAND_DISPLAY` unset, teeing output to `test.log`. Tests that announce steps pause `--step-pause` seconds after each one, so its result stays on screen.
   Then the recorder scans the IDE log for license and trial events (see [License screen](#license-screen)).
4. Writes `summary.md` from `--summary` or, for `--test`, from the test class's doc comment ([`extract_test_summary.py`](scripts/extract_test_summary.py)).
5. Runs [`build_walkthrough.sh`](scripts/build_walkthrough.sh), which skips blank frames, keeps frames where more than 0.5% of the pixels changed, and trims the black background. If the test announced its steps (see [Per-step expectations](#per-step-expectations)), each screenshot is captioned with the step it belongs to and what to look for, and every step keeps at least one screenshot.
6. Runs [`render_walkthrough.py`](scripts/render_walkthrough.py) to produce the browsable files.
7. Refreshes `index.html` in the parent folder ([`build_index.py`](scripts/build_index.py)), which lists every walkthrough there, newest first, with its title, summary, and date.

### 4. Outputs

| File | Contents |
|---|---|
| `walkthrough.html` | **Self-contained page for browsing later.** Images are embedded, so the single file can be copied, archived, or attached. Starts with a "What this test shows" panel (toggle with `S`), then a slideshow with Prev/Next (arrow keys, Home/End), thumbnails, and a "Show all steps" view (`A`). |
| `walkthrough.md` | Standard Markdown: a "What this test shows" section, then per step a heading, a "What to look for" line, and the image, with relative image links |
| `summary.md` | What the test shows, the source for the summary in both files above |
| `captions.txt` | One line per screenshot, `Caption \| What to look for`, the source for the step captions and expectations in both files above |
| `steps.tsv` | Steps the test announced, with timestamps (only for tests that use `WalkthroughSteps`) |
| `flags.txt` | Screenshots that are probably not useful (`<step number><TAB><reason>`); they are highlighted, not removed |
| `ide-events.tsv` | License-screen and trial-tab events from the IDE log, with timestamps |
| `license-screen.txt` | Present only if IntelliJ showed its license screen; the warning and instructions for the user |
| `step_NN_HHMMSS.png` | Trimmed key frames |
| `frames/` | Every distinct captured frame |
| `test.log`, `capture.log`, `xserver.log` | Raw logs |

### 5. Verify

- **If the recorder printed `LICENSE SCREEN DETECTED`** (or `license-screen.txt` exists), stop and tell the user. See
  [License screen](#license-screen). Don't retry the recording until the user has cleared the license screen.
- `Test exit status: 0`, plus the test's own success output in `test.log` (for Gradle: `BUILD SUCCESSFUL`).
- The test's `println` output (for example `Gutter colors: ...`) is not in `test.log`; Gradle puts it in
  `build/test-results/integration/TEST-<fqcn>.xml`, together with the failure and stack trace. The IDE's log is
  `build/out/ide-tests/tests/<IDE build>/<TestClass>/log/idea.log`.
- If the test failed, look at the last screenshots first: a dialog or popup that the test didn't expect is the most
  common cause (see [IntelliJ 2026.2 behavior](#intellij-20262-behavior-to-plan-for)).
- Review the screenshots marked **Possibly not useful** (`flags.txt`, amber outline in `walkthrough.html`) and tell
  the user how many there are. They are kept on purpose; only remove them if the user asks.
- `capture.log` reports a frame count. If it's zero, the UI never appeared on the recorder's display. Check that the test honors `DISPLAY` and runs in a fresh process (see Troubleshooting).

### 6. Summary and captions

Check that `summary.md` describes the flow; if it's missing, add a doc comment to the test (see
[Test summary](#test-summary)) or write `summary.md` by hand. For tests that announce their steps,
captions and "What to look for" notes are filled in automatically. Otherwise captions are capture times: view each
`step_*.png` and replace the matching lines in `captions.txt` (one line per screenshot, in order, as
`Caption | What to look for`; lines starting with `#` are ignored). Then re-render:

```bash
python3 .agents/skills/record-test-walkthrough/scripts/render_walkthrough.py <walkthrough-dir>
```

Use `--title` to change the title. To drop a near-duplicate step, delete its `step_*.png` and its
caption line. Fast steps can fall between samples; pick them from `frames/`, or rerun
`build_walkthrough.sh <frames-dir> <walkthrough-dir> <lower-percent>` (this resets `captions.txt`).

### 7. Present

- To browse later, open `<folder>/index.html` (for example `xdg-open ~/walkthroughs/index.html`) and pick a walkthrough, or open a run's `walkthrough.html` directly.
- To share as Markdown, send `walkthrough.md` together with the `step_*.png` files.
- Tools that need absolute image paths (for example, agent artifact viewers) need the images copied next to the artifact and the paths rewritten.

## Test summary

Every walkthrough starts with a "What this test shows" section, taken from the test class's doc comment (KDoc or
Javadoc) up to a line starting with `Implementation notes:`. Write that comment for someone watching the
walkthrough, using this shape:

```kotlin
/**
 * **Shows:** <one sentence: the feature or flow this test demonstrates>.
 *
 * Steps:
 * 1. <starting state, e.g. "IntelliJ IDEA opens an existing Flutter app (created with `flutter create`).">
 * 2. <each user-visible action, naming the UI elements and plugin actions involved>
 *
 * Expected result: <what the viewer should see at the end>.
 *
 * Implementation notes:
 * <environment variables, setup, cleanup: not shown in the walkthrough>
 */
class MyFlowUITest : ExistingProjectUITest("my_flow") { ... }
```

Supported Markdown: paragraphs, `-` and `1.` lists (indent continuation lines), `` `code` `` and `**bold**`.
The summary is found in untracked files too, so a new test gets it before it's committed. Put any `// TODO` comment
*above* the doc comment: a line comment between the doc comment and `class` hides the summary.
To add or refresh the summary of an existing walkthrough without re-recording it:

```bash
S=.agents/skills/record-test-walkthrough/scripts
python3 $S/extract_test_summary.py io.flutter.integrationTest.MyFlowUITest > <walkthrough-dir>/summary.md
python3 $S/render_walkthrough.py <walkthrough-dir>
```

## Screenshots flagged as possibly not useful

`build_walkthrough.sh` marks screenshots that probably don't help the viewer, without removing them. Each flag has
a reason, which is shown in the walkthrough:

| Reason | How it's detected |
|---|---|
| IntelliJ's splash screen while the IDE starts | Small window that is mostly strongly saturated color |
| The IDE is still drawing its window | Very little detail (gray-level standard deviation below 0.045) |
| IntelliJ's trial promotion tab is covering the editor | The IDE log reported the Trial tab opening, and the frame is colorful, within that step |
| IntelliJ's license screen may be showing | Captured after the IDE log reported the license screen, before the next step |
| Almost identical to the previous screenshot | Less than `--min-percent` of pixels changed since the previous screenshot of the same step |
| Captured before the test's first step | No step had been announced yet |

In `walkthrough.html`, flagged screenshots have an amber outline, a "Possibly not useful" banner, and a dashed
thumbnail. The header shows how many are flagged. In `walkthrough.md`, they get a "Possibly not useful" note.

## License screen

IntelliJ IDEA's licensing is not deterministic. On some runs it starts a free trial automatically (and opens a
"Trial" promotion tab, which is flagged). On others it stops at its license screen, which blocks the IDE until
someone deals with it. A test cannot handle this reliably, so the recorder detects it instead: if the IDE log
contains `#LicenseManager - License required` or `Trial was not started`, the recorder prints
`LICENSE SCREEN DETECTED`, writes `license-screen.txt`, and the walkthrough shows a red warning at the top.

When this happens, tell the user clearly that they need to clear the license screen in IntelliJ themselves:

1. Start the same IntelliJ IDEA version, for example `./gradlew runTarget -Pide=Ultimate -PideV=2026.2.3`.
2. On the license screen, sign in with a JetBrains account and start the trial, activate a license, or choose the
   free option if offered, until IntelliJ opens normally.
3. Close IntelliJ, then record the walkthrough again.

Do not try to click through the license screen from the test or the agent.

## Per-step expectations

Each screenshot in a walkthrough can say what the viewer should look for. Tests provide this by announcing each
user-visible step as it starts, with `WalkthroughSteps.step(title, expect)` from
`testSrc/integration/io/flutter/integrationTest/utils/WalkthroughSteps.kt` (or `step(...)` in subclasses of
`ExistingProjectUITest`):

```kotlin
step("Run Extract Flutter Widget", "The \"Extract Widget\" dialog opens with the name field showing `NewWidget` selected.")
driver.invokeAction("Flutter.ExtractWidget", now = false, component = editor.component)
```

- `title` becomes the screenshot caption. Use a short imperative, e.g. "Run the app".
- `expect` says what should be visible during or right after the step. Name concrete UI text, files, and code.
- Call `step` immediately before the action, so screenshots taken while it runs are attributed to it.
- While recording, each `step` call first waits `--step-pause` seconds (default 3, at most 5), and so does
  `WalkthroughSteps.finish()` at the end of the test, so the previous step's result is captured.

The recorder sets `WALKTHROUGH_STEPS_FILE`, and each step is appended there with a timestamp (`steps.tsv`). When the
walkthrough is built, each screenshot gets the most recent step at its capture time. If a step has no key frame, the
last screenshot captured during it is added. Outside the recorder, steps are only printed.

## Writing a test from a TestTracker test case

The Flutter plugin's manual test plan lives in TestTracker (for example plan `157725`, "Flutter vNN Full Test Plan").
To automate one test case:

1. **Read the test case.** TestTracker pages need SSO. Fetch them with `sso_client` (outside the agent sandbox, since
   it needs network and auth), then strip the HTML:

   ```bash
   sso_client --url 'https://default-dot-testtracker.googleplex.com/testplans/testcase/detail/<case>?id=<plan>&revision=1' > /tmp/case.html
   ```

   The useful parts are the title (for example "1.2.4 - Color Gutter Previews"), Pre-Condition, Procedure,
   Verification, and the Testcase History (which IDEs and OSes it's run on).
2. **Find the plugin code behind it** (`plugin.xml` extensions, tool window IDs, action IDs) and summarize for Cory how
   the case maps to the plugin and what the test can check, before writing it.
3. **Write the test** in `testSrc/integration/io/flutter/integrationTest/<Feature>UITest.kt`, extending
   `ExistingProjectUITest` unless the case is about creating projects. Name the TestTracker case in the doc comment's
   **Shows:** line, and turn the Verification into assertions the Driver can check (see below). Announce each
   user-visible step with `step(...)`.
4. **Compile, then record** (see [Running from an agent](#running-from-an-agent)), and fix what the screenshots show.
5. **Leave a `// TODO(radcliffc):` above the doc comment** for anything the test can't verify yet, or plugin bugs it
   found, so the gap stays visible.

### Building blocks

| Helper | What it gives you |
|---|---|
| `ExistingProjectUITest` | Creates the project with `flutter create`, starts the IDE (`startIdeOnProject()`), waits for indexing, and deletes the project afterwards. Also: `openProjectFile`, `inFrame { }`, `editorFor`, `allEditorText`, `waitForConsoleText`, `waitUntil` (dismisses IDE interruption dialogs while polling), `selectDevice`, `stopApp`, `context` (IDE Starter context) and `ideLogFile` (this run's `idea.log`). Override `prepareProject()` to change the project or boot a device before the IDE opens. |
| `utils/AndroidEmulator` | `start(logDir)` boots the AVD from `FLUTTER_TEST_AVD` (default `Medium_Phone`) on the test's display; `stop()` uses `adb emu kill`; `deviceSelectorNames` for `selectDevice`. |
| `utils/BrowserEngine` | `FLUTTER_TEST_BROWSER=auto\|jxbrowser\|jcef` picks the embedded browser for Inspector/DevTools windows; `applyTo(driver)` flips the plugin's "Enable JCEF browser" setting in the running IDE. `pluginHasJxBrowserLicense()` checks the plugin under test. |
| `utils/GutterColors` | `editor.gutterColors()` returns the color preview icons in an editor's gutter as `line -> [0xRRGGBB]`. |
| `utils/WalkthroughSteps` | `step(title, expect)` for walkthrough captions; see [Per-step expectations](#per-step-expectations). |

| Test | TestTracker case | Device |
|---|---|---|
| `DevToolsInspectorUITest` | 1.2.2 DevTools & Inspector | Android emulator (default) or Chrome |
| `ColorGutterPreviewUITest` | 1.2.4 Color Gutter Previews | none |

### Checking results with the Driver

Prefer checks on the IDE's real state over "something appeared on screen":

- **UI text and components:** XPath on the Swing tree, for example
  `x("//div[contains(@visible_text, 'test passed')]")`, or `window.x("//div[contains(@javaclass, 'com.teamdev.jxbrowser')]")`
  inside `toolWindow("Flutter Inspector")`. `getAllTexts()` returns the text a component shows, including HTML labels.
- **IDE objects through `@Remote` interfaces:** declare an interface for the IDE class you need (methods with the same
  names and parameter counts) and get it with `driver.service(...)`, `driver.utility(...)`, or `driver.cast(ref, ...)`.
  `GutterColors.kt` reads `EditorEx.getGutterComponentEx().getGutterRenderers(line)` and each icon's `ColorIcon` color;
  `BrowserEngine.kt` changes a setting through `PropertiesComponent.updateValue(...)`. Make the interfaces `internal`,
  not `private`, and run editor and UI reads in `driver.withContext(OnDispatcher.EDT) { ... }`. Look up exact method
  signatures in the downloaded IDE (`javap -cp build/out/ide-tests/cache/builds/<IDE>/lib/<jar> <class>`).
- **The IDE log:** read `ideLogFile` at the end of the test, for example to fail on exceptions from `io.flutter`.
- **Things the Driver can't see**, such as the page inside an embedded browser, are left to the walkthrough
  screenshots; say so in the doc comment.

### IntelliJ 2026.2 behavior to plan for

| Behavior | What to do |
|---|---|
| The first Ctrl+Y opens a "Map Action to Ctrl+Y: Redo or Delete Line?" dialog, which then swallows typed text | Invoke the action instead: `driver.invokeAction("EditorDeleteLine", component = editor.component)` |
| Some shortcuts don't reach the action (Ctrl+Alt+W for Extract Widget) | `driver.invokeAction("<action id>", component = editor.component)`; action IDs are in `plugin.xml` |
| Clicking a tool window's button hides it if it's already open | Check `stripeButton(id).isToolWindowVisible()` first; buttons are on `ToolWindowRightToolbar` / `ToolWindowLeftToolbar` |
| Typing in the editor opens completion popups | Press Escape before the next action; don't press Enter while a popup may be open |
| Local plugin builds have no JxBrowser license key, so Inspector/DevTools windows show "The JxBrowser license could not be found." | Use `BrowserEngine`; with a license, put it in the gitignored `resources/jxbrowser/jxbrowser.properties` before building |
| JCEF mode fails with `NoClassDefFoundError: com/intellij/ui/jcef/JBCefBrowser` | Known plugin bug: no dependency on `com.intellij.modules.jcef` |
| `final c = Colors.blue;` gets no gutter color preview (`Colors.x.shadeN`, `Color(...)` do) | Known plugin issue in `FlutterColorProvider`; use colors in lists or widget arguments |
| "Input methods disabler" and "Flutter supports hot reload!" notifications, the Trial tab, and the emulator's "nested virtualization" dialog show up in screenshots | Harmless; they don't block input |

## Running from an agent

The agent's command sandbox has no network, can't write outside the workspace (including `~/walkthroughs`), and can't
write `.git`. So:

- **Compile in the sandbox** (fast check, no network, no Kotlin daemon):

  ```bash
  ./gradlew --no-daemon --offline -Pkotlin.compiler.execution.strategy=in-process compileIntegrationKotlin -q
  ```

- **Record outside the sandbox** (`BypassSandbox: true`): the recorder downloads dependencies, writes to
  `~/walkthroughs`, starts emulators, and runs `flutter`. Set `FLUTTER_SDK` and put its `bin` on `PATH`, redirect the
  output to a file, and run it in the background; a run takes 3 to 10 minutes.
- **`Timeout waiting to lock journal cache`** means another Gradle build (for example in another worktree) holds the
  Gradle cache. Wait, or run the recording outside the sandbox, which compiles too.
- **Committing** also needs `BypassSandbox: true`. Commit only the test, helper, and skill files, not `.idea/*`,
  `gradle.properties`, or `local.properties`.
- **Validate the skill** after editing it with `PATH=~/develop/flutter/bin:$PATH tool/validate_skills.sh`, also
  outside the sandbox (Flutter writes to its own cache).

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
| Test clicks do nothing when run on the Wayland desktop | The RemoteDesktop permission wasn't granted (GNOME asks on first use), or `DISPLAY`/`WAYLAND_DISPLAY` weren't both set, so the portal wasn't used. Or record with `--mode visible` instead. |
| IDE Starter test loads Dart but not Flutter | The `integration` task installed an empty `flutter-intellij.zip`. It must resolve `buildPlugin.archiveFile` lazily so that the `Flutter.zip` name applies. |
| `Plugin 'Dart' ... requires IDE build 261.* or older` | The Dart plugin version doesn't support the IDE build. |
| `Thread leaked: WLKeyboard.KeyRepeatManager` | The test JVM started the Wayland AWT toolkit. The `integration` task passes `-Djava.awt.headless=true`. |
| `DriverIllegalStateException: No such session` | IDEA rebuilt frame components; `NewProjectIU262UITest` retries its final wait. Rerun if it persists. |
| Timeout waiting for the welcome screen | A license or trial dialog is blocking startup. Check `frames/`. |
| Xephyr window is larger than the screen | Pass a smaller `--size`, for example `1600x900`. |
| `WaitForException: Timeout` in a tool window check | The failure message includes the window's last text (for example "Installing DevTools..."); then check `idea.log` for the exception behind it. |
| `IndexOutOfBoundsException: Wrong offset: -1` from the Driver | The test looked up text that isn't in the editor (an edit went somewhere else, often into a dialog). Check the screenshots. |
| The walkthrough has no "What this test shows" | The test class has no doc comment, or a line comment sits between the doc comment and `class`. |

## Notes

- Walkthroughs are saved outside the repository (`~/walkthroughs` by default), so they are never committed.
- The UI tests in `testSrc/integration` are experimental (see `testSrc/integration/README.md`) and are not run in CI.
