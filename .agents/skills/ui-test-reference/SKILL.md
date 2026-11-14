---
name: ui-test-reference
description: Reference for writing and debugging IntelliJ IDE Starter + Driver UI tests for the Flutter and Dart plugins. Covers how Starter launches the IDE and installs plugins, how Driver calls IDE services and finds Swing components, our harness base classes and helpers (FlutterIdeUITest, ExistingProjectUITest, step, assertOracle, probe, consoleText, invokeFlutterAction, overlays), platform differences on Linux, macOS, and Windows, what Driver cannot see, and the pitfalls already hit. Use whenever writing, repairing, or reading a test under testSrc/integration. Not a workflow skill; it's the manual the agent-testing-* skills and record-test-walkthrough point to.
---

# UI Test Reference: Starter, Driver, and the Flutter harness

The UI tests in [`testSrc/integration`](../../../testSrc/integration/README.md)
run a real IDE in a separate process and drive it from the test JVM. Three
layers are involved, and most mistakes come from confusing them:

| Layer | What it is | Where |
|---|---|---|
| **IDE Starter** | JetBrains' framework that downloads an IDE build and plugins, prepares a project, launches the IDE, and collects logs | [`Setup.kt`](../../../testSrc/integration/io/flutter/integrationTest/Setup.kt) |
| **Driver** | JetBrains' remote-call API from the test JVM into the running IDE: services via `@Remote` interfaces, UI via XPath-like locators | used throughout the tests |
| **Our harness** | Base classes and helpers that encode Flutter-plugin workflows and the certification contract | [`FlutterIdeUITest.kt`](../../../testSrc/integration/io/flutter/integrationTest/FlutterIdeUITest.kt), [`ExistingProjectUITest.kt`](../../../testSrc/integration/io/flutter/integrationTest/ExistingProjectUITest.kt), [`utils/RunResult.kt`](../../../testSrc/integration/io/flutter/integrationTest/utils/RunResult.kt), [`utils/IdeLog.kt`](../../../testSrc/integration/io/flutter/integrationTest/utils/IdeLog.kt) |

Running: `FLUTTER_SDK=<sdk> ./gradlew --no-daemon integration --tests <fqcn>`.
One run is about 1.5 minutes for a test that only opens a project, 3–4 minutes
for one that runs an app. The first run downloads the IDE into
`build/out/ide-tests/`.

## 1. Starter

**Contexts.** `Setup.setupTestContextIU262(testName, projectSpec)` is what the
harness calls. It builds a `TestCase` for IntelliJ IDEA (`IdeProductProvider.IU`)
at `Setup.ideBuildNumber`, which is `-Pide.version=<build>` when given and
`Setup.DEFAULT_IU_BUILD` otherwise. Only build numbers (`262.10968.63`) are
accepted; `latest` and version strings aren't resolved yet. Since 2025.3 the
unified IntelliJ IDEA distribution is product code `IU`.

**Plugins.** Starter installs plugins into a fresh sandbox per test:

| What | How | Used for |
|---|---|---|
| The Flutter plugin | `path.to.build.plugin` (the zip `buildPlugin` just made), overridden by `-Pflutter.plugin.zip=<zip>` | certification red builds come in through the override |
| No Flutter plugin | `FLUTTER_UI_TEST_WITHOUT_PLUGIN=true` | the control run: every UI test must fail on its own checks |
| The Dart plugin | Marketplace version from `dartPluginVersion` in `gradle.properties`, overridden by `DART_PLUGIN_ZIP=<zip>` | Dart-seam red builds, and testing a Dart plugin PR |

**VM options.** `Setup.setupTestContext` sets the properties that keep the IDE
quiet and drivable: trust all projects, no consent/tips/surveys,
`expose.ui.hierarchy.url` (lets Driver and the probe read the component tree),
new UI, OS-specific settings (below).

