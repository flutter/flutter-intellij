---
name: prepare-pr-cleanup
description: Verify current test suite status with ./gradlew test, clean up any temporary debug modifications, and harden test coverage for active files before opening a pull request.
---

# Skill: Prepare PR Cleanup & Verification

You are tasked with verifying the current test suite status, cleaning up temporary modifications, and hardening test coverage for active files before a pull request.

## Objective
Verify the current test suite status with `./gradlew test`, clean up any temporary modifications, and harden test coverage for active files.

## Workflow Instructions

### 1. Baseline
- Run `./gradlew testClasses` to ensure the project compiles.
- Run `./gradlew test` to establish the current passing state.
- Run `./gradlew verifyPlugin` to ensure no existing verification issues.

### 2. Fix Failures
- If there are any test failures or compilation errors, investigate and resolve them. Prioritize fixing code over deleting tests.

### 3. Cleanup
- Review any currently modified files (run `git status` or check the diff). Remove any:
  - `System.out.println` / debug print statements.
  - Unused imports.
  - Commented-out code blocks.
  - Temporary "hack" fixes that should be replaced with proper solutions.

### 4. Verify & Expand
- For the files you touched or cleaned up, check if there are obvious edge cases missing from their unit tests. Add JUnit tests to cover these cases.
- Run `./gradlew test` again to ensure cleanup didn't break anything.
- Run `./gradlew verifyPlugin` to ensure strict compliance.

### 5. Report & Review
- Summarize the cleanup status (e.g., "Tests passing, removed 3 debug prints").
- **Action:** Ask the user to review the changes closely to ensure no intended code was accidentally removed.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Prepare for PR: Fix tests and remove debug code").
