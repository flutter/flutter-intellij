---
name: audit-dependencies
description: Optimize plugin size and security by removing unused dependencies and updating outdated libraries.
---

# Skill: Dependency & Library Audit

You are tasked with optimizing plugin size and security by removing unused dependencies and updating outdated libraries.

## Objective
Optimize plugin size and security by removing unused dependencies and updating outdated libraries.

## Workflow Instructions

### 1. Baseline
- Run `./gradlew test` and `./gradlew verifyPlugin` to ensure the project is stable.

### 2. Audit `build.gradle`
- Review `dependencies {}` block. Identify libraries that might no longer be used.

### 3. Check `plugin.xml`
- Ensure all `<depends>` tags match actual API usage.

### 4. Update Versions
- Check for newer stable versions of third-party libraries.

### 5. Remove Unused
- Delete any local `.jar` files in `lib/` that are not referenced or can be replaced by Maven coordinates.

### 6. Verify
- Run `./gradlew buildPlugin` and check the distribution size. Ensure the plugin still loads and functions correctly.
- Run `./gradlew testClasses`, `./gradlew test`, and `./gradlew verifyPlugin` to ensure no checking or runtime issues were introduced.
- Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

### 7. Report & Review
- Summarize the dependencies removed or updated.
- **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
- **Action:** Ask the user to review the `build.gradle` changes closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Cleanup: Remove unused dependency [Name]").
