---
name: audit-ui-thread-safety
description: Prevent UI freezes and ensure a responsive user experience by validating threading rules and migrating blocking calls off the Event Dispatch Thread (EDT).
---

# Skill: UI Thread Safety & Responsiveness

You are tasked with preventing UI freezes and ensuring a responsive user experience by validating threading rules.

## Objective
Prevent UI freezes and ensure a responsive user experience by validating threading rules.

## Workflow Instructions

### 1. Baseline
- Run `./gradlew test` and `./gradlew verifyPlugin` to ensure the project is in a good state before refactoring.

### 2. Audit EDT Usage
- Search for usages of `runInEdt`, `invokeLater`, and `invokeAndWait`.

### 3. Identify Blocking Calls
- Ensure no I/O operations (file access, network calls) or heavy computations occur within these UI blocks.

### 4. Migrate to Background
- Refactor heavy tasks to run on background threads using `Task.Backgroundable` or `ReadAction.nonBlocking`.

### 5. Check Modality
- Verify that modal dialogs do not block the UI thread unnecessarily.

### 6. Verify
- Run `./gradlew testClasses` to ensure safe refactoring.
- Run `./gradlew test` and `./gradlew verifyPlugin` to ensure no regressions.
- Run the plugin in a sandbox (`./gradlew runIde`) and perform key actions while monitoring for UI freezes.
- Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

### 7. Report & Review
- Summarize the threading fixes.
- **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
- **Action:** Ask the user to review the changes closely to ensure no deadlocks or UI freezes are introduced.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Fix UI freeze: Move heavy computation to background task").
