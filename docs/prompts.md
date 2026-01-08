# AI Agent Prompts

This document serves as the authoritative "Runbook for Robots," codifying standard operating procedures into executable prompts for AI agents. It operationalizes "Prompt Engineering as Infrastructure," treating these instructions not as casual suggestions but as critical configuration that ensures deterministic and hermetic development environments. By strictly adhering to these workflows, agents are grounded in the project's "Sensory Input"—reliable metrics like exit codes, code coverage, and static analysis—rather than operating on assumptions. This approach enforces a "Verify and Harden" loop where every task is validated against rigorous testing suites and lints, ensuring that the increased velocity from AI tools creates stability rather than technical debt. We define success through finite, idempotent scopes, allowing agents to confidently maintain, refactor, and evolve the codebase while always preserving a "Known Good" state.

> [!NOTE]
> This is an organic document. As developers use these prompts, they should iterate on the instructions to improve them for future uses, ensuring the prompts evolve alongside the codebase and tooling. If you find a better way to do something, or if an instruction is outdated, please update this file.

## Prepare current work for PR: Verify Tests Passing and Cleanup
**Objective:** Verify the current test suite status with `./gradlew test`, clean up any temporary modifications, and harden test coverage for active files.

**Instructions:**
1.  **Baseline:**
    *   Run `./gradlew testClasses` to ensure the project compiles.
    *   Run `./gradlew test` to establish the current passing state.
    *   Run `./gradlew verifyPlugin` to ensure no existing verification issues.
2.  **Fix Failures:** If there are any test failures or compilation errors, investigate and resolve them. Priorities fixing code over deleting tests.
3.  **Cleanup:** Review any currently modified files (run `git status` or check the diff). Remove any:
    *   System.out.println / debug print statements.
    *   Unused imports.
    *   Commented-out code blocks.
    *   Temporary "hack" fixes that should be replaced with proper solutions.
4.  **Verify & Expand:**
    *   For the files you touched or cleaned up, check if there are obvious edge cases missing from their unit tests. Add JUnit tests to cover these cases.
    *   Run `./gradlew test` again to ensure cleanup didn't break anything.
    *   Run `./gradlew verifyPlugin` to ensure strict compliance.
5.  **Report & Review:**
    *   Summarize the cleanup status (e.g., "Tests passing, removed 3 debug prints").
    *   **Action:** Ask the user to review the changes closely to ensure no intended code was accidentally removed.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Prepare for PR: Fix tests and remove debug code").

---

## Single File Test Coverage Improvement
**Objective:** Write a new single test file, or modify an existing file, to improve coverage for a specific target class.

**Instructions:**
1.  **Identify Target:** Choose a single source file (Java or Kotlin) in `src/` that has low or no test coverage and is suitable for unit testing (e.g., utility classes, helpers).
3.  **Establish Baseline:**
    *   Run `./gradlew testClasses` to ensure the project compiles.
    *   Run `./gradlew test` to ensure the project is stable.
    *   Run `./gradlew verifyPlugin` to ensure no verification issues exist.
    *   Run tests with Kover to determine the current coverage percentage for this file.
4.  **Implement/Update Test:** Create a new test file in `testSrc/` or update the existing one. Focus on:
    *   Edge cases (null inputs, empty strings, boundary values).
    *   Branch coverage (ensure if/else paths are exercised).
    *   Mocking dependencies where necessary.
5.  **Verify & Iterate:**
    *   Run the tests to ensure they pass and check the coverage increase.
    *   Run `./gradlew verifyPlugin` to ensure no regressions.
    *   If coverage is still low, **iterate a few times**: analyze missed lines/branches in the Kover report and add targeted test cases to improve it further.
5.  **Report & Review:**
    *   Summarize what was fixed/covered and report Kover progress (e.g., `X% -> Y%` for `<filename>`).
    *   **Action:** Ask the user to review the new tests closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Improve test coverage for [Class Name]").

---

## Update org.jetbrains.intellij.platform Gradle Plugin
**Objective:** Update the `org.jetbrains.intellij.platform` plugin version to the latest available.

**Instructions:**
1.  **Baseline:**
    *   Run `./gradlew buildPlugin` to establish a baseline.
    *   Run `./gradlew test` to ensure project stability.
    *   Run `./gradlew verifyPlugin` to check for existing warnings.
