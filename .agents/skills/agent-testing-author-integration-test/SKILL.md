---
name: agent-testing-author-integration-test
description: Turn a workflow spec in testSrc/integration/specs into a test class for the Flutter IntelliJ plugin at the level the spec names (ui via ExistingProjectUITest, or platform via BasePlatformTestCase), with one assertOracle block per oracle part, an optional fixture overlay, and at most one probe run to discover locators. Use when a spec exists and needs a test. Don't use to write a spec (agent-testing-*-to-spec) or to run and certify the test (agent-testing-run-integration-test).
---

# Skill: Author an Integration Test

Turn a **workflow spec** into a test. The spec is the contract: the test
performs its Steps, checks its Oracle parts O1–O4, and nothing else. You write
code under `testSrc/` and `testData/` only.

Design: `.agents/design-docs/agent-integration-testing.md`
sections 5.3–5.5. How Starter, Driver, and our harness work, with every helper
and the known pitfalls: [`ui-test-reference`](../ui-test-reference/SKILL.md).
Read it before writing a `ui` test. Recording a run as a walkthrough:
[`record-test-walkthrough`](../record-test-walkthrough/SKILL.md).

`step(...)` and `assertOracle(...)`, used below, are **our** harness helpers in
`FlutterIdeUITest`, not framework functions. They run a block, record its id,
status, message, and timing, and the base class writes the result to
`build/agent-testing/run-result.json` at the end of the test. Plain JUnit
assertions inside the blocks are fine; what matters is that every check is
inside the block for its oracle part.

## Input

A spec at `testSrc/integration/specs/<name>.md` that passes the lint:

```bash
python3 .agents/skills/agent-testing-run-integration-test/scripts/lint_spec.py testSrc/integration/specs/<name>.md
```

If it doesn't, stop and send it back; don't fix the spec yourself.

## Workflow

### 1. Choose the base

The level comes from the spec's **Test level**; the base class comes from its
**Fixture**:

| Level | Fixture says | Base class | File | Run with |
|---|---|---|---|---|
| `ui` | an existing project (`flutter create ...`, with or without an overlay) | `ExistingProjectUITest` | `testSrc/integration/io/flutter/integrationTest/<Name>UITest.kt` | `./gradlew integration --tests ...` |
| `ui` | no project; the workflow creates one (New Project wizard) | `NewProjectUITest` base | same | same |
| `ui` | no project at all (welcome screen, Settings, plugin dialogs) | `FlutterIdeUITest` | same | same |
| `platform` | a light or heavy fixture | `BasePlatformTestCase`, or a heavy fixture test when the spec needs a real module/SDK | `testSrc/unit/io/flutter/<area>/<Name>Test.java` or `.kt` | `./gradlew test --tests ...` |

All three `ui` bases extend `FlutterIdeUITest`, which carries the checks every
UI test gets for free: plugin loaded, `idea.log` gate, process cleanup,
`step`/`assertOracle`, `run-result.json`. Don't change the level. If the
spec's level or fixture looks wrong, say so and stop.

### 2. Map the Steps table to code

Each row becomes one `step("<#> <User interaction>") { <Drives> }` call, in
order. The step title is the User interaction text, so a failure says what the
user was doing, and the walkthrough recorder uses it as the caption.

For the Drives column:

- **Action ID** → `invokeFlutterAction("<id>")`, which checks that the action
  is enabled and visible in the current context before invoking it. Never
  call `ActionManager.getAction(id).actionPerformed(...)` directly; that skips
  `update()` and hides a disabled button.
- **Harness helper** → call it. If the helper doesn't exist and the step is
  one other specs will need (open settings page, wait for tool window), add it
  to `utils/` rather than inline.
- **Locator** → use it. Prefer `name`/`accessibleName` over `class`, and
  `class` over position. If a plugin component has no stable name, add one in
  `src/` in a **separate commit** that contains nothing else; the runner builds
  both red and green on top of it.