**Where things land.** Per test, under `build/out/ide-tests/tests/<IDE>/<TestClass>/`:
`log/idea.log`, `log/screenshots/` (Starter saves one on every Driver error),
`log/threadDumps-freeze-*` (the IDE's own freeze detector),
`config/`, `system/`. The harness writes `build/agent-testing/run-result.json`
and probes under `build/agent-testing/probes/`. Gradle's test report is
`build/test-results/integration/*.xml` and contains the test's stdout.

**Timeouts mean nothing.** Starter's waits (`waitFor`, `waitForIndicators`)
time out with a `WaitForException`; a bare timeout tells you the condition
never became true, not why. Read `idea.log` and the last screenshot.

## 2. Driver

Driver is a client for a server inside the IDE. `run.driver` is the handle.

**Calling IDE code.** Declare an interface annotated with the IDE class's
fully qualified name and call it like the real thing:

```kotlin
@Remote("com.intellij.ide.util.PropertiesComponent")
interface PropertiesComponentRef { fun getValue(name: String): String? }

driver.service(PropertiesComponentRef::class).getValue("key")   // application service
driver.utility(SomeStaticHolderRef::class).staticMethod()       // static methods
driver.new(SomeClassRef::class, arg1, arg2)                     // constructors
```

The SDK ships ready-made ones: `driver.isPluginLoaded(id)`, `driver.getPlugin(id)`
(`PluginDescriptor` with `getVersion()`, `isEnabled()`), `driver.service(ActionManager::class)`
(`getAction(id)`, `tryToExecute(...)`), `driver.openFile(path)`,
`driver.waitForIndicators(timeout)`, `driver.invokeAction(id, now, component)`,
`driver.takeScreenshot(name)`.

Wrap UI work in `driver.withContext { ideFrame { ... } }` (the harness's
`inFrame { }`); that is what makes `x()` searches run against the IDE's
component tree.

**Finding UI.** Components are addressed with an XPath-like query over the
Swing tree, where every component is a `div`:

```kotlin
x("//div[@class='ActionButton' and @myicon='stop.svg']")        // exactly one match, or an error
xx("//div[@class='EditorComponentImpl']").list()                // any number
x("//div[@title='New Project']", NewProjectDialogUI::class.java) // typed wrapper
```

Attributes you can match on: `class` (simple class name), `javaclass` (fully
qualified, use `contains(@javaclass, 'DeviceSelectorAction')` for inner
classes), `name`, `accessiblename`, `text`, `visible_text`, `title`, `myicon`
(icon file name, the best handle for toolbar buttons), `myaction`, `enabled`,
`visible`. Combine with `and`/`or`, nest with `[.//div[...]]`.

What a `UiComponent` can do: `click()`, `doubleClick()`, `rightClick()`,
`isEnabled()`, `present()`/`notPresent()`, `hasText()`, `getAllTexts()`,
`waitFound(timeout)`, `keyboard { typeText(..); enter(); escape() }`. Typed
wrappers (`IdeaFrameUI`, `JEditorUiComponent` with `.text`, `projectView { projectViewTree.pathExists(...) }`,
`welcomeScreen { }`) add the component-specific operations; prefer them over
raw locators when they exist.

**What Driver cannot see or do**, and what to do instead:

| Can't | Instead |
|---|---|
| Inside a JCEF/JxBrowser page (DevTools). It sees the browser component only | run JavaScript in the page via `JBCefJSQuery` / `executeJavaScript` through a `@Remote`, and assert on the result |
| Native file dialogs | `Setup.kt` forces Swing dialogs on macOS; keep it that way |
| OS windows that aren't the IDE (Chrome, the emulator) | observe through the app (VM service) or the console, not the window |
| An action's `update()` when invoked bare | `invokeFlutterAction(id, component)` goes through `tryToExecute`, which runs `update()` in that context and rejects disabled actions |

## 3. Our harness

### Base classes

Pick the base from the spec's **Fixture**:

| Fixture | Base | Boots to |
|---|---|---|
| an existing project | `ExistingProjectUITest("package_name")` | a `flutter create` project, open and indexed |
| no project; the workflow creates one | the New Project base (extracted from `NewProjectUITest` when T2 is authored) | the welcome screen, then the wizard |
| no project at all | `FlutterIdeUITest` directly | the welcome screen |

All of them extend `FlutterIdeUITest`, which gives every test:

- `startIde(context) { it.runIdeWithDriver() }` — starts the IDE and runs the
  **plugin-loaded gate**: `io.flutter` and `Dart` must be loaded and enabled,
  or the test fails in `SETUP`.
- `step`, `assertOracle`, `probe` (below).
- Teardown: closes the IDE, then the **`idea.log` gate** (`IdeLog.severeEntries`:
  `SEVERE`/`ERROR` entries mentioning `io.flutter` or `com.jetbrains.lang.dart`,
  minus a short justified allowlist) and the **leaked-process check**
  (`flutter`/`dart` descendants still alive are killed and reported). Both fail
  the test, except when `AGENT_TESTING_MODE=red`, when they only record.
- `waitUntil(message) { }`, `dismissInterruptions()`, `closeTrialTab()`.

`ExistingProjectUITest` adds the project lifecycle (`createProject` in
`@BeforeAll`, `prepareProject()` hook for overlays, `deleteProject`),
`startIdeOnProject()`, `inFrame { }`, `openProjectFile(path)`, `editorFor(name)`,
the console helpers, `selectDevice(names...)`, and `stopAppAndVerifyTerminated()`.

### The certification helpers

These are **ours**, not framework functions. They record into `RunResult` and
the result is written to `build/agent-testing/run-result.json` when the test
ends; `parse_run_result.py` reads it.

```kotlin
step("Click Run next to the main.dart configuration") { invokeFlutterAction("Run") }
step("Click Flutter Hot Reload in the Run tool window toolbar", trigger = true) { ... }

assertOracle("O1", "console has no reload yet; app shows the old title") { ... }
assertOracle("O3", "console reports Reloaded N of M; app shows the new title") { ... }

probe("after opening the Run tool window")   // hierarchy + screenshot under build/agent-testing/probes/
```

- `step` titles are the spec's User interaction text; they become the
  walkthrough captions and the step names in the result. Mark the spec's
  trigger step with `trigger = true`.
- `assertOracle` ids are the spec's oracle parts. Put every predicate for a
  part inside its block and nowhere else; that's what lets the runner say
  "failed at O3 after all steps completed".
- A failure inside a block records the innermost assertion message (Starter
  wraps failures in `DriverWithContextError` with an ANSI banner; `RunResult.summarize`
  unwraps it).
- Phases in the result: `PREFLIGHT` → `SETUP` → `STEP` / `ORACLE` → `TEARDOWN`;
  `FREEZE` is reserved for the thread-dump oracle (not built yet).

### Observation helpers

- `consoleText(toolWindow = null, sinceOffset = 0)` reads **console editors
  only** (`ConsoleViewImpl` descendants), optionally scoped to a tool window by
  title, from `sinceOffset`. Record `consoleOffset()` before the trigger step
  and read after it. There is no `allEditorText()` any more; it matched source
  files.
- `waitForConsoleText(texts..., toolWindow, sinceOffset, failTexts, timeout)`
  waits for one of `texts` and throws as soon as any of `failTexts` appears,
  so an error is a failure, not a timeout.
- `editorFor("main.dart").text` for source editors.

### Action helpers

- `invokeFlutterAction(id, component?)`: `ActionManager.getAction(id)` (fails if
  unregistered) then `tryToExecute(action, null, component, place, now = true)`;
  a rejected callback fails the test with the action's error. Use it for every
  action a spec drives by ID. Pass the editor or tool-window component when the
  action's enablement depends on context.
- `selectDevice("Chrome", "Linux")`: opens the device selector, speed-searches,
  retries while the daemon is still listing devices.
- `stopAppAndVerifyTerminated()`: clicks Stop, waits for the Stop buttons to
  disable and the console to say the process finished. `stopApp()` is the
  best-effort form for cleanup only.
- `openProjectFile("lib/main.dart")`.

### Fixtures

`ExistingProjectUITest` runs `flutter create` into `Platform.testProjectsDir`
before the IDE starts. Override `prepareProject()` to copy an **overlay**
(`testData/integration/overlays/<name>/`) over it. `KEEP_TEST_PROJECT=true`
keeps the project; `KEEP_IDE_OPEN_MINUTES=n` keeps the IDE up after the test.

### Walkthroughs

`step` titles go to `WalkthroughSteps`, which the
[`record-test-walkthrough`](../record-test-walkthrough/SKILL.md) recorder reads
to caption screenshots (Linux only). The recorder pauses between steps when
`WALKTHROUGH_STEP_PAUSE_MS` is set; tests never sleep themselves.

## 4. Platforms

Everything runs on Linux, macOS, and Windows. `Platform.kt` holds the
differences: `flutterExecutable` (`flutter` vs. `flutter.bat`), `testProjectsDir`
(a short path on Windows because of the 260-character limit), `androidSdk`,
`adb`, `displayMode`, `deleteRecursively` with retries for Windows file locks.
Tests never check the OS themselves.

- **Linux.** `DISPLAY` unset → Starter runs its own `xvfb-run`. `DISPLAY` set
  without `WAYLAND_DISPLAY` → X11 (Xvfb/Xephyr from the recorder). Both set →
  the Wayland desktop through XWayland; input goes through the RemoteDesktop
  portal (`awt.robot.screenshotMethod=dbusRemoteDesktop`), which GNOME asks to
  allow once. For headless runs from a Wayland shell, clear both variables and
  set `XDG_SESSION_TYPE=x11`, or Chrome will open on the real desktop.
- **macOS.** A logged-in desktop session is required; `Setup.kt` turns off the
  native file chooser, sheet dialogs, and the screen menu bar so Driver can
  reach them.
- **Windows.** A logged-in desktop session; paths via `Platform`; expect
  file-lock retries on cleanup.
- **Linux-only:** Xvfb, Xephyr, the walkthrough recorder.

## 5. Pitfalls already hit

Product and platform findings (as opposed to harness pitfalls) live in
[`testSrc/integration/specs/FINDINGS.md`](../../../testSrc/integration/specs/FINDINGS.md); read it before
calling a failure a product finding, and append to it when you find one.

| Symptom | Cause | Rule |
|---|---|---|
| Every test "ran" but nothing Flutter-specific worked; failures were slow timeouts | `Flutter.zip` was empty; the plugin never loaded | plugin-loaded gate in `startIde` |
| 3-minute timeout waiting for the Inspector | JCEF `NoClassDefFoundError` logged as `SEVERE`, never read | `idea.log` gate in teardown |
| `waitForConsoleText("exit code 0")` matched before the run finished | the old helper read every editor, including `main.dart` | `consoleText` reads `ConsoleViewImpl` only |
| Hot reload test passed when the reload was a restart, or reloaded nothing | the oracle accepted `"Reloaded"` *or* `"Restarted"`, and `"Reloaded 0 of 600 libraries"` matched too | O4 negatives; match `Reloaded [1-9]\d* of \d+` and reject `Restarted` |
| Second run of a test hung on "device busy" | `flutter` daemon from the first run still alive | `stopAppAndVerifyTerminated`; leaked-process check |
| Chrome window never appeared in Xvfb screenshots | `XDG_SESSION_TYPE=wayland` made Chrome pick the Wayland socket | clear it / set `x11` for headless runs |
| Background tasks never finished; IDE looked idle | Trial tab, feedback survey, or Android Studio sign-in dialog was modal | `dismissInterruptions()` inside every wait |
| `x("//div[contains(@javaclass,'DeviceSelectorAction')]")` threw "more than one UiComponent" | the selector exists twice (main toolbar and run toolbar) | `xx(...).list()` when there may be several; `x()` only when exactly one |
| `run-result.json` showed an empty failure message | Starter's `DriverWithContextError` starts with an ANSI banner line | `RunResult.summarize` unwraps to the innermost assertion |
| `Stop` action rejected as disabled when invoked | the editor was the action context | click the Stop button, or pass the Run tool window as `component` |
| `consoleText()` was empty while the Run console clearly had output | `@class='ConsoleViewImpl'` matches the simple class name exactly; the Flutter run console is a `DaemonConsoleView` subclass | match subclasses with `contains(@javaclass, 'ConsoleView')` |
| `@Remote("io.flutter.…")` failed with `No such class … in plugin null` | plugin classes aren't on the platform classloader | `@Remote("io.flutter.utils.FlutterModuleUtils", plugin = "io.flutter")` |
| `tryToExecute` through a raw `@Remote` threw `InvocationTargetException` | it must run on the EDT | use `driver.invokeAction(...)` (the SDK does the EDT hop and the rejection check); `invokeFlutterAction` wraps it |
| A hand-launched IDE "with the fix" still showed the bug; both manual sandboxes had empty `log/` dirs | Starter leaves `<ide-home>.vmoptions` next to the cached IDE with `-Didea.{config,system,plugins,log}.path` pointing at the **last test's** sandbox; `-D` beats `idea.properties`/`IDEA_PROPERTIES`, so the IDE silently ran the test's (broken) plugin | launch by hand only through `tool/agent-testing/launch-ide.sh`, which passes its own vmoptions via `IDEA_VM_OPTIONS` and verifies paths, version, and jar sha256 from the IDE's log |
| A red run looked valid but nobody could say which plugin bytes the IDE had loaded | the broken and real builds were both `SNAPSHOT` | `build-broken-plugin.sh` stamps `-broken-<stamp>` into the version; the harness records `pluginVersion`/`pluginZipSha256`; the parser rejects an unstamped red or a stamped green |
| `#8845` did not reproduce on IU 262 but did on IU 252, the build in the issue | the platform changed how `File > Open` picks an open processor | pin `ide.version` from the issue in the spec's Fixture; on 25x use `DART_PLUGIN_VERSION=506.1.0` (needs LSP4IJ, which `Setup` installs) |
| On IU 252 the project opened but nothing rendered for 2+ minutes: Project tree "loading…", no toolbar actions, Driver screenshots timing out | 2025.2 defaults to out-of-process JCEF; under Xvfb the `cef_server` handshake for the auto-opened `README.md` Markdown preview blocked the EDT (thread dumps: `MarkdownJCEFHtmlPanel` → `CefServer`) | `Setup.kt` sets `jcef.remote.enabled=false` on Linux; a frozen EDT looks like a plugin failure, so check `monitoring-thread-dumps-ide/` before blaming the plugin |
