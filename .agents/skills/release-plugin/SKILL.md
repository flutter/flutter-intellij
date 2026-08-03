---
name: release-plugin
description: Prepare and execute a new release for the flutter-intellij plugin, including updating dependencies, compatibility bounds, changelogs, and verification.
---

# Skill: Release Plugin

You are tasked with preparing a new version release of the `flutter-intellij` plugin (e.g., releasing `95.0.0`).

## Objective
Systematically update plugin dependencies, compatibility parameters, and `CHANGELOG.md`, verify tests and plugin structure, and build the release zip artifact.

---

## Workflow Instructions

### 1. Update Platform & Dependency Parameters
- **`gradle.properties`**:
  - Update `dartPluginVersion` to the latest public Dart plugin release version. Fetch the latest version from JetBrains Marketplace (e.g. via `curl -s "https://plugins.jetbrains.com/api/plugins/6351/updates?size=1"` or by checking [JetBrains Marketplace Dart Plugin](https://plugins.jetbrains.com/plugin/6351-dart)) and set `dartPluginVersion` in `gradle.properties` to that version (e.g., `dartPluginVersion= 508.0.0`).
  - If dropping support for older platform versions, update `sinceBuild` to the new lower bound (e.g., `sinceBuild=252`). Note: `untilBuild` is omitted for open-ended platform compatibility.
- **New Platform Version Compatibility & Baselines**:
  - When supporting/verifying a new platform release (e.g., IntelliJ 2026.3 / build `263`):
    - Update the verification loop in `tool/github.sh` (`for version in ...; do`).
    - Run `./tool/update_baselines.sh` to generate or update the plugin verifier baseline files in `tool/baseline/<version>/verifier-baseline.txt`.
- **JxBrowser License Key**:
  - Ensure `resources/jxbrowser/jxbrowser.properties` is present (copy from local environment if creating a new git worktree).

### 2. Fix API & Dependency Compatibility
- Check for compilation errors or API signature changes introduced by the updated dependencies (e.g., websocket/DTD exception handling changes in `FlutterInitializer.java` and `FlutterApp.java`).
- Run `./gradlew testClasses` to confirm the codebase compiles against updated dependencies.

### 3. Update Changelog (`CHANGELOG.md`)
- **Compatibility Changes PR**:
  - Document compatibility updates under `### Changed` in `CHANGELOG.md` with the PR reference (e.g., `- Removed upper build constraint (\`untilBuild\`) for open-ended platform compatibility. (#9063)`).
- **Release Version Bump**:
  - Rename `## Unreleased` to `## <VERSION>.0.0` (e.g., `## 95.0.0`).
  - Ensure all user-facing changes since the last release are categorized and tagged with PR numbers.
  - Insert a fresh empty `## Unreleased` block at the top with:
    ```markdown
    ## Unreleased

    ### Added

    ### Changed

    ### Removed

    ### Fixed
    ```

### 4. Build & Validation
- Run `./gradlew testClasses` to verify Java/Kotlin compilation.
- Run `./gradlew verifyPluginStructure` to validate `plugin.xml` structure.
- Run `./gradlew test` to ensure all unit tests pass.
- Run `./gradlew buildPlugin -Prelease -PversionedName` to build the prospective release plugin zip artifact (`Flutter-<VERSION>-<COMMIT>.zip`) in `build/distributions/`.
- **Upload Prospective Zip**: Manually upload the generated release zip (`Flutter-<VERSION>-<COMMIT>.zip`) from `build/distributions/` to the team's Google Drive folder (e.g. DevExp Flutter Releases) for team-wide manual testing prior to public publishing.

### 5. Manual Testing Checklist
Perform manual sanity checks across **every supported platform version** (e.g. IntelliJ IDEA 2025.3, 2026.1, 2026.2 and Android Studio 2025.3 / Meerkat):
- [ ] **Create New Project**: Verify new Flutter project wizard succeeds.
- [ ] **Open Existing Project**: Verify existing Flutter project opens cleanly.
- [ ] **Run & Debug**: Run app, hit breakpoints, and inspect variables.
- [ ] **Hot Reload & Hot Restart**: Perform **Hot Reload** (⚡) and **Hot Restart** on a running app.
- [ ] **DevTools**: Open DevTools tool windows (Inspector, Memory, Network, etc.).
- [ ] **Version-Specific Features**: Verify any release-specific fixes or changes (e.g. Flutter SDK path changes in Settings, DTD theme synchronization, EAP installation compatibility).
