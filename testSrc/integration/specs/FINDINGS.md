# Findings from certification runs

Things the agent-testing process turned up that are **not** defects in the test
being certified: product bugs in other components, platform behaviour that
changes a result, and environment traps. One entry per finding, newest first.
Humans: this is the list to triage. Agents: read it before classifying a
failure as a product finding, and append to it (same format) when you find
something new; link the evidence directory.

Format: **title** · date · where it bit · what it is · what we did · status.

---

## On Windows the IDE under test opens behind the active window, and every click misses

- **Date:** 2026-11-27 · **Where:** T4 (`OpenProjectWithoutIdeaDirUITest`) on Windows 11, IU 262.10968.63, display at 250% scaling; every T4 run on this machine since 2026-11-23.
- **What:** step 1 ("click Open") reported success, step 2 timed out waiting for the Open dialog. `idea.log` had `SmoothRobot - Click was unsuccessful … expui/welcome/open.svg` (`MOUSE_RELEASED event was missing`). Driver clicks with the real mouse (`java.awt.Robot`), so a click goes to whatever window is on top at that spot. Windows' foreground lock doesn't let a process that the user didn't start take the foreground, so the IDE (launched by Gradle's test JVM) opened behind the maximized terminal and only flashed its taskbar button; the Starter screenshot showed the terminal. Java still reported the hidden IDE frame as focused, so a check based on `isFocused()` doesn't detect it. Earlier all-black screenshots were the same failure with the screen locked.
- **What we did:** `FlutterIdeUITest.bringIdeToFront()` makes each showing IDE window always-on-top for a moment on the IDE's UI thread (`toFront()` alone is ignored under the foreground lock), then restores it. Called with `force = true` when the welcome screen or project frame appears, and without force from `dismissInterruptions()` while polling. T4 then passed in 55 s with no unsuccessful clicks.
- **Status:** fixed in the harness. A locked or sleeping screen still breaks every UI test on Windows and macOS: keep the desktop unlocked.

## IDE Starter's process scans cost ~7 minutes per test on Windows

- **Date:** 2026-11-26 · **Where:** `HarnessSelfCheckUITest` on Windows 11, IU 262.10968.63.
- **What:** a passing self-check took 9 minutes, of which the test itself needed about 1.5. The new `[timing]` output showed three Starter steps, each finding nothing: the `KillOutdatedProcesses` JUnit extension's "Killing process containing '/ide-tests/'" before the class (2m 11s) and after it (2m 25s), and `MavenBuildTool`'s "Killing process containing 'MavenServerIndexerMain'" after the IDE closed (2m 47s, reported as `close IDE and wait`). Each reads every process's command line through OSHI (`processUtils.kt getProcessList`), which is that slow on this machine. The IDE itself shut down in 3 s. `MavenBuildTool` was only alive because `Setup.kt` called `withBuildTool<GradleBuildTool>()` (unused bootstrap leftover), and resolving one build tool instantiates every build tool in Starter's DI set.
- **What we did:** `FlutterIdeUITest` calls Starter's public `CommonScope.perSuiteScopeForIdeActivities()`, which disables the extension's scans, and runs its own `killLeftoverTestIdes()` (`ProcessHandle`, kills executables under `ide-tests`; 0.1 s). Removed `withBuildTool<GradleBuildTool>()`. Self-check: 9 m → 1 m 12 s end to end (test 223 s → 55 s). Trade-off: Starter now binds IDE processes to the suite scope rather than the class scope; every test closes its IDE in `tearDownIde` regardless.
- **Status:** fixed in the harness. `NewProjectUITest` doesn't extend `FlutterIdeUITest` and still pays for the scans.

## A stale `DART_PLUGIN_VERSION` silently disabled Flutter in every run

- **Date:** 2026-11-23 → 2026-11-26 · **Where:** T4 and `HarnessSelfCheckUITest` runs on IU 262 from a developer shell.
- **What:** `DART_PLUGIN_VERSION=506.1.0`, correct for a 252 run (see the Dart 510 entry below), stayed in effect. On 262 the platform rejected Dart (`requires IDE build 261.* or older`), then Flutter (`has dependency on 'Dart' which is not installed`). The old plugin gate polled for 2 minutes and failed with only `Timeout(2m): plugins to initialize`; the same commands passed for anyone without the variable, so it looked like flakiness. Unsetting the variable in the shell did **not** help: the test JVM inherits the environment of the Gradle daemon, which keeps the environment it was started with, so a daemon started while the variable was set kept passing it to every later run (reproduced 2026-11-26: a run with the variable unset still installed 506.1.0).
- **What we did:** `build.gradle.kts` now passes the harness's environment variables (`DART_PLUGIN_VERSION`, `DART_PLUGIN_ZIP`, `FLUTTER_SDK`, `AGENT_TESTING_MODE`, …) from the shell that ran Gradle via `providers.environmentVariable`, and removes them from the test JVM when that shell doesn't set them; this also makes them configuration cache inputs (same fix as `a522aa651` for `FLUTTER_SDK` alone). `utils/DartPluginSelection.kt` picks the Dart plugin before the IDE starts and checks it against the IDE build using the plugin's own `<idea-version since-build until-build>`. Explicit choices (`DART_PLUGIN_ZIP`, `DART_PLUGIN_VERSION=<v>` or `latest`) are installed exactly or fail in `PREFLIGHT` within seconds, naming the source; the default (`dartPluginVersion` from `gradle.properties`) falls back to the Marketplace's newest compatible version when the IDE build can't load it, and says so. `run-result.json` records `dartPluginVersion`, `dartPluginSource` and `dartPluginBuilds`. The plugin gate no longer polls (the plugin set is fixed at startup) and quotes the IDE's "Problems found loading plugins" lines.
- **Status:** fixed in the harness. Don't export `DART_PLUGIN_VERSION`; for a 25x run pin the IDE with `-Pide.version` and let the harness pick Dart.

