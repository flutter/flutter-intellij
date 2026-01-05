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

---

## Prompt 3: Update org.jetbrains.intellij.platform
**Objective:** Update the `org.jetbrains.intellij.platform` plugin version to the latest available.

**Instructions:**
1.  **Build the plugin** using `./gradlew buildPlugin` to establish a baseline and check for any existing warnings.
2.  **Create a new branch** off of `main` for this update.
3.  **Find the new version name and URL**:
    *   Open `https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform` in Chrome.
    *   Identify the latest version number.
4.  **Make the change**:
    *   Update the version in `third_party/build.gradle.kts` (look for `id("org.jetbrains.intellij.platform") version "..."`).
5.  **Validate**:
    *   Run `./gradlew buildPlugin` again to ensure the build succeeds.
    *   Run the tests (`./gradlew test`) to ensure everything is still working.
    *   **Crucial**: If any tests fail, verify if they are pre-existing failures by running them on the `main` branch (or the version before your changes). Only failures introduced by the update need to be fixed.
6.  **Create a PR**:
    *   Use a commit message like: "Update org.jetbrains.intellij.platform to <VERSION>"
    *   You can point to https://github.com/flutter/dart-intellij-third-party/pull/167 as an example of a similar PR.

---

## Prompt 4: Resolve Plugin Verification Issues
**Objective:** Eliminate plugin verification warnings and errors (Internal API usage, Override-only API usage, etc.) by creating isolated, non-colliding fix branches.

**Instructions:**
1.  **Run Verification:** Execute `./gradlew verifyPlugin` to generate the latest verification report.
2.  **Analyze Report:** Identify distinct issues or categories of issues (e.g., "Override-only API usage in ClassX", "Internal API usage in ClassY").
3.  **Branch Strategy:** For each distinct issue:
    *   Check existing `verify-fix-*` branches to ensure you don't duplicate work.
    *   Create a new branch named `verify-fix-XX-topic` (e.g., `verify-fix-15-internal-api`), incrementing the number from the latest existing branch.
4.  **Implement Fix:**
    *   Resolve the specific issue (e.g., remove the override-only call, migrate the internal API).
    *   Ensure the fix is minimal and targeted.
5.  **Verify Fix:** Run `./gradlew verifyPlugin` again to confirm the specific warning is gone and no new issues were introduced.
6.  **Commit:**
    *   Commit with a clear message: `Fix [Issue Type] in [Class Name]`.
    *   Include relevant website links and supporting information.
    *   Include the number of warnings or errors resolved in the commit message.
    *   Do not push any branches, the user will do that.
    *   Switch back to `main` to start the next fix.
7.  **Final Report:**
    *   Provide a summary to the user of all of the fixes and which branches they are in.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

---

## Prompt 5: Gradle Build Optimization
**Objective:** Improve local build performance and CI efficiency by leveraging Gradle's caching and profiling tools.

**Instructions:**
1.  **Profile Build:** Run `./gradlew clean build --profile` to identify slow tasks and bottlenecks.
2.  **Enable Caching:** Check `gradle.properties` for `org.gradle.caching=true`. If missing, add it to enable the Build Cache.
3.  **Configuration Cache:** Run `./gradlew help --configuration-cache` to check for compatibility. If feasible, enable it to speed up the configuration phase.
4.  **Update Wrapper:** Check if a newer stable Gradle version is available and update the wrapper using `./gradlew wrapper --gradle-version [VERSION]`.
5.  **Verify:**
    *   Run a clean build to ensure optimizations didn't break the build process.

---

## Prompt 6: Plugin Compatibility & EAP Verification
**Objective:** Ensure the plugin remains compatible with the latest IntelliJ Platform releases and EAP (Early Access Program) builds.

**Instructions:**
1.  **Target EAP:** Temporarily update `gradle.properties` to target the latest IntelliJ Platform EAP version.
2.  **Run Verification:** Execute `./gradlew verifyPlugin` to check for binary incompatibilities or broken API usages in the new version.
3.  **Check Bounds:** Review `plugin.xml` for `<idea-version since-build="..." until-build="..."/>`. Ensure `until-build` is open-ended or appropriately set for the new version.
4.  **Resolve Issues:** Fix any compilation errors or verification warnings specific to the new platform version.
5.  **Report:**
    *   Summarize any required changes or blockers for supporting the new version.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

---

## Prompt 7: UI Thread Safety & Responsiveness
**Objective:** Prevent UI freezes and ensure a responsive user experience by validating threading rules.

