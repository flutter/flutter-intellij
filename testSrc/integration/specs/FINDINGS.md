# Findings from certification runs

Things the agent-testing process turned up that are **not** defects in the test
being certified: product bugs in other components, platform behaviour that
changes a result, and environment traps. One entry per finding, newest first.
Humans: this is the list to triage. Agents: read it before classifying a
failure as a product finding, and append to it (same format) when you find
something new; link the evidence directory.

Format: **title** · date · where it bit · what it is · what we did · status.

---

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
- **What we did:** `DART_PLUGIN_VERSION` env override; `Setup.kt` installs LSP4IJ at `libs.versions.lsp4ij`; the run skill's preflight says to check Dart compatibility whenever `ide.version` is pinned away from the default.
- **Status:** handled by the harness.
