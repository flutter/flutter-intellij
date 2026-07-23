---
name: migrate-intellij-util
description: Optimize memory usage, consistency, and performance by migrating standard Java/Kotlin classes to IntelliJ's specialized com.intellij.util implementations.
---

# Skill: API Migration to use com.intellij.util.* classes and methods

You are tasked with optimizing memory usage, consistency, and performance by migrating standard Java/Kotlin classes to IntelliJ's specialized `com.intellij.util` implementations.

## Objective
Go read https://javadoc.jetbrains.net/teamcity/openapi/current/com/intellij/util/package-summary.html, and then optimize memory usage, consistency, and performance by migrating standard Java/Kotlin classes to IntelliJ's specialized `com.intellij.util` implementations.

## Candidates for Migration
- `ArrayList` -> `SmartList` (for potentially empty/small lists)
- `HashMap` / `ConcurrentHashMap` -> `ContainerUtil` maps
- `String` manipulation -> `StringUtil` (e.g., `isEmpty`, `join`, `notNullize`)
- `Path` / `File` string manipulation -> `PathUtil`
- `javax.swing.Timer` -> `Alarm` (for UI-related callbacks/debouncing)
- Null checks -> `ObjectUtils` (e.g., `doIfNotNull`, `coalesce`)
- Array resizing/merging -> `ArrayUtil`

## Workflow Instructions

### 1. Baseline
- Run `./gradlew testClasses`, `./gradlew test`, and `./gradlew verifyPlugin` to ensure project is in a good state.

### 2. Select Target
- Identify a *single* migration opportunity within a *single package*.

### 3. Constraint
- Keep the change extremely small (**max 50 lines of code change**).

### 4. Migrate
- Replace standard API with the `com.intellij.util` equivalent.

### 5. Verify
- Run `./gradlew testClasses` to ensure migration didn't break test compilation.
- Run `./gradlew test` and `./gradlew verifyPlugin` to ensure no regressions.
- Suggest manual test steps: Check code changes made and write test steps for a user to execute that will trigger changed code paths. If needed, add logging statements to verify code paths have successfully run.

### 6. Report & Review
- Summarize the migration.
- **Test Location:** Explicitly state *where* in the IDE user should go to test changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
- **Action:** Ask user to review changes closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Migrate code to use StringUtil in [Class Name]").
