<!--* freshness: { reviewed: '2026-10-25' } *-->
# Embedded JxBrowser

## Overview
Manages the lifecycle, downloading, and initialization of the JxBrowser embedded browser engine used for DevTools.

Key files:
- [JxBrowserManager.java](JxBrowserManager.java): downloads the JxBrowser jars, loads them into the classloader, and tracks installation status.
- [EmbeddedBrowserEngine.java](EmbeddedBrowserEngine.java): application service that owns the shared JxBrowser `Engine`.
- [EmbeddedJxBrowser.java](EmbeddedJxBrowser.java): project service that extends [EmbeddedBrowser](../view/EmbeddedBrowser.java). The same file holds the package-private `EmbeddedJxBrowserTab`, which implements [EmbeddedTab](../view/EmbeddedTab.java).
- [JxBrowserStatus.java](JxBrowserStatus.java), [FailureType.java](FailureType.java), [InstallationFailedReason.java](InstallationFailedReason.java): status and failure reporting types.

The license key, platform-specific file names and distribution links come from [JxBrowserUtils](../utils/JxBrowserUtils.java).

## Interface
- `class EmbeddedBrowserEngine`
- `class EmbeddedJxBrowser`
- `enum FailureType`
- `class InstallationFailedReason`
- `class JxBrowserManager`
  - `setUp(String projectName)`: starts installation, or marks it `INSTALLATION_SKIPPED` when `JxBrowserUtils.skipInstallation()` is true.
  - `retryFromFailed(Project)`: moves `INSTALLATION_FAILED` back to `NOT_INSTALLED` and calls `setUp` again.
  - `listenForSettingChanges(Project)`: registers a single `FlutterSettings` listener (`SettingsListener`) that calls `setUp` while the status is `NOT_INSTALLED`.
  - `waitForInstallation(int seconds)`: blocks on the `installation` future.
- `enum JxBrowserStatus`

## Invariants
- EmbeddedBrowserEngine is an application service.
- EmbeddedJxBrowser is a project service.
- JxBrowserManager operates as a singleton. `getInstance()` creates it lazily without synchronization.
- The authoritative installation status is a static `AtomicReference<JxBrowserStatus>`. Its compare-and-set from `NOT_INSTALLED` to `INSTALLATION_IN_PROGRESS` ensures the IDE installs JxBrowser only once, even with several projects open.
- `JxBrowserManager.installation` is a public static `CompletableFuture` that waiters can block on. `setUp` replaces it with a new future when the previous attempt has already completed (for example, a retry after failure).
- Only the download runs in the background, as a `Task.Backgroundable`. The rest of `setUp` runs on the caller's thread: license-key lookup, directory creation, file-existence checks, deletes, and `loadClasses` when the files already exist.
- The engine uses `HARDWARE_ACCELERATED` rendering on macOS and `OFF_SCREEN` elsewhere. On Linux it adds `--force-device-scale-factor=1`.

## Side Effects
- Downloads JxBrowser platform, API, and Swing files to `JxBrowserManager.DOWNLOAD_PATH` in the IDE plugins directory. `JxBrowserManager.setUp` creates that directory.
- Deletes existing JxBrowser files prior to re-downloading.
- `EmbeddedBrowserEngine` creates a `user-data` directory under `DOWNLOAD_PATH` through `EngineOptions.userDataDir`.
- Dynamically injects downloaded JxBrowser jars into the runtime classloader.
- Sets global JVM properties for JxBrowser. `JxBrowserManager.setUp` sets the license key. `EmbeddedJxBrowser` sets DPI awareness and logging properties, and writes a `jxbrowser.log` file to the IDE log directory.