2.  **Create a new branch** off of `main` for this update.
3.  **Find the new version name and URL**:
    *   Open `https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform` in Chrome.
    *   Identify the latest version number.
4.  **Make the change**:
    *   Update the version in `third_party/build.gradle.kts` (look for `id("org.jetbrains.intellij.platform") version "..."`).
5.  **Validate**:
    *   Run `./gradlew buildPlugin` again to ensure the build succeeds.
    *   Run `./gradlew test` to ensure everything is still working.
    *   Run `./gradlew verifyPlugin` to check for any new verification issues.
    *   **Crucial**: If any tests fail, verify if they are pre-existing failures by running them on the `main` branch (or the version before your changes). Only failures introduced by the update need to be fixed.
6.  **Report & Review:**
    *   Summarize the update (old version -> new version).
    *   **Action:** Ask the user to review the build files closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Update org.jetbrains.intellij.platform to [Version]").

---

## Resolve Plugin Verification Issues
**Objective:** Eliminate plugin verification warnings and errors (Internal API usage, Override-only API usage, etc.).

**Instructions:**
1.  **Baseline:**
    *   Run `./gradlew testClasses` to ensure project compiles.
    *   Run `./gradlew test` and `./gradlew verifyPlugin` to establish a clean baseline.
2.  **Analyze Report:** Identify distinct issues to resolve (e.g., "Override-only API usage in ClassX").
3.  **Implement Fix:**
    *   Resolve the specific issue in the current branch.
    *   Ensure the fix is minimal and targeted.
4.  **Verify Fix:**
    *   Run `./gradlew testClasses` to ensure no new compilation errors.
    *   Run `./gradlew verifyPlugin` again to confirm the specific warning is gone and quantify the improvement.
    *   Run `./gradlew test` to ensure no regressions.
5.  **Validation & Manual Testing:**
    *   Explain how to verify the fix manually by running `./gradlew runIde`.
    *   Provide specific navigation steps for the user to reach the changed code within the plugin (e.g. "Open Settings > Languages & Frameworks > Flutter...").
    *   Verify that the changes do not introduce runtime errors or regressions.
6.  **Report & Review:**
    *   Summarize the remaining `verifyPlugin` issues (e.g., "Total remaining issues: X compatibility warnings, Y internal API usages").
    *   Confirm that `./gradlew runIde` validation passed.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
    *   **Action:** Ask the user to review the fix closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message including specific counts (e.g., "Fix Internal API usage in ClassX: 5 experimental APIs used -> 4 experimental APIs used").

---

## Gradle Build Optimization
**Objective:** Improve local build performance and CI efficiency by leveraging Gradle's caching and profiling tools.

