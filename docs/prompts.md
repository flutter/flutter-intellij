# AI Agent Prompts

## Prompt 1: Verify Tests and Cleanup
**Objective:** Verify the current test suite status with `./gradlew test`, clean up any temporary modifications, and harden test coverage for active files.

**Instructions:**
1.  **Run Tests:** Execute `./gradlew test` to establish the current baseline.
2.  **Fix Failures:** If there are any test failures or compilation errors, investigate and resolve them. Priorities fixing code over deleting tests.
3.  **Cleanup:** Review any currently modified files (run `git status` or check the diff). Remove any:
    *   System.out.println / debug print statements.
    *   Unused imports.
    *   Commented-out code blocks.
    *   Temporary "hack" fixes that should be replaced with proper solutions.
4.  **Expand Coverage:** For the files you touched or cleaned up, check if there are obvious edge cases missing from their unit tests. Add JUnit tests to cover these cases.
5.  **Verify:** Run the full test suite again to ensure everything is green and clean.

---

## Prompt 2: Full Project Test Coverage Expansion & PR Split
**Objective:** Systematically expand unit test coverage across the entire project and submit changes in organized, granular Pull Requests.

**Instructions:**
1.  **Analyze Coverage:** Scan the `src/` directory (specifically packages like `io.flutter.utils`, `io.flutter.run`, `io.flutter.sdk`, `io.flutter.logging`, etc.) to identify utility classes, helpers, and data structures that lack corresponding unit tests in `testSrc/`.
2.  **Implement Tests:** Systematically write JUnit tests for these uncovered classes. Focus on:
    *   Edge cases (null inputs, empty strings, invalid formats).
    *   Branch coverage (ensure if/else paths are tested).
    *   Mocking dependencies where necessary to keep tests fast and unit-focused.
3.  **Verify Locally:** Run `./gradlew test` frequently to ensure new tests pass and no regressions are introduced.
4.  **Split and Submit:** instead of one giant commit, organize your changes into logical groups (e.g., by package). For each group:
    *   Create a new branch off `main` (e.g., `test-coverage-utils`, `test-coverage-run`).
    *   Add only the relevant source and test files for that package.
    *   Commit with a clear message (e.g., "Add test coverage for io.flutter.utils").
    *   Push the branch to `origin`.
    *   (Optional) Reset `main` or switch back to `main` to start the next branch cleanly.
5.  **Final Report:** Provide a summary of the branches created and the packages covered.
