---
name: improve-documentation-coverage
description: Ensure public classes and methods in src/ have Javadoc/KDoc to improve maintainability and API clarity.
---

# Skill: Documentation Coverage

You are tasked with ensuring all public classes and methods in `src/` have Javadoc/KDoc to improve maintainability and API clarity.

## Objective
Ensure all public classes and methods in `src/` have Javadoc/KDoc to improve maintainability and API clarity.

## Workflow Instructions

### 1. Baseline
- Run `./gradlew testClasses` to ensure project compiles.
- Run `./gradlew test` to ensure stability.
- Run `./gradlew verifyPlugin` to ensure no preexisting verification issues.

### 2. Select Batch
- Identify a batch of **up to 5** files in `src/` with missing public documentation.

### 3. Add Docstrings
- Write concise descriptions for missing items (`/** ... */` or `///`), explaining *what* they do and *why* using expected Java and Kotlin documentation standards.

### 4. Check Parameters
- Ensure `@param`, `@return`, and `@throws` tags are present where applicable.

### 5. Verify
- Inspect changes to ensure accuracy.
- Run `./gradlew testClasses` to ensure documentation changes didn't break compilation (e.g. invalid refs).
- Run `./gradlew verifyPlugin` to ensure no compliance issues.

### 6. Report & Review
- Summarize the changes made (rationale for documentation added).
- **Action:** Ask the user to review documentation closely.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Add Javadoc to [Component Name] utility classes").