**Instructions:**
1.  **Read Documentation:** Before starting, read the official Gradle documentation to understand best practices:
    *   [Performance Guide](https://docs.gradle.org/current/userguide/performance.html)
    *   [User Guide](https://docs.gradle.org/current/userguide/userguide.html)
2.  **Profile Build:** Run `./gradlew clean build --profile` to identify slow tasks and bottlenecks.
3.  **Enable Caching:** Check `gradle.properties` for `org.gradle.caching=true`. If missing, add it to enable the Build Cache. Check for other opportunities as well with any changes to Gradle.
4.  **Configuration Cache:** Run `./gradlew help --configuration-cache` to check for compatibility. If feasible, enable it to speed up the configuration phase.
5.  **Update Wrapper:** Check if a newer stable Gradle version is available and update the wrapper using `./gradlew wrapper --gradle-version [VERSION]`.
6.  **Verify:**
    *   Run a clean build to ensure optimizations didn't break the build process.
7.  **Report & Review:**
    *   Summarize the optimizations applied.
    *   **Action:** Ask the user to review the changes closely, especially `gradle.properties`.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Optimize Gradle build: Enable caching and update wrapper").

---

## Plugin Compatibility & EAP Verification
**Objective:** Ensure the plugin remains compatible with the latest IntelliJ Platform releases and EAP (Early Access Program) builds.

**Instructions:**
1.  **Target EAP:** Temporarily update `gradle.properties` to target the latest IntelliJ Platform EAP version.
2.  **Run Verification:**
    *   Run `./gradlew testClasses` to ensure the project compiles against the EAP.
    *   Execute `./gradlew verifyPlugin` to check for binary incompatibilities or broken API usages in the new version.
    *   Run `./gradlew test` to check for functional regressions with the EAP.
3.  **Check Bounds:** Review `plugin.xml` for `<idea-version since-build="..." until-build="..."/>`. Ensure `until-build` is open-ended or appropriately set for the new version.
4.  **Resolve Issues:** Fix any compilation errors or verification warnings specific to the new platform version.
5.  **Report & Review:**
    *   Summarize any required changes or blockers for supporting the new version.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
    *   **Action:** Ask the user to review the compatibility fixes closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Fix EAP compatibility: Resolve invalid until-build").

---

## UI Thread Safety & Responsiveness
**Objective:** Prevent UI freezes and ensure a responsive user experience by validating threading rules.

**Instructions:**
1.  **Baseline:** Run `./gradlew test` and `./gradlew verifyPlugin` to ensure the project is in a good state before refactoring.
2.  **Audit EDT Usage:** Search for usages of `runInEdt`, `invokeLater`, and `invokeAndWait`.
2.  **Identify Blocking Calls:** Ensure no I/O operations (file access, network calls) or heavy computations occur within these UI blocks.
3.  **Migrate to Background:** Refactor heavy tasks to run on background threads using `Task.Backgroundable` or `ReadAction.nonBlocking`.
4.  **Check Modality:** Verify that modal dialogs do not block the UI thread unnecessarily.
5.  **Verify:**
    *   Run `./gradlew testClasses` to ensure safe refactoring.
    *   Run `./gradlew test` and `./gradlew verifyPlugin` to ensure no regressions.
    *   Run the plugin in a sandbox (`./gradlew runIde`) and perform key actions while monitoring for UI freezes.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.
6.  **Report & Review:**
    *   Summarize the threading fixes.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
    *   **Action:** Ask the user to review the changes closely to ensure no deadlocks or UI freezes are introduced.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Fix UI freeze: Move heavy computation to background task").

---

## Code Inspection & Cleanup
**Objective:** Reduce technical debt and improve code quality by systematically resolving static analysis warnings.

**Instructions:**
1.  **Baseline:** Run `./gradlew test` and `./gradlew verifyPlugin` to ensure the project is stable.
2.  **Run Inspection:** Execute `Analyze > Inspect Code...` on the project (or a specific module) using the "Default" profile.
2.  **Prioritize:** Focus first on "Probable bugs", "Performance", and "Memory" categories.
3.  **Refactor:** Apply quick-fixes for obvious issues (e.g., "Result of method call ignored", "String concatenation in loop").
4.  **Suppress:** Explicitly suppress false positives with a comment and reason (e.g., `//noinspection [ID] - [Reason]`).
5.  **Verify:**
    *   Run `./gradlew testClasses` to ensure safe refactoring.
    *   Run `./gradlew test` and `./gradlew verifyPlugin` to ensure no regressions.
    *   Re-run analysis to ensure the "Yellow code" count has decreased and no regressions were introduced.
6.  **Report & Review:**
    *   Summarize the inspections resolved.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
    *   **Action:** Ask the user to review the changes closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Cleanup: Resolve static analysis warnings in [Module]").

---

## Accessibility (A11y) Audit
**Objective:** Ensure the plugin's custom UI components are accessible to users with screen readers and other assistive technologies.

**Instructions:**
1.  **Baseline:**
    *   Run `./gradlew testClasses` to ensure project compilation.
    *   Run `./gradlew test` to ensure the project is stable.
    *   Run `./gradlew verifyPlugin` to ensure no verification issues.
2.  **Identify Custom UI:** List all custom `JPanel`, `JComponent`, or `Dialog` classes in the codebase.
3.  **Check Properties:** Verify that every interactive component has:
    *   `getAccessibleContext().setAccessibleName(...)`
    *   `getAccessibleContext().setAccessibleDescription(...)`
4.  **Focus Management:** Ensure custom components handle focus traversal correctly (Tab/Shift+Tab).
5.  **Color Contrast:** If custom colors are used, verify they meet WCAG contrast guidelines (especially for dark themes).
6.  **Verify:**
    *   Run `./gradlew testClasses` and `./gradlew test` to ensure no regressions.
    *   Run `./gradlew verifyPlugin` to ensure strict compliance.
    *   Use the "Accessibility Inspector" (if available in the SDK) or a screen reader to navigate the UI.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.
6.  **Report & Review:**
    *   Summarize the accessibility improvements in the form of a git commit message.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
    *   **Action:** Ask the user to review the UI changes closely.
    *   **Do not commit or push.**

---

## Dependency & Library Audit
**Objective:** Optimize plugin size and security by removing unused dependencies and updating outdated libraries.

**Instructions:**
1.  **Baseline:** Run `./gradlew test` and `./gradlew verifyPlugin` to ensure the project is stable.
2.  **Audit `build.gradle`:** Review `dependencies {}` block. Identify libraries that might no longer be used.
2.  **Check `plugin.xml`:** Ensure all `<depends>` tags match the actual API usage.
3.  **Update Versions:** Check for newer stable versions of third-party libraries (e.g., using `./gradlew dependencyUpdates` if configured, or manual check).
4.  **Remove Unused:** Delete any local `.jar` files in `lib/` that are not referenced or can be replaced by Maven coordinates.
5.  **Verify:**
    *   Run `./gradlew buildPlugin` and check the distribution size. Ensure the plugin still loads and functions correctly.
    *   Run `./gradlew testClasses`, `./gradlew test`, and `./gradlew verifyPlugin` to ensure no checking or runtime issues were introduced.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.
6.  **Report & Review:**
    *   Summarize the dependencies removed or updated.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
    *   **Action:** Ask the user to review the `build.gradle` changes closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Cleanup: Remove unused dependency [Name]").

---

## Documentation Coverage
**Objective:** Ensure all public classes and methods in `src/` have Javadoc/KDoc to improve maintainability and API clarity.

**Instructions:**
1.  **Baseline:**
    *   Run `./gradlew testClasses` to ensure project compiles.
    *   Run `./gradlew test` to ensure stability.
    *   Run `./gradlew verifyPlugin` to ensure no preexisting verification issues.
2.  **Select Batch:** Identify a batch of **up to 5** files in `src/` with missing public documentation.
3.  **Add Docstrings:** Write concise descriptions for missing items (`/** ... */` or `///`), explaining *what* they do and *why* using expected Java and Kotlin documentation standards.
4.  **Check Parameters:** Ensure `@param`, `@return`, and `@throws` tags are present where applicable.
5.  **Verify:**
    *   Inspect the changes to ensure accuracy.
    *   Run `./gradlew testClasses` to ensure documentation changes didn't break compilation (e.g. invalid refs).
    *   Run `./gradlew verifyPlugin` to ensure no compliance issues.
5.  **Report & Review:**
    *   Summarize the changes made (rationale for the documentation added).
    *   **Action:** Ask the user to review the documentation closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Add Javadoc to [Component Name] utility classes").

---

## API Migration to use com.intellij.util.* classes and methods
**Objective:** Go read https://javadoc.jetbrains.net/teamcity/openapi/current/com/intellij/util/package-summary.html, and then optimize memory usage, consistency, and performance by migrating standard Java/Kotlin classes to IntelliJ's specialized `com.intellij.util` implementations.

**Candidates for Migration:**
*   `ArrayList` -> `SmartList` (for potentially empty/small lists)
*   `HashMap` / `ConcurrentHashMap` -> `ContainerUtil` maps
*   `String` manipulation -> `StringUtil` (e.g., `isEmpty`, `join`, `notNullize`)
*   `Path` / `File` string manipulation -> `PathUtil`
*   `javax.swing.Timer` -> `Alarm` (for UI-related callbacks/debouncing)
*   Null checks -> `ObjectUtils` (e.g., `doIfNotNull`, `coalesce`)
*   Array resizing/merging -> `ArrayUtil`

**Instructions:**
1.  **Baseline:** Run `./gradlew testClasses`, `./gradlew test`, and `./gradlew verifyPlugin` to ensure project is in a good state.
2.  **Select Target:** Identify a *single* migration opportunity within a *single package*.
3.  **Constraint:** Keep the change extremely small (**max 50 lines of code change**).
4.  **Migrate:** Replace the standard API with the `com.intellij.util` equivalent.
5.  **Verify:**
    *   Run `./gradlew testClasses` to ensure the migration didn't break test compilation.
    *   Run `./gradlew test` and `./gradlew verifyPlugin` to ensure no regressions.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.
6.  **Report & Review:**
    *   Summarize the migration.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
    *   **Action:** Ask the user to review the changes closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Migrate code to use StringUtil in [Class Name]").

---

## Unused Asset Cleanup
**Objective:** Reduce plugin size by removing unused icons, images, and resources.

**Instructions:**
1.  **Baseline:** Run `./gradlew test` and `./gradlew verifyPlugin` to ensure the project is stable.
2.  **Scan Assets:** List all files in `resources/icons` and other asset directories.
2.  **Search Usages:** Use "Find Usages" (Alt+F7) or `grep` to check if each asset is referenced in the code or `plugin.xml`.
3.  **Remove:** Delete assets with zero references.
4.  **Verify:**
    *   Run `./gradlew buildPlugin` and check that the plugin still loads correctly without missing resource exceptions.
    *   Run `./gradlew testClasses`, `./gradlew test` and `./gradlew verifyPlugin` to ensure no regressions.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.
5.  **Report & Review:**
    *   Summarize the assets removed.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
    *   **Action:** Ask the user to review the deletions closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Cleanup: Remove unused icon [Icon Name]").

---

## Add Missing Unit Test
**Objective:** Add a new unit test for a class that currently lacks one, or add a new test case to an existing test file, to improve code coverage and reliability.

**Instructions:**
1.  **Identify Target:** Choose a class in `src/` that has complex logic but logic coverage is missing or incomplete.
2.  **Establish Baseline:**
    *   Run `./gradlew testClasses` to compile all sources (production and test) and ensure the project is in a valid state.
    *   Run `./gradlew verifyPlugin` to ensure no plugin verification issues exist.
    *   Run `./gradlew test` (or the specific test if it exists) to establish the current pass/fail status.
    *   Run tests with Kover (e.g., `./gradlew koverHtmlReport` or `./gradlew koverXmlReport`) to determine the current coverage percentage for the target class.
3.  **Implement Test:**
    *   Create a new test class (e.g., `[ClassName]Test`) in `testSrc/` if it doesn't exist.
    *   Add test cases to cover specific methods, edge cases, and branches.
    *   Ensure the test follows the project's testing conventions (naming, mocking, assertions).
4.  **Verify:**
    *   Run `./gradlew testClasses` to ensure the new test compiles.
    *   Run the new test (and related tests) to ensure it passes.
    *   Run `./gradlew verifyPlugin` to ensure no regressions in plugin verification.
    *   Run Kover again to confirm the coverage improvement.
5.  **Report & Review:**
    *   Summarize the test added and the coverage improvement.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Run the new test file `[TestFileName]`").
    *   **Action:** Ask the user to review the new test closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message, **explicitly including the Kover coverage improvement**.
        *   Example Pattern: `Add unit tests for [Class Name]. Coverage verified using Kover. Coverage improvement: [Class Name]: ~[X]% line coverage ([Y] covered, [Z] uncovered).`

---

## Fix Specified Issue
**Objective:** Fix a specific issue provided by the user (stack trace or GitHub issue).

**Instructions:**
1.  **Setup & Context:**
    *   Read the basics of IntelliJ plugin development here: [https://plugins.jetbrains.com/docs/intellij/developing-plugins.html](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html).
    *   **Input Required:** If the user has not provided a stack trace or GitHub issue link, **ask for it now**. Do not proceed without a specific issue to reproduce and fix.
2.  **Reproduction:**
    *   Create a minimal reproduction case if possible, or identify the code path responsible for the crash/issue.
    *   **Establish Baseline:**
        *   Run `./gradlew testClasses` to ensure the project compiles.
        *   Run `./gradlew test` to ensure the current state is stable (or to confirm the failure if you wrote a repro test).
        *   Run `./gradlew verifyPlugin` to check for existing verification issues.
        *   Run tests with Kover to determine the current coverage percentage.
3.  **Implement Fix:**
    *   Analyze the stack trace or issue description.
    *   Apply the fix in the codebase.
    *   Ensure the fix is minimal and targeted.
    *   Add a unit test if needed.
4.  **Verify:**
    *   Run `./gradlew testClasses` to ensure the fix compiles.
    *   Run `./gradlew test` to ensure no regressions and that the repro test (if created) now passes.
    *   Run `./gradlew verifyPlugin` to ensure no new verification issues were introduced.
    *   Run Kover again to confirm coverage is sustained or improved.
    *   Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.
5.  **Report & Review:**
    *   Summarize the fix applied.
    *   **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality/verify the fix.
    *   **Action:** Ask the user to review the fix closely.
    *   **Do not commit or push.**
    *   Provide a suggested Git commit message (e.g., "Fix [Issue ID/Description]: [Summary of Fix]").

