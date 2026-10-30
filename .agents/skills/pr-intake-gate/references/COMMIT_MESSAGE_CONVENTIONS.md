# Git Commit Message Conventions for PR Intake Gates

This document defines the exact formatting conventions and requirements for Git commit messages in `flutter-intellij`.

---

## Commit Message Anatomy

A conforming commit message is structured into five distinct components:

```gitcommit
<Title Line: Imperative summary <= 50 characters>

<Body: Detailed explanation of problem and solution, wrapped at 72 chars>

[LARGER PR: <lines> lines of change - <architectural justification>]

[NO CHANGELOG: <reason>]

Plugin Verifier: <summary of verifier status>

<!-- flutter-pr-check: passed @ <timestamp_epoch> (commit <sha>) -->
```

---

## Section Guidelines

### 1. Title Line
- Imperative mood, present tense (e.g., "Add Flutter Outline caching", not "Added..." or "Adds...").
- Capitalized first word, no trailing period.
- Maximum 50 characters (hard limit 72 characters).

### 2. Body
- Explains **what** was changed and **why**, not just how.
- Highlights any subtle concurrency, threading (EDT), or VFS implications.
- Wrapped strictly at ~72 columns.

### 3. Conditional Headers

#### `LARGER PR` Header (Mandatory if >500 lines of change)
When total changes (additions + deletions) exceed 500 lines:
```
LARGER PR: 742 lines of change - Monolithic SDK model migration that cannot be cleanly partitioned without intermediate breaking API changes.
```

#### `NO CHANGELOG` Header (Mandatory if CHANGELOG.md is not modified)
When `CHANGELOG.md` is omitted because changes are non-user-facing:
```
NO CHANGELOG: Internal unit test suite expansion for ActionUpdateThread.
```

### 4. Plugin Verifier Summary (Mandatory)
Documents the output of `./gradlew verifyPlugin`:
```
Plugin Verifier: 0 errors, 4 warnings (2024.3, 2025.1)
```

### 5. Verification Stamp (Mandatory)
The exact HTML comment identifier required by the CI presubmit check:
```html
<!-- flutter-pr-check: passed @ 1793228400 (commit 8b1f2e4) -->
```
- Must use Unix timestamp in seconds.
- Must reference the commit SHA (short format).

---

## Full Examples

### Example 1: Standard Feature / Bug Fix
```gitcommit
Migrate DartConsoleFilter to background ReadAction

Move heavy pubspec scanning off the Event Dispatch Thread to prevent
UI freezes when console output contains multiple project links.

Plugin Verifier: 0 errors, 2 warnings (unchanged from baseline)

<!-- flutter-pr-check: passed @ 1793228400 (commit a1b2c3d) -->
```

### Example 2: Larger PR (>500 lines) with Justification
```gitcommit
Migrate Flutter SDK detection to modern Workspace Model

Replaces legacy project-level SDK path caches with the IntelliJ
Platform Workspace Model entity storage.

LARGER PR: 680 lines of change - Monolithic workspace model entity
migration spanning entity definitions, listeners, and wizard integration.
Splitting would introduce broken intermediary states in project loading.

Plugin Verifier: 0 errors, 0 warnings (verified on 2024.3, 2025.1)

<!-- flutter-pr-check: passed @ 1793228400 (commit d4e5f6a) -->
```

### Example 3: Test-Only / Internal PR (No Changelog)
```gitcommit
Add ActionUpdateThread tests for Flutter tool window actions

Verifies that all tool window actions declare ActionUpdateThread.BGT
to comply with modern IntelliJ Platform background action update rules.

NO CHANGELOG: Internal unit test verification without user-facing impact.

Plugin Verifier: 0 errors, 0 warnings

<!-- flutter-pr-check: passed @ 1793228400 (commit 7890abc) -->
```
