# PR Intake Gate Specifications

This document defines the technical specifications, evaluation criteria, and remediation procedures for each of the pre-flight PR intake gates in `flutter-intellij`.

---

## Gate 1: CHANGELOG Entry Validation

### Objective
Maintain a clear, user-facing history of plugin enhancements, changes, and fixes for Flutter developers. Prevent unannounced regressions or undocumented features.

### Scope & Criteria
- **Trigger**: Any modification to user-facing plugin source code, resources, templates, or tool integrations (primarily files under `src/`, `resources/`, or plugin descriptors).
- **Location**: `CHANGELOG.md` under the top-level section:
  ```markdown
  ## Unreleased
  ```
- **Allowed Categories**:
  - `### Added`: New user-visible actions, wizards, inspector features, or tool integrations.
  - `### Changed`: Modifications to existing behavior, UI changes, SDK detection updates, or deprecations.
  - `### Removed`: Deprecated features, removed SDK version support, or deleted legacy tools.
  - `### Fixed`: Bug fixes, freeze/EDT crash resolutions, analysis server reconnect fixes, or IDE compatibility patches.
- **Entry Format**:
  ```markdown
  - Clear, concise description of the user-facing change in past/present tense. (#IssueOrPR)
  ```

### The "No Changelog" Exception Rule
Not every pull request requires a changelog entry. When a pull request touches:
- Unit, integration, or UI tests (`testSrc/`, `test/`)
- Internal build logic, Gradle scripts, or CI workflows (`build.gradle.kts`, `.github/`)
- Documentation, READMEs, agent guidelines, or skills (`.agents/`, `docs/`)
- Pure internal refactorings with no user-visible behavioral changes

Then `CHANGELOG.md` should **not** be modified. In this case, the author/agent **must** document the explicit reason in the Git commit message using the following tag:
```
NO CHANGELOG: <concise explanation of why change is not user-facing>
```
*Examples:*
- `NO CHANGELOG: Internal test cleanup and added unit tests for ActionUpdateThread.`
- `NO CHANGELOG: CI workflow optimization for GitHub Actions presubmit.`
- `NO CHANGELOG: Refactored internal PSI helper without behavioral change.`

---

## Gate 2: Plugin Verification (`./gradlew verifyPlugin`)

### Objective
Ensure that the plugin code compiles and runs reliably without binary incompatibilities, NoSuchMethodError/NoClassDefFoundError crashes, or unmitigated deprecations across all supported IntelliJ Platform and Android Studio releases.

### Verification Command
Run the plugin verifier locally:
```bash
./gradlew verifyPlugin
```

### Evaluation Criteria
1. **0 Compatibility Errors**: The verifier must report zero binary or source incompatibilities against all target platform versions.
2. **Platform Deprecations**:
   - Modern IntelliJ platform releases deprecate legacy APIs (e.g., `ReadAction.compute(ThrowableComputable)`, older project open listeners, legacy action update threads).
   - Changes must not introduce new deprecated API usages unless required to maintain compatibility across an older supported baseline.
   - When a deprecated API must be preserved for backward compatibility across releases (e.g. supporting both older IDE releases and modern EAPs):
     - The deliberate trade-off must be documented in the commit message and in code comments.
     - Safer cross-version wrappers (e.g. `ApplicationManager.getApplication().runReadAction(...)`) should be used where available.
3. **Internal API Usages**:
   - Usage of IntelliJ Platform `@ApiStatus.Internal` or `@ApiStatus.Experimental` APIs should be avoided or replaced with public API equivalents.
   - Any unavoidable internal API usages must be quantified and documented.

### Commit Message Requirement
The commit message must include a verifier summary line:
```
Plugin Verifier: 0 errors, <N> warnings (verified on target platform releases)
```

---

## Gate 3: Copyright Headers

### Objective
Ensure legal compliance and open-source attribution according to the Chromium and Flutter repository license guidelines.

### Evaluation Criteria
Every newly added file in the PR (`git diff --name-only --diff-filter=A`) must begin with the standard Chromium BSD copyright header.

