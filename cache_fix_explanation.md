# Explanation of Cache Fix for Reviewers

## Q1: Who introduced the cache and why is it used instead of reading from disk?

**A:** The commit to fix the issue by bypassing the cache was made by **Cory R** on **Wed Feb 25, 2026**. The commit message explains that the original cached method, `FlutterSdk.getFlutterSdk()`, was provided by the Dart plugin.

The reason a cache is used is for performance. Reading from disk is thousands of times slower than reading from memory. Since many parts of the IDE need the SDK path frequently, caching the value after the first read prevents redundant, slow disk operations and keeps the UI responsive.

The bug occurred because the settings page, which needs the authoritative value from the disk, was incorrectly using the potentially stale cached value. The fix was to use `getIncomplete()` in that specific location to read the authoritative value.

## Q2: Does this fix cause normal access methods to read from disk more often?

**A:** No, it does not. The fix was carefully targeted *only* to the settings page code (`FlutterSettingsConfigurable.java`).

Other parts of the plugin that use the cached `FlutterSdk.getFlutterSdk()` method for normal, frequent operations (like running an app) are completely unaffected. They continue to get the performance benefit of the cache. The direct disk read only happens in the infrequent case of a user opening the settings page, where correctness is more important than a minor performance optimization.

## Q3: Is there a way to make the fix by refreshing the cache properly before reading it, instead of bypassing it?

**A:** While theoretically possible, bypassing the cache is the better and safer design choice in this specific context for several reasons:

1.  **Ownership:** The cache belongs to the Dart plugin. Forcing a refresh on another component's internal state is risky and can have unintended side effects.
2.  **API Availability:** There's no guarantee the Dart plugin exposes a public, stable API for forcing a refresh. `getIncomplete()`, however, is clearly designed for this purpose.
3.  **Correctness:** A settings page's primary job is to reflect the **authoritative source of truth** (the configuration on disk). Bypassing a cache to read this ground truth is the most direct and correct design pattern for a UI that edits that truth.
