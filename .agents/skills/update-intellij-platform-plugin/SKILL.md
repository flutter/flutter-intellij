---
name: update-intellij-platform-plugin
description: Update the org.jetbrains.intellij.platform Gradle plugin version to the latest available and run comprehensive validation checks.
---

# Skill: Update org.jetbrains.intellij.platform Gradle Plugin

You are tasked with updating the `org.jetbrains.intellij.platform` Gradle plugin version to the latest available.

## Objective
Update the `org.jetbrains.intellij.platform` plugin version to the latest available.

## Workflow Instructions

### 1. Baseline
- Run `./gradlew buildPlugin` to establish a baseline.
- Run `./gradlew test` to ensure project stability.
- Run `./gradlew verifyPlugin` to check for existing warnings.

### 2. Create a New Branch
- Create a new branch off of `main` for this update.

### 3. Find the New Version Name and URL
- Check `https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform` to identify the latest stable version number.

### 4. Make the Change
- Update the version in `third_party/build.gradle.kts` (look for `id("org.jetbrains.intellij.platform") version "..."`).

### 5. Validate
- Run `./gradlew buildPlugin` again to ensure the build succeeds.
- Run `./gradlew test` to ensure everything is still working.
- Run `./gradlew verifyPlugin` to check for any new verification issues.
- **Crucial:** If any tests fail, verify if they are pre-existing failures by running them on the `main` branch (or the version before your changes). Only failures introduced by the update need to be fixed.

### 6. Report & Review
- Summarize the update (old version -> new version).
- **Action:** Ask the user to review the build files closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Update org.jetbrains.intellij.platform to [Version]").