### Supported Language Formats

#### Java and Kotlin (`.java`, `.kt`)
```java
/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
```

#### Dart (`.dart`)
```dart
// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
```

#### Shell and Bash (`.sh`)
```bash
#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
```

#### XML (`.xml`)
```xml
<!--
  Copyright 2026 The Chromium Authors. All rights reserved.
  Use of this source code is governed by a BSD-style license that can be
  found in the LICENSE file.
-->
```

#### Properties (`.properties`)
```properties
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
```

*Note: Ensure the year matches the year the file was created (e.g. 2026).*

---

## Gate 4: PR Size & Complexity Budget

### Objective
Maintain review velocity, prevent reviewer cognitive overload, minimize merge conflicts, and keep Git history bisectable. Large diffs are statistically correlated with higher defect escape rates.

### Thresholds
- **Lines Changed**: Calculated as total additions + deletions via `git diff --stat`.
- **Standard PR**: $\le$ 500 lines changed.
- **Larger PR**: > 500 lines changed.

### Requirements for Larger PRs (>500 lines)
If a pull request exceeds 500 lines of change:
1. **Commit Message Header**: The Git commit message **must** contain:
   ```
   LARGER PR: XXXX lines of change
   ```
2. **Architectural Justification**: The commit message and PR description must explain why this changeset could not be broken down into smaller, sequential PRs (e.g., massive file rename, code generator update, monolithic API migration).
3. **Reading Guide**: The PR description must provide reviewers with a suggested order of review (which files to read first, which are boilerplate/generated).

---

## Gate 5: Test Integrity & Anti-Weakening Guard

### Objective
Defend test suite reliability and prevent test regression. Coding agents and contributors must never weaken test assertions or remove existing tests to achieve green CI.

### Forbidden Practices (Instant Gate Failure)
1. **Test Deletion**: Deleting existing unit test files or `@Test` methods rather than fixing the underlying regression.
2. **Test Disabling**: Adding `@Ignore`, `@Disabled`, or commenting out failing test methods without explicit codeowner authorization and a tracking issue.
3. **Weakened Assertions**:
   - Replacing strict equality checks (e.g., `assertEquals(expected, actual)`) with tautologies (e.g., `assertTrue(actual != null)` or `assertTrue(true)`).
   - Widening regex or tolerance bounds simply to accommodate unexpected output.
   - Deleting assertions that fail intermittently.
4. **Silent Exception Catching**:
   - Wrapping failing test logic in empty `try { ... } catch (Exception ignored) {}` blocks.

### Mandatory Positive Requirements
- **Bug Fixes**: Every bug fix must be accompanied by a reproducing test case in `testSrc/` demonstrating that the bug is fixed and cannot regress.
- **New Features**: Every new action, service, or parser must have unit tests covering both happy paths and edge cases (e.g., null inputs, project disposal, cancelled progress monitors).

---

## Gate 6: AI / Bot Review Finding Triage

### Objective
Ensure that automated static analysis, linter bots, and AI reviewers (such as Gemini Code Assist) are respected as an effective pre-flight filter rather than dismissed.

### Scope & Criteria
Whenever a draft PR is open and automated bots post comments:
- **`[MUST-FIX]` Findings**:
  - Represent logic bugs, potential threading deadlocks (e.g. running heavy operations on EDT without ReadAction), resource leaks, or null-pointer risks.
  - **Action**: Must be fixed in code. If the finding is based on an incorrect static assumption, it must be answered with `[FALSE-POSITIVE]` and a technical proof.
- **`[CONCERN]` Findings**:
  - Represent architectural friction, code duplication, or platform best-practice deviations.
  - **Action**: Must be fixed or answered with `[INTENTIONAL]` describing the deliberate trade-off.
- **`[NIT]` Findings**:
  - Optional naming or stylistic feedback; author discretion.

*Silent dismissal (clicking 'Resolve' without replying or fixing) fails this gate.*
See [Bot Triage Guide](BOT_TRIAGE_GUIDE.md) for detailed reply syntax.
