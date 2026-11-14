---
name: agent-testing-change-to-spec
description: Turn a local diff, branch, or pull request against the Flutter or Dart IntelliJ plugins into one or more workflow specs that exercise the changed user-visible behavior, with a red-build strategy so the resulting tests are proven to fail without the change. Use when asked to "verify my change in the IDE", "add integration coverage for this PR", or "test this diff". Don't use for a bug report (agent-testing-bug-to-spec) or a workflow with no diff (agent-testing-workflow-to-spec).
---

# Skill: Change to Spec

Turn a code change into **workflow specs** for the user-visible behavior it
adds or changes, and decide how the red build for each spec will be made. You
produce specs only.

This skill shares its gate, Steps table, Oracle contract, and template with
[`agent-testing-workflow-to-spec`](../agent-testing-workflow-to-spec/SKILL.md);
read that first.

## Input

- The diff: a branch name, a commit range, a PR number, or uncommitted changes.
- The intent: the PR description, commit messages, or the linked issue. If
  there is none, ask for one sentence. A diff without intent produces specs
  that test the implementation instead of the behavior.

## Workflow

### 1. Read the intent before the diff

Write down, from the intent alone, what a user can now do, or do differently.
That list is the candidate Goals. Then read the diff to find the seams and to
check the list; add to it only if the diff clearly changes user-visible
behavior the intent didn't mention, and say so.

The order matters. Specs written from the diff first mirror the diff: they
assert that the new method is called, not that the user got the result. Those
pass on any change that keeps the method, including a broken one.

### 2. Classify every hunk

For each contiguous block of changed lines in the diff (a *hunk*, in
`git diff` terms: one `@@ ... @@` section) that touches `src/`, `resources/`,
or the Dart plugin, record one of:

| Class | Meaning | Spec needed? |
|---|---|---|
| **Behavior change** | An existing user path does something different | yes |
| **New behavior** | A new action, dialog, setting, banner, or `plugin.xml` entry | yes |
| **Refactor** | Same behavior, different code | only if no spec covers the path already; then the spec protects the refactor |
| **Internal** | Logging, tests, build files, docs | no |

Changes that are only in `testSrc/` or `.agents/` need no spec.

### 3. Run the seam gate per Goal

As in `agent-testing-workflow-to-spec` step 2. The seam is usually obvious from the diff;
the level is not. Downshift when the changed code is reachable from
`BasePlatformTestCase` (a producer, an inspection, a settings persistence
class). Keep `ui` when the change is in an action's enablement, a dialog, a
process launch, or anything that needs a running `flutter`.

Group Goals that share a fixture into one spec with several Tasks; a change
that adds three settings to one page is one spec, not three.

### 4. Decide the red-build strategy

This is the step that's specific to changes, and it goes in the spec's
**Certification plan**. The runner needs to know how to build a plugin that
*lacks* the change, so the test can be shown red against it.

| Change class | Red build | Caveat |
|---|---|---|
| **Behavior change** to existing code | `merge-base` of the branch (the code before the change) | Valid only if the test compiles against it and reaches the trigger step. If the change renamed a symbol the test uses, note it; the runner builds red from `HEAD` with the hunk reverted instead |
| **New behavior** (new action ID, new class, new `plugin.xml` entry) | **Hollow seam:** `HEAD` with the body of the new code replaced by a no-op that keeps the signature and registration | `merge-base` won't do: the action ID doesn't exist there, so the test fails at step 1 with `ActionNotFound`, which proves nothing about the oracle. Name the method whose body should be hollowed |
| **Refactor** | `HEAD` with a one-line semantic fault in the refactored seam (wrong branch, dropped event, swapped argument) | Name the fault. Prefer one that degrades the outcome over one that throws |

Say which oracle part the red run should trip. If the honest answer is O2
only ("the new method wasn't called"), the oracle is too thin: add what the
user would notice to O3 and O4.

### 5. Save and report

As in `agent-testing-workflow-to-spec` step 6. **Source** is the branch or PR. Report to the
user: each Goal, its class, seam, level, and red-build strategy, and any
hunk you classified as *Refactor* or *Internal*, so they can disagree.

## Hard rules

- Never derive the Expected outcome or the Oracle from the diff. Derive them
  from the intent and from what a user sees; use the diff only to find the
  seam and to classify hunks.
- Never let the spec reference internal names from the diff in the User
  interaction column. Those go in Drives, if anywhere.
- Never propose `merge-base` as the red build for new behavior.
- Don't push, open PRs, or comment on the PR under review.
