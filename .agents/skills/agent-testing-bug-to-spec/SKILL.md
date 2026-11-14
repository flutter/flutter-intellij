---
name: agent-testing-bug-to-spec
description: Turn a GitHub issue or bug report against the Flutter or Dart IntelliJ plugins into a workflow spec whose oracle encodes the correct behavior, so that a test written from it fails on the buggy code and passes on the fix. Use when asked to "reproduce", "write a test for issue #N", or "repro" a bug. Don't use for a plain workflow with no bug (agent-testing-workflow-to-spec) or a code change (agent-testing-change-to-spec).
---

# Skill: Bug to Spec

Turn a bug report into a **workflow spec** whose oracle describes what
*should* happen. A test written from the spec is red on the buggy code and
green once the bug is fixed. You produce the spec only.

This skill shares its gate, Steps table, Oracle contract, and template with
[`agent-testing-workflow-to-spec`](../agent-testing-workflow-to-spec/SKILL.md);
read that first. What's different here is the input rules and the oracle.

## Input

The issue text **as originally filed**: title, body, reproduction steps,
environment (IDE product and version, OS, Flutter SDK), plus any clarification
the person running you gives (step 1). Nothing from after the fix.

## Hide-the-answer rules

These exist so the test is shaped by the bug, not by the fix.

- **Don't read the fix.** Not the fix PR, not the fix diff, not the commit that
  closed the issue, not comments posted after the fix. If the issue is closed,
  the person running you (or `agent-testing-run-integration-test`) knows the
  fix commit; you don't need it.
- **Do read the plugin source** to find the seam: which `io.flutter` or Dart
  plugin code is on the path the user describes. That's allowed and expected.
  Reading source is not the same as reading the fix.
- **Say what you assumed.** Bug reports omit things. Every assumption (which
  device, which file was open, whether the project had `.idea/`) goes in the
  spec's **Preconditions** with "(assumed)" so a reviewer can check it against
  the report.

## Workflow

### 1. Check the report is reproducible

A spec needs three things from the report: how to get there (starting state
and steps), what the user saw, and what they expected instead. If any is
missing or ambiguous, ask the person running you, in one message, before
doing anything else. If they don't know either, offer two ways forward and let
them choose: they comment on the issue to ask the reporter, or they ask you to
draft that comment for them to post. Don't post it yourself. Don't proceed on
guesses; anything you must assume to move on goes in **Preconditions** marked
"(assumed)".

### 2. Extract the workflow from the report

From the reproduction steps, write the Goal, Tasks, and Interactions exactly
as in `agent-testing-workflow-to-spec` step 1. The Goal is what the user was *trying* to do,
not the bug. For #9106 ("empty SDK dropdown after cancelling the wizard") the
Goal is "create a new Flutter project after backing out of another project
type", and the bug is that one step on the way shows the wrong state.

### 3. Run the seam gate

Same four checks as `agent-testing-workflow-to-spec` step 2, with these additions:

- **Ownership** also decides whether the bug is *ours*. If the path the user
  describes never enters `io.flutter` or Dart plugin code (a platform bug, a
  `flutter` CLI bug), reject with the evidence and say where the bug belongs.
  The #9176 stretch case in the design doc is the model: a third-party plugin
  was the cause, so the issue can be diagnosed but not regression-tested here.
- **Level** often drops. Many bugs that are *reported* through the UI live in
  code that a platform test can reach: #7985 (test names escaped) is a
  run-configuration producer, so it's `platform`, even though the user saw it
  in the Run tool window. Say why.
- **Freeze and hang bugs** (#9093, #9013) are `ui`, and their Requirements
  section is mandatory: the oracle must name the blocked thread and frame, not
  just "it took too long".

### 4. Write the oracle for the correct behavior

The oracle says what a working plugin does, in the four parts:

| Part | For a bug |
|---|---|
| **O1 Pre-state** | The state just before the step where the bug shows. Often this is the setup the bug depends on (the wizard was opened for Kotlin first, then cancelled). Check it, don't assume it. |
| **O2 Attribution** | Proof that our code ran the step: the action was enabled, the producer was ours, the module is a Flutter module. This is what separates "our plugin got it right" from "the platform happened to do the right thing". |
| **O3 Positive** | The correct outcome the report says is missing: the dropdown lists the SDK; the test with a Cyrillic name runs and reports a result. |
| **O4 Negative** | The bug's symptom, stated as something that must *not* be there: no empty dropdown, no escaped name in the command line, no error banner, no `SEVERE` in `idea.log`, no frozen EDT. |

If the report says "sometimes" or "intermittently", say so in **Requirements**
and set the certification plan to `N_red = 3`; a 1-in-3 red is a real finding
and goes back to the user as *intermittent*, not as a flake.

### 5. Pin the environment

- If the bug is tied to an IDE version (#8890 on 2026.1), set `ide.version` in
  **Fixture** instead of `latest`.
- If the bug needs a project state (`invalid-sdk-path`, `two-sdks`, no
  `.idea/`), name the overlay. Create a new overlay only if none fits; say so.
- If the report names a platform (Windows for #9093), note it. Linux is the
  reference; a bug that only reproduces elsewhere is reported, not failed.

### 6. Certification plan

Write what red and green mean for this bug:

- **Closed issue:** red = `HEAD` with the fix reverted, green = `HEAD`. You
  don't know the fix commit; write "fix reverted by the runner".
- **Open issue:** red = `HEAD`. Green waits for a candidate fix. The test is
  tagged `unvalidated-repro` until then.
- Name the oracle part the red run should trip (usually O3 or O4). If the red
  run trips O1 or O2 instead, the spec has the pre-state wrong.

### 7. Save and report

As in `agent-testing-workflow-to-spec` step 6, plus: the **Source** line is the issue URL,
and the report to the user lists every assumption you made.

## Hard rules

- Never read the fix, the fix PR, or post-fix comments.
- Never comment on the issue yourself; draft the comment for the user if asked.
- Never write the oracle as "the bug doesn't happen". Write the correct
  behavior, with the symptom in O4.
