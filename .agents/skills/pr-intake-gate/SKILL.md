---
name: pr-intake-gate
description: Enforces pre-flight PR intake gates before requesting human review or opening a non-draft PR in flutter-intellij. Validates CHANGELOG updates (or requires an explicit reason in the commit message), copyright headers, plugin verifier status (./gradlew verifyPlugin), PR size budgets (>500 lines), test integrity (guarding against deleted or weakened tests), and AI review triage ([MUST-FIX]/[CONCERN]). Enforces a strict Git commit message stamp checked by CI presubmit. Use whenever preparing, validating, reviewing, or opening a pull request.
compatibility: Requires git, bash, and ./gradlew in flutter-intellij.
---

# Skill: PR Intake Gate (`pr-intake-gate`)

Enforces comprehensive pre-flight quality and readiness gates before pull requests are submitted or marked ready for human review by repository codeowners.

## Purpose & Philosophy

Pull requests should never consume codeowner review attention with mechanical defects, unverified deprecations, missing changelog entries, or unaddressed bot review findings.

This skill shifts quality verification left to the PR author and their coding agent:
1. **Zero Mechanical Review Waste**: Codeowners must never have to ask for changelog entries, missing copyright headers, or diagnose plugin verification failures.
2. **Deterministic Pre-Flight Gating**: All static and verifier checks run before opening a PR or transitioning from draft to ready.
3. **No Weakening Tests for CI**: Tests must never be deleted, disabled, or made vacuous simply to turn CI green.
4. **Active Bot Triage**: Automated review findings (`[MUST-FIX]` and `[CONCERN]`) must be resolved in code or answered with clear technical rationale—silent dismissal is strictly prohibited.
5. **Verifiable Proof Stamp**: Validated changes receive an immutable stamp in the Git commit message:
   `<!-- flutter-pr-check: passed @ <timestamp_epoch> (commit <sha>) -->`
   which is validated by repository CI presubmits before codeowner review begins.

---

## The 6 PR Intake Gates

Before stamping a commit or requesting review, evaluate each gate in order:

### Gate 1: CHANGELOG Entry Validation
- Inspect the diff against `upstream/main` (or target branch) for modified user-facing files in `src/`, `resources/`, or configuration.
- **Requirement**: `CHANGELOG.md` must be updated under `## Unreleased` in the appropriate category (`Added`, `Changed`, `Removed`, `Fixed`).
- **Exception Rule**: If a change has no user-facing impact (e.g., test-only modifications, internal refactoring, build configuration), `CHANGELOG.md` may remain unmodified **only if** the Git commit message includes an explicit reason:
  ```
  NO CHANGELOG: <specific technical explanation>
  ```
