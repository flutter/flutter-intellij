---
name: fix-specified-issue
description: Reproduce, test, fix, and verify a specific user-provided issue (stack trace or GitHub issue link) following plugin development guidelines.
---

# Skill: Fix Specified Issue

You are tasked with fixing a specific issue provided by the user (stack trace or GitHub issue).

## Objective
Fix a specific issue provided by the user (stack trace or GitHub issue).

## Workflow Instructions

### 1. Setup & Context
- Read the basics of IntelliJ plugin development: [https://plugins.jetbrains.com/docs/intellij/developing-plugins.html](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html).
- **Input Required:** If the user has not provided a stack trace or GitHub issue link, **ask for it now**. Do not proceed without a specific issue to reproduce and fix.

### 2. Reproduction
- Create a minimal reproduction case if possible, or identify the code path responsible for the crash/issue.
- **Establish Baseline:**
  - Run `./gradlew testClasses` to ensure the project compiles.
  - Run `./gradlew test` to ensure the current state is stable (or confirm failure if you wrote a repro test).
  - Run `./gradlew verifyPlugin` to check for existing verification issues.
  - Run tests with Kover to determine current coverage percentage.

### 3. Implement Fix
- Analyze stack trace or issue description.
- Apply fix in codebase.
- Ensure fix is minimal and targeted.
- Add a unit test if needed.

### 4. Verify
- Run `./gradlew testClasses` to ensure fix compiles.
- Run `./gradlew test` to ensure no regressions and that repro test (if created) now passes.
- Run `./gradlew verifyPlugin` to ensure no new verification issues were introduced.
- Run Kover again to confirm coverage is sustained or improved.
- Suggest manual test steps: Check code changes made and write test steps for a user to execute that trigger changed code paths. If needed, add logging statements to verify code paths successfully run.

### 5. Report & Review
- Summarize fix applied.
- **Test Location:** Explicitly state *where* in the IDE user should go to test changed functionality/verify fix.
- **Action:** Ask user to review fix closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Fix [Issue ID/Description]: [Summary of Fix]").
