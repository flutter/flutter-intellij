---
name: cleanup-unused-assets
description: Reduce plugin size by scanning resources/icons and removing unreferenced assets.
---

# Skill: Unused Asset Cleanup

You are tasked with reducing plugin size by removing unused icons, images, and resources.

## Objective
Reduce plugin size by removing unused icons, images, and resources.

## Workflow Instructions

### 1. Baseline
- Run `./gradlew test` and `./gradlew verifyPlugin` to ensure the project is stable.

### 2. Scan Assets
- List all files in `resources/icons` and other asset directories.

### 3. Search Usages
- Check if each asset is referenced in code or `plugin.xml`.

### 4. Remove
- Delete assets with zero references.

### 5. Verify
- Run `./gradlew buildPlugin` and check that the plugin still loads correctly without missing resource exceptions.
- Run `./gradlew testClasses`, `./gradlew test` and `./gradlew verifyPlugin` to ensure no regressions.
- Suggest manual test steps: Check code changes made and write test steps for a user to execute that trigger changed code paths. If needed, add logging statements to verify code paths successfully run.

### 6. Report & Review
- Summarize assets removed.
- **Test Location:** Explicitly state *where* in the IDE user should go to test changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
- **Action:** Ask user to review deletions closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Cleanup: Remove unused icon [Icon Name]").