- See [Gate Specifications](references/GATE_SPECIFICATIONS.md#gate-1-changelog-entry-validation) for formatting details.

### Gate 2: Plugin Verification (`./gradlew verifyPlugin`)
- Run `./gradlew verifyPlugin` directly in the project root.
- Verify that no new compatibility errors, internal API usage errors, or binary breaking changes are introduced against targeted IntelliJ Platform releases.
- Pay close attention to cross-version platform deprecations (such as threading and read-action APIs across IntelliJ Platform releases).
- **Requirement**: The verifier must pass, and the verifier status must be summarized in the Git commit message:
  ```
  Plugin Verifier: 0 errors, <N> warnings (verified on target platform releases)
  ```
- See [Gate Specifications](references/GATE_SPECIFICATIONS.md#gate-2-plugin-verification) for resolution strategies.

### Gate 3: Copyright Headers
- Inspect every newly added file in the diff (`git status` or `git diff --name-only --diff-filter=A`).
- **Requirement**: Every newly added file must begin with the standard copyright header block:
  ```java
  /*
   * Copyright 2026 The Chromium Authors. All rights reserved.
   * Use of this source code is governed by a BSD-style license that can be
   * found in the LICENSE file.
   */
  ```
- See [Gate Specifications](references/GATE_SPECIFICATIONS.md#gate-3-copyright-headers) for language-specific comment formats (`.java`, `.kt`, `.dart`, `.xml`, `.properties`, `.sh`).

### Gate 4: PR Size & Complexity Budget (>500 lines)
- Check total changed lines (additions + deletions) using `git diff --stat`.
- **Requirement**:
  - Diffs $\le$ 500 lines: proceed normally.
  - Diffs > 500 lines: The Git commit message **must** include the header:
    ```
    LARGER PR: XXXX lines of change
    ```
    followed by a concise architectural justification explaining why the changeset cannot be split into smaller, independent pull requests.
- See [Gate Specifications](references/GATE_SPECIFICATIONS.md#gate-4-pr-size--complexity-budget) for justification criteria.

### Gate 5: Test Integrity & Anti-Weakening Guard
- Inspect test modifications in `testSrc/` or `test/`.
- **Strictly Prohibited**:
  - Deleting existing unit tests or test methods.
  - Adding `@Ignore` or `@Disabled` annotations to bypass failing tests.
  - Weakening assertions (e.g., changing strict equality to loose conditions or deleting assertions) to make CI green.
  - Catching exceptions silently in tests to prevent failures.
- **Requirement**: Every bug fix must include a regression test reproducing the original issue. Every new feature must include comprehensive unit tests.
- See [Gate Specifications](references/GATE_SPECIFICATIONS.md#gate-5-test-integrity--anti-weakening-guard) for details.

### Gate 6: AI / Bot Review Finding Triage
- For open draft PRs or subsequent iterations receiving automated reviews (e.g., Gemini Code Assist or static analysis bots):
- **Requirement**: Every `[MUST-FIX]` and `[CONCERN]` finding must be accounted for before marking the PR ready:
  1. **Fix in code**: Apply the recommended fix and verify tests pass.
  2. **Push back with rationale**: Post a reply to the bot finding tagged with either:
     - `[INTENTIONAL]`: Valid architectural trade-off (e.g., maintaining backward compatibility across older IDE versions).
     - `[FALSE-POSITIVE]`: Demonstrating that the bot's static assumption is invalid in this context.
- Silent dismissal, closing threads without reply, or leaving findings unaddressed is strictly prohibited.
- See [Bot Triage Guide](references/BOT_TRIAGE_GUIDE.md) for full examples.

---

## Git Commit Message Specification

The Git commit message serves as the permanent record of gate compliance. It must adhere to the following structure:

```gitcommit
<Short imperative summary title (50 chars or less)>

<Optional detailed description explaining the problem and solution,
wrapped cleanly at ~72 columns.>

[LARGER PR: XXXX lines of change - <architectural rationale>] (only if >500 lines)

[NO CHANGELOG: <reason>] (only if CHANGELOG.md is not modified)

Plugin Verifier: <summary of verifier status>

<!-- flutter-pr-check: passed @ <timestamp_epoch> (commit <sha>) -->
```

### Verification Stamp Format
The verification stamp is an exact, machine-readable HTML comment:
```html
<!-- flutter-pr-check: passed @ <timestamp_epoch> (commit <sha>) -->
```
- `<timestamp_epoch>`: Current Unix timestamp in seconds (e.g., `1793228400`).
- `<sha>`: Short commit SHA (7-10 characters) of the commit being certified.

See [Commit Message Conventions](references/COMMIT_MESSAGE_CONVENTIONS.md) for complete formatting rules and examples.

---

## Step-by-Step Agent Execution Playbook

When an agent is invoked to validate or prepare a PR, follow these steps:

### Step 1: Analyze Diff and Scope
1. Run `git status` to ensure a clean working directory (or stash uncommitted edits).
2. Determine base branch (`upstream/main` or `origin/main`).
3. Run `git diff --stat upstream/main...HEAD` to determine files changed and line counts.
4. If lines changed > 500, note that `LARGER PR: XXXX lines of change` will be required in the commit message.

### Step 2: Validate CHANGELOG & Copyright Headers
1. Check `git diff --name-only upstream/main...HEAD` for modifications to `CHANGELOG.md`.
   - If missing, check if changes are strictly non-user-facing. If so, prepare the `NO CHANGELOG: <reason>` line. If user-facing, add an entry under `## Unreleased` in `CHANGELOG.md`.
2. Check new files with `git diff --name-only --diff-filter=A upstream/main...HEAD`.
   - Read the head of each new file and assert that the standard Chromium BSD copyright header is present. If missing, insert it.

### Step 3: Audit Test Integrity
1. Review all diffs in `testSrc/` and `test/`.
2. Verify that no `@Test` methods were removed, commented out, or annotated with `@Ignore`/`@Disabled`.
3. Verify that assertions were not weakened or replaced with no-ops.
4. Verify that any code modification has corresponding unit tests.

### Step 4: Run Plugin Verification
1. Run `./gradlew verifyPlugin`.
2. Inspect the output. Ensure 0 compatibility errors exist.
3. Record the verifier summary (number of warnings/experimental usages).

### Step 5: Triage Bot / Review Findings (Draft PRs)
1. If operating on an existing PR, fetch PR review comments.
2. Ensure every `[MUST-FIX]` and `[CONCERN]` finding has either a code fix or an explicit `[INTENTIONAL]` / `[FALSE-POSITIVE]` response.

### Step 6: Generate Stamp & Format Commit
1. Determine the current Unix timestamp: `date +%s`.
2. Identify the commit SHA: `git rev-parse --short HEAD`.
3. Construct the verification stamp:
   `<!-- flutter-pr-check: passed @ <timestamp> (commit <sha>) -->`
4. Format the commit message incorporating all required gate markers.
5. Amend or create the commit:
   `git commit --amend -m "<formatted_message>"` (or prepare new commit).

### Step 7: Verify Presubmit Readiness
1. Run `.github/check_pr_intake_stamp.sh` locally to ensure the commit message passes the repository presubmit gate.

---

## Supporting References & Assets

- [Gate Specifications](references/GATE_SPECIFICATIONS.md): Complete failure criteria, regex patterns, and resolution guides.
- [Bot Triage Guide](references/BOT_TRIAGE_GUIDE.md): Protocol and examples for answering Gemini Code Assist findings.
- [Commit Message Conventions](references/COMMIT_MESSAGE_CONVENTIONS.md): Full commit message examples and guidelines.
- [PR Template Checklist](assets/PULL_REQUEST_TEMPLATE_CHECKLIST.md): Markdown checklist for `.github/PULL_REQUEST_TEMPLATE.md`.
