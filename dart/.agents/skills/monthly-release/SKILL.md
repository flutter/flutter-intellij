---
name: monthly-release
description: Step-by-step guide for performing monthly releases of the Dart IntelliJ plugin.
---

# Monthly Plugin Release Process

Use this skill when preparing, testing, validating, and publishing a new monthly release of the Dart IntelliJ plugin (`dart-intellij-third-party`).

> [!NOTE]
> **Platform Compatibility Policy**: `untilBuild` is intentionally omitted in `gradle.properties` and `build.gradle.kts` to maintain open-ended compatibility with future IDE builds without artificial version caps. Do NOT add `untilBuild` caps during monthly releases.

---

## Release Process Checklist

### 1. Update Changelog for the Release
- **Branch**: `changelog` (created off `upstream/main`)
- **Goal**: Collect unreleased user-facing PRs into the release version header and set up a new `## Unreleased` block.
- **Steps**:
  1. Review commits merged since the previous release (`git log <last-release-commit>..upstream/main`).
  2. Identify missing user-facing PRs (bug fixes, new features, performance enhancements).
  3. In `third_party/CHANGELOG.md`, rename `## Unreleased` to `## <new-version>` (e.g. `## 508.0.0`).
  4. Follow changelog formatting conventions:
     - Entries must strictly use descriptive, state-based phrases (typically starting with gerunds, nouns, or verbs like *Avoid* / *Support* / *Log* / *Prevent*) rather than starting with imperative verbs like *Fix* or *Add*.
     - Under `### Removed`, do NOT repeat action verbs like "Remove" (e.g., `- untilBuild restriction (#553)`).
  5. Add a fresh empty `## Unreleased` section at the top of `third_party/CHANGELOG.md` with standard subheaders (`### Added`, `### Changed`, `### Removed`, `### Fixed`).
  6. Create PR (e.g. `Update changelog for <version> (#<PR>)`) and merge it to `main`.
  7. Delete local and remote `changelog` branch upon merge (`git branch -D changelog`).

### 2. Build & Validate
- **Compilation & Structure Verification**:
  1. Ensure local `main` is checked out and updated (`git fetch upstream && git checkout main && git reset --hard upstream/main`), and navigate to the `third_party` directory (`cd third_party`).
  2. Run compilation check:
     ```shell
     ./gradlew testClasses
     ```
  3. Run plugin structure validation:
     ```shell
     ./gradlew verifyPluginStructure
     ```
  4. Run unit tests:
     ```shell
     ./gradlew test
     ```
- **Build Prospective Release Zip Artifact**:
  1. Run the Gradle build with `-PversionedName` (from the `third_party` directory):
     ```shell
     ./gradlew buildPlugin -PversionedName
     ```
  2. **Behavior**:
     - Gradle reads the release version (`<version>`) directly from `CHANGELOG.md`.
     - The `-PversionedName` flag instructs `build.gradle.kts` to output `Dart-<version>-<commitHash>.zip`.
     - Generated zip location: `third_party/build/distributions/Dart-<version>-<commitHash>.zip`.

### 3. Upload Release Candidate to Google Drive
- **Location**: [Devexp folder releases > Dart plugin builds](https://drive.google.com/corp/drive/folders/14PIy3OCZW5WBJjjFeKcqTXTf1ZhXAUCY?resourcekey=0-0WZ8mFRUYRhQzRw9UgQLQQ)
- **Goal**: Upload `Dart-<version>-<commitHash>.zip` to the designated Google Drive folder for team testing prior to public Marketplace release.

### 4. Perform Manual Testing Across IDE Versions
Perform manual sanity checks across all supported IDE platform versions (IntelliJ IDEA Ultimate/Community and Android Studio):
- **Baseline Smoke Tests**:
  - [ ] **Create New Project**: Verify new Dart project creation succeeds.
  - [ ] **Open Existing Project**: Verify existing Dart project opens cleanly.
  - [ ] **Run & Debug**: Run a Dart app/script, hit breakpoints, inspect variables.
- **Targeted Release Tests**:
  - [ ] Verify all new features and bug fixes introduced in the release (e.g. LSP Go to Definition navigation, LSP diagnostics server error clearing, Windows path URI casing, DTD connection ping interval resilience).
  - [ ] Verify installation on latest EAP / Canary IDE builds without version incompatibility warnings.