## Dart 506.1.0 on IDE 251/252 throws fatal `Debug metadata version mismatch` coroutine errors

- **Date:** 2026-11-13 · **Where:** T4 green runs on IU 252.28238.7; also a hand-run IDE.
- **What:** `CoroutinesInternalError … Caused by: IllegalStateException: Debug metadata version mismatch. Expected: 1, got 2` in `CoroutineName(com.jetbrains.lang.dart.analyzer.DartAnalysisServerService)` and `…DartToolingDaemonService`, 4–6 times per project open. No `io.flutter` frames. Dart 506.x is compiled with Kotlin 2.3 (`mv=[2,3,0]`) but declares `since 251`; 2025.x's bundled stdlib can't read its coroutine metadata. Same bug as flutter/dart-intellij-third-party#210 and flutter/flutter-intellij#8729 (fixed in 503, regressed in 506 the day Kotlin was bumped to 2.4; 507+ are `since 253`, so 506.1.0 is terminal for 25x users).
- **What we did:** allowlisted the signature in `IdeLog` with justification (approved by Cory) so it can't mask Flutter-plugin errors; drafted an issue for the Dart repo.
- **Status:** issue to be filed (draft with Cory). Agents: do not widen the allowlist beyond this regex.

## The auto-opened README.md's JCEF Markdown preview freezes the EDT under Xvfb on IU 252

- **Date:** 2026-11-13 · **Where:** T4 green runs on IU 252.28238.7 (attempt 1, and again after the first fix).
- **What:** the platform opens `README.md` on a project's first open; its Markdown preview is a JCEF browser. Under Xvfb that browser blocked the EDT for 2+ minutes: first the out-of-process `cef_server` handshake (2025.2's default, `ide.browser.jcef.out-of-process.enabled=true`), then, with JCEF in-process, a native `CefClientHandler.N_removeMessageRouter` call while the preview was torn down. Symptoms: Project tree stuck at "loading…", no toolbar actions, Driver screenshots timing out — indistinguishable from a plugin failure without the thread dumps (`monitoring-thread-dumps-ide/`). A desktop IDE was fine. Not a plugin bug.
- **What we did:** `Setup.kt` turns off the advanced setting "Open README.md file on project startup" (`ide.open.readme.md.on.startup`) in the test config, so no Markdown preview and no JCEF is created at project open; it also keeps JCEF in-process on Linux (`jcef.remote.enabled=false`). T4 on 252 went 2/2 green and ~40% faster. Nothing under test depends on the README auto-open.
- **Status:** fixed in the harness. A spec that needs the README preview or out-of-process JCEF must say so in its Fixture.

## #8845 reproduces only on the reported IDE build

- **Date:** 2026-11-12 · **Where:** T4 red runs.
- **What:** with the fix (e965bb8d9) reverted, File > Open of a Flutter folder without `.idea/` does nothing on IU 252.28238.7 but opens fine on IU 262.10968.63: the platform changed how it picks an open processor. A certification on the harness default IDE would have been `mutant-survived` and told us nothing. Separately, Starter's command-line project open is **not** equivalent to the user's File > Open and must not be used as a shortcut for this workflow.
- **What we did:** spec Fixture pins `ide.version=252.28238.7`; `bug-to-spec` requires pinning the IDE build from the report (design doc §6.2); the test drives the welcome screen and file chooser.
- **Status:** documented; the test is certified on 252 and passes on 262 with either build.

## Starter's leftover `<ide-home>.vmoptions` hijacks hand-launched IDEs

- **Date:** 2026-11-13 · **Where:** manual before/after reproduction of #8845.
- **What:** Starter writes `build/out/ide-tests/cache/builds/IU-<build>/idea-IU-<build>.vmoptions` with `-Didea.{config,system,plugins,log}.path` pointing at the **last test's** sandbox. `idea.sh` reads it, and `-D` beats `idea.properties` / `IDEA_PROPERTIES`, so an IDE launched by hand silently used the test's sandbox and its (broken) plugin — both "broken" and "fixed" manual runs showed the bug.
- **What we did:** `tool/agent-testing/launch-ide.sh` passes its own vmoptions via `IDEA_VM_OPTIONS` and verifies paths, plugin version and jar sha256 from the IDE's log before handing the IDE over; broken builds are version-stamped so the IDE itself says which build it is.
- **Status:** fixed by the launcher. Never launch the cached IDE by hand any other way.

## Dart 510+ requires platform 253; 25x runs need Dart 506.1.0 and LSP4IJ

- **Date:** 2026-11-12 · **Where:** first T4 run on IU 252.
- **What:** `dartPluginVersion` in `gradle.properties` (510.0.0) is `since 253`; on a 25x IDE the plugin is rejected. Dart 506.1.0 is the last 25x-compatible release and depends on LSP4IJ, which the IDE doesn't bundle.
- **What we did:** `DART_PLUGIN_VERSION` env override; `Setup.kt` installs LSP4IJ at `libs.versions.lsp4ij`; the run skill's preflight says to check Dart compatibility whenever `ide.version` is pinned away from the default. Superseded 2026-11-26: the harness now picks a compatible Dart itself when `ide.version` is pinned (see "A stale `DART_PLUGIN_VERSION`…" above).
- **Status:** handled by the harness.
