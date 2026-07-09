---
name: optimize-gradle-build
description: Improve local build performance and CI efficiency by leveraging Gradle's caching and profiling tools.
---

# Skill: Gradle Build Optimization

You are tasked with improving local build performance and CI efficiency by leveraging Gradle's caching and profiling tools.

## Objective
Improve local build performance and CI efficiency by leveraging Gradle's caching and profiling tools.

## Workflow Instructions

### 1. Read Documentation
- Read official Gradle documentation to understand best practices:
  - [Performance Guide](https://docs.gradle.org/current/userguide/performance.html)
  - [User Guide](https://docs.gradle.org/current/userguide/userguide.html)

### 2. Profile Build
- Run `./gradlew clean build --profile` to identify slow tasks and bottlenecks.

### 3. Enable Caching
- Check `gradle.properties` for `org.gradle.caching=true`. If missing, add it to enable the Build Cache. Check for other opportunities as well with any changes to Gradle.

### 4. Configuration Cache
- Run `./gradlew help --configuration-cache` to check for compatibility. If feasible, enable it to speed up the configuration phase.

### 5. Update Wrapper
- Check if a newer stable Gradle version is available and update the wrapper using `./gradlew wrapper --gradle-version [VERSION]`.

### 6. Verify
- Run a clean build to ensure optimizations didn't break the build process.

### 7. Report & Review
- Summarize the optimizations applied.
- **Action:** Ask the user to review the changes closely, especially `gradle.properties`.
- **Do not commit or push.**
- Provide a suggested Git commit message (e.g., "Optimize Gradle build: Enable caching and update wrapper").
