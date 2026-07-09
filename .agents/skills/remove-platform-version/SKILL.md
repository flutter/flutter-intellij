---
name: remove-platform-version
description: Remove support for an older IntelliJ Platform / Android Studio version from the project and clean up obsolete code, baselines, and CI configurations.
---

# Skill: Remove Platform Version Support

You are tasked with removing support for an older IntelliJ Platform or Android Studio version (e.g., dropping support for `2025.1` / build `251` and raising the lower bound to `2025.2` / build `252`).

## Objective
Systematically update compatibility ranges, clean up CI verification loops and baseline files, identify and remove obsolete compatibility code or TODOs, check repository issues, and update documentation.

---

## Workflow Instructions

### 1. Establish Baseline & Identify Target Version
- Identify the build number being removed (e.g., `251` for `2025.1`) and the new lower bound (e.g., `252` for `2025.2`).
- Run `./gradlew testClasses` to ensure the project compiles before making changes.

### 2. Update Compatibility Lower Bound (`gradle.properties`)
- Open `gradle.properties`.
- Update `sinceBuild=<version>` to the new lower bound build number (e.g., change `sinceBuild=251` to `sinceBuild=252`).

### 3. Update CI Verification Script (`tool/github.sh`)
- Open `tool/github.sh`.
- Locate the `VERIFY_BOT` verification loop (`for version in ...; do`).
- Remove the dropped version number from the list so that `./gradlew verifyPlugin` is only run against active versions in the compatibility range.

### 4. Remove Obsolete Verifier Baseline (`tool/baseline/`)
- Check `tool/baseline/<version>/` for the removed version directory.
- Remove the directory and baseline file (e.g., using `git rm -r tool/baseline/<version>`) so baseline update scripts (`tool/update_baselines.sh`) do not attempt to verify unsupported versions.

### 5. Search & Remove Obsolete Code and TODOs
- **Search Codebase:** Grep `src/` for references to the removed build number or version string (e.g., `251`, `2025.1`).
- **Check Version Checks:** Look for version-specific branches or workarounds (such as in `DartPluginVersion.java` or UI compatibility shims) that check for the removed version and simplify or remove dead code paths.
- **Check TODOs:** Look for comments like `TODO: remove when dropping support for <version>` or similar workarounds that can now be cleaned up.

### 6. Search Repository Issues
- Search repository issues (via GitHub issue search or issue tracker) for issues related to dropping or moving off the removed version (e.g., `repo:flutter/flutter-intellij <version>` or platform compatibility issues).
- Check if any open issues or tracked technical debt items specify code deletions or cleanup steps upon dropping support for that version, and implement any applicable cleanups.

### 7. Update Changelog
- **`CHANGELOG.md`:** Under the `### Removed` section, add an entry documenting the removal (e.g., `- Support removed for 2025.1.`).
- **Note:** Do not modify `docs/building.md`. Its version examples are illustrative and should not be updated when removing a platform version.

### 8. Verify & Validate
- Run `./gradlew testClasses` to verify compilation.
- Run `./gradlew test` to verify unit tests pass.
- Run `./gradlew verifyPlugin` to verify that plugin verification succeeds against the new compatibility range.
