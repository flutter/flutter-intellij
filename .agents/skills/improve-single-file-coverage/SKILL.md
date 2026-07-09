---
name: improve-single-file-coverage
description: Write a new single test file, or modify an existing file, to improve coverage for a specific target class using Kover.
---

# Skill: Single File Test Coverage Improvement

You are tasked with writing a new single test file or modifying an existing test file to improve coverage for a specific target class.

## Objective
Write a new single test file, or modify an existing file, to improve coverage for a specific target class.

## Workflow Instructions

### 1. Identify Target
- Choose a single source file (Java or Kotlin) in `src/` that has low or no test coverage and is suitable for unit testing (e.g., utility classes, helpers).

### 2. Establish Baseline
- Run `./gradlew testClasses` to ensure the project compiles.
- Run `./gradlew test` to ensure the project is stable.
- Run `./gradlew verifyPlugin` to ensure no verification issues exist.
- Run tests with Kover to determine the current coverage percentage for this file.

### 3. Implement/Update Test
- Create a new test file in `testSrc/` or update the existing one. Focus on:
  - Edge cases (null inputs, empty strings, boundary values).
  - Branch coverage (ensure if/else paths are exercised).
  - Mocking dependencies where necessary.

### 4. Verify & Iterate
- Run the tests to ensure they pass and check the coverage increase.
- Run `./gradlew verifyPlugin` to ensure no regressions.
- If coverage is still low, **iterate a few times**: analyze missed lines/branches in the Kover report and add targeted test cases to improve it further.

### 5. Report & Review
- Summarize what was fixed/covered and report Kover progress (e.g., `X% -> Y%` for `<filename>`).
- **Action:** Ask the user to review the new tests closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Improve test coverage for [Class Name]").
