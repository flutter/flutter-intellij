---
name: verify-eap-compatibility
description: Ensure the plugin remains compatible with the latest IntelliJ Platform releases and EAP (Early Access Program) builds.
---

# Skill: Plugin Compatibility & EAP Verification

You are tasked with ensuring the plugin remains compatible with the latest IntelliJ Platform releases and EAP (Early Access Program) builds.

## Objective
Ensure the plugin remains compatible with the latest IntelliJ Platform releases and EAP (Early Access Program) builds.

## Workflow Instructions

### 1. Target EAP
- Temporarily update `gradle.properties` to target the latest IntelliJ Platform EAP version.

### 2. Run Verification
- Run `./gradlew testClasses` to ensure the project compiles against the EAP.
- Execute `./gradlew verifyPlugin` to check for binary incompatibilities or broken API usages in the new version.
- Run `./gradlew test` to check for functional regressions with the EAP.

### 3. Check Bounds
- Review `plugin.xml` for `<idea-version since-build="..." until-build="..."/>`. Ensure `until-build` is open-ended or appropriately set for the new version.

### 4. Resolve Issues
- Fix any compilation errors or verification warnings specific to the new platform version.

### 5. Report & Review
- Summarize any required changes or blockers for supporting the new version.
- **Test Location:** Explicitly state *where* in the IDE the user should go to test the changed functionality (e.g., "Go to Preferences > Languages & Frameworks > Flutter").
- **Action:** Ask the user to review the compatibility fixes closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Fix EAP compatibility: Resolve invalid until-build").
