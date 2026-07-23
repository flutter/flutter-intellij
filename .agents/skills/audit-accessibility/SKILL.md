---
name: audit-accessibility
description: Ensure the plugin's custom UI components are accessible to users with screen readers and other assistive technologies.
---

# Skill: Accessibility (A11y) Audit

You are tasked with ensuring the plugin's custom UI components are accessible to users with screen readers and other assistive technologies.

## Objective
Ensure the plugin's custom UI components are accessible to users with screen readers and other assistive technologies.

## Workflow Instructions

### 1. Baseline
- Run `./gradlew testClasses` to ensure project compilation.
- Run `./gradlew test` to ensure the project is stable.
- Run `./gradlew verifyPlugin` to ensure no verification issues.

### 2. Identify Custom UI
- List all custom `JPanel`, `JComponent`, or `Dialog` classes in the codebase.

### 3. Check Properties
- Verify that every interactive component has:
  - `getAccessibleContext().setAccessibleName(...)`
  - `getAccessibleContext().setAccessibleDescription(...)`

### 4. Focus Management
- Ensure custom components handle focus traversal correctly (Tab/Shift+Tab).

### 5. Color Contrast
- If custom colors are used, verify they meet WCAG contrast guidelines (especially for dark themes).

### 6. Verify
- Run `./gradlew testClasses` and `./gradlew test` to ensure no regressions.
- Run `./gradlew verifyPlugin` to ensure strict compliance.
- Use the "Accessibility Inspector" (if available in the SDK) or a screen reader to navigate the UI.
- Suggest manual test steps: Check the code changes made and write test steps for a user to execute that will trigger the code paths that have changed. If needed, add logging statements to verify that the code paths have successfully run.

### 7. Report & Review
- Summarize the accessibility improvements in the form of a git commit message.
- **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
- **Action:** Ask the user to review the UI changes closely.
- **Do not commit or push.**
