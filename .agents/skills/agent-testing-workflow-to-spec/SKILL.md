---
name: agent-testing-workflow-to-spec
description: Turn a prose description of an IDE workflow, or a coverage gap from the spec catalog, into a checked-in workflow spec for the Flutter and Dart plugins, after checking that the workflow is worth an integration test and picking the cheapest test level that can prove it. Use when asked to "write a spec for", "cover", or "add an integration test for" a user workflow. Don't use for a bug report (agent-testing-bug-to-spec) or a code change (agent-testing-change-to-spec).
---

# Skill: Workflow to Spec

Turn a user workflow into a **workflow spec**: a one-page markdown contract that
`agent-testing-author-integration-test` turns into a test and
`agent-testing-run-integration-test` proves red and green. You produce the spec
only. You don't write test code, and you don't run the IDE.

The design is in `.agents/design-docs/agent-integration-testing.md`
(sections 5.2 and 5.3). The spec format is [`TEMPLATE.md`](../../../testSrc/integration/specs/TEMPLATE.md).
Existing specs are listed in the catalog, [`specs/README.md`](../../../testSrc/integration/specs/README.md).

## Input

One of:

- A prose description: "open a project, run it on Chrome, edit `main.dart`, hot reload".
- A Goal from the catalog that has no spec yet.

If the description is vague ("test hot reload"), ask what you need to pin down
the Goal, the device, and the starting state, in one message rather than a
series, or pick the most common path and say so in the spec's **Source**.

## Workflow

### 1. Name the Goal, Tasks, and Interactions

Write the workflow as the user would experience it:

- **Goal:** the outcome, in one sentence, from the user's point of view.
- **Tasks:** the coarse steps on the way (Launch, Edit, Reload, Stop).
- **Interactions:** what the user does in each task, in the words of the UI
  ("Click *Flutter Hot Reload* in the Run tool window toolbar").

Don't mention test helpers, locators, or class names yet. If you can't describe
the workflow without them, it isn't a user workflow.

### 2. Run the seam gate

Four checks, cheapest first. If any fails, stop and write a **rejection
report** (below) instead of a spec.

**2a. Catalog.** Look the Goal up in
[`specs/README.md`](../../../testSrc/integration/specs/README.md). If a row
already covers it, stop and say which spec. This needs nothing but the Goal,
so it comes before any code reading.

**2b. Ownership.** Find the **target seam**: the specific code whose breakage
must turn the test red. It must be in a plugin we maintain and can build
broken:

| Seam lives in | Accept? | Red build comes from |
|---|---|---|
| `io.flutter.*` (this repo) | yes | `tool/agent-testing/build-broken-plugin.sh --repo flutter` |
| The Dart plugin (`com.jetbrains.lang.dart.*`, source in `dart-intellij-third-party`) | yes | `build-broken-plugin.sh --repo dart`, loaded via `DART_PLUGIN_ZIP` |
| IntelliJ platform, the `flutter` CLI, the Dart SDK, a third-party plugin such as FES | **no** | nothing we can break, so nothing we can prove |

Name the seam concretely: a class and method, an action ID, or a `plugin.xml`
registration. If the only plugin code involved is a pass-through to the
platform, reject.

**Finding the seam.** Don't guess which plugin owns the behavior; look:

| Signal | Where | Reading it |
|---|---|---|
| Package prefix | class names in `src/`, a probe dump, or an `idea.log` stack trace | `io.flutter.*` is this repo; `com.jetbrains.lang.dart.*` is the Dart plugin; `com.intellij.*` is the platform |
| Action ID prefix | `resources/META-INF/plugin.xml` here; the Dart plugin's `plugin.xml` | `Flutter.*` ours; `Dart.*` theirs |
| Hand-off points | `grep -rl "com.jetbrains.lang.dart" src/` (about 90 files) | where `io.flutter` calls into Dart: `io.flutter.dart.DartPlugin` configures the Dart SDK; `FlutterSaveActionsManager` talks to `DartAnalysisServerService`; `DartPsiUtil` reads Dart PSI. The seam is whichever side owns the behavior the Goal is about, not whichever side the user clicked in |
| Dart plugin source | `$DART_PLUGIN_REPO`, a checkout of `dart-intellij-third-party` | needed to name a Dart method and, later, to write a fault patch. If it isn't set, say so in the report; don't substitute a guess from the jar |

Example: "an error appears in the editor after typing invalid Dart" goes
editor → `com.jetbrains.lang.dart.analyzer.DartAnalysisServerService` → the
Dart Analysis Server → the Dart plugin's annotator. Nothing in `io.flutter` is on that path, so the seam is
the Dart plugin's, and the red build is a Dart plugin zip. "Hot reload applies
an edit" goes toolbar → `ReloadFlutterAppRetarget` → `FlutterApp.performHotReload`
→ the `flutter` daemon; the seam is ours.

**2c. Level.** Pick the test level from the smallest runtime the seam needs
to exist, not from how you'd phrase the assertion:

| The seam needs | Level | Where |
|---|---|---|
| Nothing outside the plugin: pure logic, parsers, lookup tables, string escaping, a race that needs a latch to reproduce | **unit** | redirect to `add-missing-unit-test`; no spec |
| PSI, VFS, modules, `plugin.xml` wiring, run-configuration producers, live templates, refactorings | **`platform`** | `BasePlatformTestCase` or a heavy fixture test in `testSrc/unit` |
| A running `flutter` process, the VM service, DevTools, a modal dialog or wizard, JCEF, project open, an EDT freeze | **`ui`** | `ExistingProjectUITest` in `testSrc/integration` |

Write one sentence in the spec saying why the level below yours can't prove
the seam. "Needs real UI" is not a reason; "needs a live `flutter` daemon to
answer the reload request" is.

**2d. Coalescing.** Now that you know the level: if it's `ui` and an existing
spec shares the fixture and boot state (same project, same device, same open
file), add a Task to that spec instead of creating a new one; a UI boot costs
minutes. Check `testSrc/integration` too, for tests that predate the catalog.

### 3. Write the Steps table

One row per interaction, in order, grouped by Task:

| # | Task | User interaction | Drives |
|---|---|---|---|

**User interaction** is step 1's wording. **Drives** is the technical action
the test will use: an action ID (`Flutter.Toolbar.ReloadAction`), a harness
helper (`selectDevice("Chrome")`), or, when a control must be clicked, a
locator. Rules:

- Drives never replaces User interaction. A reader must be able to follow the
  spec from the first three columns.
- When the UI path *is* the claim (a wizard, a menu), Drives must be the
  control, not the action ID behind it.
- Mark the trigger step: the one the oracle is about. Everything before it is
  setup; everything after it is observation or cleanup.
- If you don't know what a control is called, write Drives as `probe` and
  leave it to the authoring skill, which runs one probe for all unknowns.

### 4. Write the Oracle

Four parts, all required. Each becomes one `assertOracle("On")` block in the
test, so each must be something a test can check.

| Part | Answers | Example (hot reload) |
|---|---|---|
| **O1 Pre-state** | What is true right before the trigger, proving the outcome isn't already true | Console has no `Reloaded`; the live app's title is the old one |
| **O2 Attribution** | What proves *our* seam produced the outcome, not the platform or another plugin | The reload action was enabled in the Run tool window; `FlutterApp` sent `app.restart` with `fullRestart = false` |
| **O3 Positive** | The complete outcome, across every surface it touches | `Reloaded [1-9]\d* of \d+ libraries`; the live app shows the new title; stop ends the process |
| **O4 Negative** | What must *not* be there: degraded outcomes, leftovers, errors | No `Restarted`, no `Reloaded 0 of`; no `io.flutter` `SEVERE` in `idea.log`; no orphan `flutter` process |

Oracles that only look at the editor or the console text are usually
incomplete: ask what the user would see or lose if the seam were broken, and
check that.

Two lessons from T1: keep **O2 about whether our code acted**, not whether it
acted correctly (a reload that became a restart still satisfies O2; O3/O4
catch it), and remember that the plugin **clears the Run console** when a
reload or restart starts, so a post-trigger console check reads the whole
console rather than an offset into it.

### 5. Write the rest

- **Requirements** (optional): a time or health bound ("reload completes within
  30 s"). Required for freeze or performance workflows.
- **Fixture:** `flutter create` options, overlay name (see
  `testData/integration/overlays/`), IDE product.
- **Preconditions:** SDK, settings, open files.
- **Certification plan:** which fault the red run should use and which oracle
  it should trip. Prefer a fault that produces a *degraded* outcome (a reload
  that restarts, an event that is dropped) over one that throws or returns
  early, so that O3/O4 are what catch it, not O2.

Use [`TEMPLATE.md`](../../../testSrc/integration/specs/TEMPLATE.md). Run the
lint before you finish:

```bash
python3 .agents/skills/agent-testing-run-integration-test/scripts/lint_spec.py testSrc/integration/specs/<name>.md
```

### 6. Save and report

- Save as `testSrc/integration/specs/<goal-in-kebab-case>.md`.
- Add or update the Goal's row in `specs/README.md` (Goal, Tasks, Spec,
  Priority, Owner).
- Report to the user: the Goal, the seam, the level with its one-sentence
  reason, and anything you had to assume.

## Rejection report

When the gate fails, write this instead of a spec, and add the Goal to the
catalog with the spec column set to *rejected: <reason>*:

```markdown
## Not specced: <Goal>

**Reason:** <ownership | level | duplicate>
**Detail:** <which check failed and what you found: the code path, the
existing spec, or the unit test location>
**Nearest qualifying workflow:** <if any: a nearby Goal that does have a
plugin seam, so the request isn't lost>
```

## Hard rules

- Never invent a seam. If you can't point at code in `src/` or the Dart plugin
  that would make the test fail, reject.
- Never write O1–O4 by restating the Expected outcome four ways. Each part is a
  different observation.
- Never write test code or locators beyond the Drives column.
- Don't open issues, comment on issues, or push. The spec is a local file.