**Instructions:**
1.  **Audit EDT Usage:** Search for usages of `runInEdt`, `invokeLater`, and `invokeAndWait`.
2.  **Identify Blocking Calls:** Ensure no I/O operations (file access, network calls) or heavy computations occur within these UI blocks.
3.  **Migrate to Background:** Refactor heavy tasks to run on background threads using `Task.Backgroundable` or `ReadAction.nonBlocking`.
4.  **Check Modality:** Verify that modal dialogs do not block the UI thread unnecessarily.
5.  **Verify:**
    *   Run the plugin in a sandbox (`./gradlew runIde`) and perform key actions while monitoring for UI freezes.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

---

## Prompt 8: Code Inspection & Cleanup
**Objective:** Reduce technical debt and improve code quality by systematically resolving static analysis warnings.

**Instructions:**
1.  **Run Inspection:** Execute `Analyze > Inspect Code...` on the project (or a specific module) using the "Default" profile.
2.  **Prioritize:** Focus first on "Probable bugs", "Performance", and "Memory" categories.
3.  **Refactor:** Apply quick-fixes for obvious issues (e.g., "Result of method call ignored", "String concatenation in loop").
4.  **Suppress:** Explicitly suppress false positives with a comment and reason (e.g., `//noinspection [ID] - [Reason]`).
5.  **Verify:** Re-run analysis to ensure the "Yellow code" count has decreased and no regressions were introduced.

---

## Prompt 9: Accessibility (A11y) Audit
**Objective:** Ensure the plugin's custom UI components are accessible to users with screen readers and other assistive technologies.

**Instructions:**
1.  **Identify Custom UI:** List all custom `JPanel`, `JComponent`, or `Dialog` classes in the codebase.
2.  **Check Properties:** Verify that every interactive component has:
    *   `getAccessibleContext().setAccessibleName(...)`
    *   `getAccessibleContext().setAccessibleDescription(...)`
3.  **Focus Management:** Ensure custom components handle focus traversal correctly (Tab/Shift+Tab).
4.  **Color Contrast:** If custom colors are used, verify they meet WCAG contrast guidelines (especially for dark themes).
5.  **Verify:**
    *   Use the "Accessibility Inspector" (if available in the SDK) or a screen reader to navigate the UI.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

---

## Prompt 10: Dependency & Library Audit
**Objective:** Optimize plugin size and security by removing unused dependencies and updating outdated libraries.

**Instructions:**
1.  **Audit `build.gradle`:** Review `dependencies {}` block. Identify libraries that might no longer be used.
2.  **Check `plugin.xml`:** Ensure all `<depends>` tags match the actual API usage.
3.  **Update Versions:** Check for newer stable versions of third-party libraries (e.g., using `./gradlew dependencyUpdates` if configured, or manual check).
4.  **Remove Unused:** Delete any local `.jar` files in `lib/` that are not referenced or can be replaced by Maven coordinates.
5.  **Verify:**
    *   Run `./gradlew buildPlugin` and check the distribution size. Ensure the plugin still loads and functions correctly.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

---

## Prompt 11: Documentation Coverage
**Objective:** Ensure all public classes and methods in `src/` have Javadoc/KDoc to improve maintainability and API clarity.

**Instructions:**
1.  **Scan Codebase:** Identify public classes and methods in `src/` missing `/** ... */` or `///` documentation.
2.  **Add Docstrings:** Write concise descriptions for missing items, explaining *what* they do and *why*.
3.  **Check Parameters:** Ensure `@param`, `@return`, and `@throws` tags are present where applicable.
4.  **Verify:** Run `javadoc` to ensure no generation errors occur.

---

## Prompt 12: Performance Investigation
**Objective:** Identify and optimize performance bottlenecks in the plugin's execution.

**Instructions:**
1.  **Profile:** Use the IntelliJ Profiler or `VisualVM` while running the plugin to capture CPU and memory snapshots.
2.  **Analyze Hotspots:** Look for expensive operations on the EDT (Event Dispatch Thread) or frequent object allocations.
3.  **Optimize:**
    *   Cache expensive results.
    *   Use `ReadAction.nonBlocking` for long-running read operations.
    *   Optimize collections (e.g., use `SmartList` or `ContainerUtil` maps).
4.  **Verify:**
    *   Re-profile to confirm the performance improvement.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

---

## Prompt 13: Unused Asset Cleanup
**Objective:** Reduce plugin size by removing unused icons, images, and resources.

**Instructions:**
1.  **Scan Assets:** List all files in `resources/icons` and other asset directories.
2.  **Search Usages:** Use "Find Usages" (Alt+F7) or `grep` to check if each asset is referenced in the code or `plugin.xml`.
3.  **Remove:** Delete assets with zero references.
4.  **Verify:** Run `./gradlew buildPlugin` and check that the plugin still loads correctly without missing resource exceptions.