- **`probe`** → see step 4.

Rules for the code around the steps:

- Every wait is `waitUntil(message) { condition }`. No `Thread.sleep`, no
  `delay(`. The lint fails on both.
- Read console output through `consoleText(toolWindow, sinceOffset)`, never
  `allEditorText()`.
- Stop apps with `stopAppAndVerifyTerminated()`.
- `platform` tests resolve actions and extensions through `ActionManager`,
  `RunConfigurationProducer.getProducers`, or the extension point. Never
  `new` a `plugin.xml`-registered class; the registration is part of what's
  under test.

### 3. Map the Oracle to `assertOracle` blocks

One block per part, in order, named for it:

```kotlin
assertOracle("O1", "console has no reload yet; app shows the old title") { ... }
// trigger step here
assertOracle("O2", "reload action was enabled; FlutterApp sent app.restart without fullRestart") { ... }
assertOracle("O3", "console reports Reloaded N of M; app shows the new title") { ... }
assertOracle("O4", "no Restarted, no Reloaded 0, no SEVERE in idea.log") { ... }
```

`O1` runs before the trigger step, `O2`–`O4` after. Each block is the only
place its checks live, so the runner can tell from `run-result.json` which part
failed. Every predicate in the spec's oracle must appear in some block; add
nothing the spec doesn't ask for, and drop nothing it does. If a predicate
can't be checked (no API reaches it), stop and say which; don't substitute a
weaker one.

The base harness adds its own checks around yours: plugin loaded before the
test, `idea.log` and process cleanup after. Don't duplicate them.

### 4. Probe, once, if needed

If any Drives cell is `probe`, or a locator you wrote doesn't resolve, write a
throwaway test that performs the steps up to each unknown and at each one
dumps the component tree and a screenshot (the harness's `probe("<step>")`
helper does both, on every platform). Run it **once**, with every unknown in the same run; then
write the locators and delete the probe. A second probe needs a reason in your
report. Read `plugin.xml` and `src/` first; the name you need is often there.

### 5. Overlay, if the spec names one

If the overlay exists under `testData/integration/overlays/<name>/`, apply it
in `prepareProject()`. If the spec names a new one, create the smallest set of
files that produces the state, with a `README.md` in the overlay directory
saying what state it creates and which spec uses it.

### 6. Tag, link, lint

- `@Tag("agent-generated")` on `ui` tests; `@Category(AgentGeneratedTest.class)`
  on `platform` tests.
- KDoc on the class: one line of purpose plus a link to the spec. The
  walkthrough recorder reads this as the summary.
- The test must run on Linux, macOS, and Windows unless the spec's Fixture
  says the workflow is platform-specific. Use `Platform.kt` for paths and
  shortcuts; never hard-code a separator or a modifier key.
- Run the lint on the spec and test together:

  ```bash
  python3 .agents/skills/agent-testing-run-integration-test/scripts/lint_spec.py testSrc/integration/specs/<name>.md --test <path to test>
  ```

- Compile: `./gradlew --offline compileIntegrationKotlin -q` (or `compileTestJava`
  for `platform`). Don't run the test; that's the runner's job, and it needs to
  run it red first.

### 7. Report

List: the test file, the overlay (if any), any `src/` naming commit, any
probe run and what it found, and every spec predicate with the `assertOracle`
block it landed in. If anything in the spec couldn't be implemented as
written, say what and why, and stop there.

## Hard rules

- Write only under `testSrc/` and `testData/`, except a component-naming
  commit in `src/` that changes no behavior.
- Never weaken, drop, or reinterpret an oracle predicate. Send the spec back
  instead.
- Never add `Thread.sleep`, `Assumptions.assume*`, `@Disabled`, or a
  try/catch that swallows an assertion.
- Never run the test to green before the runner has run it red.
- Don't push, open PRs, or comment on issues.
