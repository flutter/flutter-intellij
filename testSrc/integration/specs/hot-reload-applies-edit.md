# Hot reload applies an edit to a running app

**Goal:** as a Flutter developer, I want an edit in my code to show up in the
running app without restarting it.
**Source:** manual (coverage gap: no test proves a reload is a *reload*, that
the edit reaches the app, or that stop ends the process). MVP test T1.
**Seam gate result:** `ui` — needs a live `flutter run` process answering the
daemon's `app.restart` request, the VM service to read the app's widget tree,
and the Run tool window. A platform test has no running app to reload. No
existing spec shares this fixture and boot state.

## Target seam

- `io.flutter.actions.ReloadFlutterAppRetarget` (`Flutter.Toolbar.ReloadAction`),
  which retargets to `io.flutter.actions.ReloadFlutterApp` for the running app.
- `io.flutter.run.FlutterReloadManager.saveAllAndReload` → `io.flutter.run.daemon.FlutterApp.performHotReload`
  → `DaemonApi.restartApp(appId, fullRestart = false, pauseAfterRestart, reason)`.

**Why not a unit test:** a unit test can check that `performHotReload` builds
an `app.restart` request with `fullRestart = false`, but not that the request
reaches a real app, that the saved edit is what the app reloads, or that the
toolbar action is enabled in the Run tool window's context.

## Fixture

- Base project: `flutter create` counter app (the harness default). Device: Chrome (web).
- Overlay: none.
- Product: `IU` and `AI`. `ide.version`: the harness default, pinned by the runner.

## Preconditions

- Flutter SDK configured (`FLUTTER_SDK`); `flutter devices` lists Chrome.
- `lib/main.dart` open in the editor. The `MyHomePage(title:)` argument reads
  `'Flutter Demo Home Page'`.
- No run configuration is running.

## Steps

| # | Task | User interaction | Drives |
|---|---|---|---|
| 1 | Launch | Pick *Chrome (web)* in the device selector on the main toolbar | `selectDevice("Chrome")` |
| 2 | Launch | Click *Run* next to the `main.dart` run configuration (not *Debug*) | `invokeFlutterAction("Run")` |
| 3 | Launch | Wait for the app to start: the Run console prints the VM service URI | `waitUntil { vmServiceUriFromConsole(consoleText()) != null }` |
| 4 | Edit | In `lib/main.dart`, change `'Flutter Demo Home Page'` to `'Hot Reloaded Home Page'` in the `MyHomePage(title:)` argument. Don't save. | `editorFor("main.dart").text = text.replace(old, new)` |
| 5 | Reload | **(trigger)** Click *Flutter Hot Reload* in the Run tool window toolbar | `invokeFlutterAction("Flutter.Toolbar.ReloadAction")` |
| 6 | Stop | Click *Stop* in the Run tool window | `stopAppAndVerifyTerminated()` |

Step 5 drives the action by ID rather than clicking the button, so the test is
not sensitive to toolbar layout. `invokeFlutterAction` still asserts the action
was enabled and visible in context, so the shortcut doesn't hide a broken
toolbar.

## Expected outcome

The running app shows the new title after a **hot reload** (not a restart),
the console reports a successful reload, and stopping the app ends the
`flutter` process.

**Requirements:** the reload completes within 3 minutes of step 5; the process
exits within 30 s of step 6.

## Oracle

- **O1 — Pre-state** (after step 3, before step 4):
  - The Run console contains `Flutter run key commands` or the VM service URI
    line, and does **not** contain `Reloaded`, `Restarted`, or `Performing hot`.
    (The plugin clears the Run console when a reload or restart starts,
    `FlutterApp.onAppProgressStarting`, so after step 5 the console holds only
    output from the reload onwards; O2–O4 read the whole console, not an
    offset.)
  - VM service: the running app's widget tree contains `"Flutter Demo Home Page"`
    and not `"Hot Reloaded Home Page"`. (This proves the title probe sees the
    live app, so O3 can't pass by reading the editor.)
  - The selected run configuration is of type `FlutterRunConfigurationType`
    (not the Dart plugin's).
- **O2 — Attribution** (after step 5):
  - `Flutter.Toolbar.ReloadAction` was enabled and visible when invoked
    (`invokeFlutterAction` fails otherwise).
  - The console shows a plugin-printed `app.progress` message, `Performing hot`
    (reload *or* restart: O2 proves our plugin drove the daemon; whether it was
    a reload is O3/O4).
- **O3 — Positive postconditions:**
  - The console matches
    `Reloaded ([1-9]\d* of \d+ librar|application in)` (devices report
    libraries; web reports `Reloaded application in …`).
  - VM service: the widget tree now contains `"Hot Reloaded Home Page"` and not
    `"Flutter Demo Home Page"`.
  - The editor still shows the new title (the plugin's save-all ran, the edit
    wasn't reverted).
  - After step 6: the Stop buttons are disabled and the console reports the
    process finished.
- **O4 — Negative / analysis postconditions:**
  - The console does **not** contain `Performing hot restart`,
    `Restarted application`, `Hot reload failed`, `Hot reload rejected`, or
    `Reloaded 0 of`.
  - No `Flutter run key commands` after the reload (the console was cleared at
    the reload, so its presence means a relaunch, not a reload).
  - No `SEVERE` `idea.log` entries with `io.flutter` frames (harness, green runs).
  - After step 6: no `flutter`/`dart` processes left behind (harness).

## Test level

`ui` — `ExistingProjectUITest` subclass, `@Tag("agent-generated")`.

## Certification plan

- **Red build:** fault in `FlutterApp.performHotReload`: pass `fullRestart = true`
  to `myDaemonApi.restartApp(...)`, so a reload request becomes a restart.
  Expected failure: `O4` (`Restarted application` seen) or `O3` (no successful
  `Reloaded` line), after steps 1–5 and `O1` complete.
- **Green build:** `HEAD`, `N_green = 3`.
- **N_red:** 1.
