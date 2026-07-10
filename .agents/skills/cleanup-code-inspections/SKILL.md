---
name: cleanup-code-inspections
description: Reduce technical debt and improve code quality by systematically resolving static analysis warnings.
---

# Skill: Code Inspection & Cleanup

You are tasked with reducing technical debt and improving code quality by systematically resolving static analysis warnings.

## Objective
Reduce technical debt and improve code quality by systematically resolving static analysis warnings.

## Workflow Instructions

### 1. Baseline
- Run `./gradlew test` and `./gradlew verifyPlugin` to ensure the project is stable.

### 2. Run Inspection
- Execute `Analyze > Inspect Code...` on the project (or a specific module) using the "Default" profile.

### 3. Prioritize
- Focus first on "Probable bugs", "Performance", and "Memory" categories.

### 4. Refactor
- Apply quick-fixes for obvious issues (e.g., "Result of method call ignored", "String concatenation in loop").

### 5. Suppress
- Explicitly suppress false positives with a comment and reason (e.g., `//noinspection [ID] - [Reason]`).

### 6. Verify
- Run `./gradlew testClasses` to ensure safe refactoring.
- Run `./gradlew test` and `./gradlew verifyPlugin` to ensure no regressions.
- Re-run analysis to ensure the "Yellow code" count has decreased and no regressions were introduced.

### 7. Report & Review
- Summarize the inspections resolved.
- **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
- **Action:** Ask the user to review the changes closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Cleanup: Resolve static analysis warnings in [Module]").
