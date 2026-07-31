---
name: resolve-verification-issues
description: Eliminate plugin verification warnings and errors (Internal API usage, Override-only API usage, etc.) identified by ./gradlew verifyPlugin.
---

# Skill: Resolve Plugin Verification Issues

You are tasked with eliminating plugin verification warnings and errors (Internal API usage, Override-only API usage, etc.).

## Objective
Eliminate plugin verification warnings and errors (Internal API usage, Override-only API usage, etc.).

## Workflow Instructions

### 1. Baseline
- Run `./gradlew testClasses` to ensure project compiles.
- Run `./gradlew test` and `./gradlew verifyPlugin` to establish a clean baseline.

### 2. Analyze Report
- Identify distinct issues to resolve (e.g., "Override-only API usage in ClassX").

### 3. Implement Fix
- Resolve the specific issue in the current branch.
- Ensure the fix is minimal and targeted.

### 4. Verify Fix
- Run `./gradlew testClasses` to ensure no new compilation errors.
- Run `./gradlew verifyPlugin` again to confirm the specific warning is gone and quantify the improvement.
- Run `./gradlew test` to ensure no regressions.

### 5. Validation & Manual Testing
- Explain how to verify the fix manually by running `./gradlew runIde`.
- Provide specific navigation steps for the user to reach the changed code within the plugin (e.g. "Open Settings > Languages & Frameworks > Flutter...").
- Verify that the changes do not introduce runtime errors or regressions.

### 6. Report & Review
- Summarize the remaining `verifyPlugin` issues (e.g., "Total remaining issues: X compatibility warnings, Y internal API usages").
- Confirm that `./gradlew runIde` validation passed.
- **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
- **Action:** Ask the user to review the fix closely.
- **Do not commit or push.**
- Provide a suggested Git commit message including specific counts (e.g., "Fix Internal API usage in ClassX: 5 experimental APIs used -> 4 experimental APIs used").
