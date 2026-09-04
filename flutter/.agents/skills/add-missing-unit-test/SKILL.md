---
name: add-missing-unit-test
description: Add a new unit test for a class that currently lacks one or add a new test case to an existing test file, verifying improvement with Kover.
---

# Skill: Add Missing Unit Test

You are tasked with adding a new unit test for a class that currently lacks one, or adding a new test case to an existing test file, to improve code coverage and reliability.

## Objective
Add a new unit test for a class that currently lacks one, or add a new test case to an existing test file, to improve code coverage and reliability.

## Workflow Instructions

### 1. Identify Target
- Choose a class in `src/` that has complex logic but logic coverage is missing or incomplete.

### 2. Establish Baseline
- Run `./gradlew testClasses` to compile all sources (production and test) and ensure the project is in a valid state.
- Run `./gradlew verifyPlugin` to ensure no plugin verification issues exist.
- Run `./gradlew test` (or specific test if it exists) to establish current pass/fail status.
- Run tests with Kover (`./gradlew koverHtmlReport` or `./gradlew koverXmlReport`) to determine current coverage percentage for target class.

### 3. Implement Test
- Create a new test class (`[ClassName]Test`) in `testSrc/` if it doesn't exist.
- Add test cases to cover specific methods, edge cases, and branches.
- Ensure the test follows project testing conventions (naming, mocking, assertions).

### 4. Verify
- Run `./gradlew testClasses` to ensure new test compiles.
- Run new test (and related tests) to ensure it passes.
- Run `./gradlew verifyPlugin` to ensure no regressions in plugin verification.
- Run Kover again to confirm coverage improvement.

### 5. Report & Review
- Summarize test added and coverage improvement.
- **Test Location:** Explicitly state *where* in the IDE user should go to test changed functionality (e.g., "Run the new test file `[TestFileName]`").
- **Action:** Ask user to review new test closely. Validate that code being tested is actively used (e.g., check that it is not dead code/0 references).
- **Do not commit or push.**
- Provide a suggested Git commit message, **explicitly including the Kover coverage improvement**:
  - Example Pattern: `Add unit tests for [Class Name]. Coverage verified using Kover. Coverage improvement: [Class Name]: ~[X]% line coverage ([Y] covered, [Z] uncovered).`
