<!--* freshness: { reviewed: '2026-10-25' } *-->
# Android

## Overview
Detects and interacts with the underlying Android SDK and emulators for Flutter projects.

## Interface
- [`AndroidEmulator`](AndroidEmulator.java)
- [`AndroidSdk`](AndroidSdk.java): `createFromProject(Project)`, `getEmulators()`, `getEmulatorToolExecutable()`.
- [`AndroidStudioGradleSyncProvider`](AndroidStudioGradleSyncProvider.java)
- [`GradleSyncProvider`](GradleSyncProvider.java): extension point `io.flutter.gradleSyncProvider`.
- [`IntelliJAndroidSdk`](IntelliJAndroidSdk.java): `fromProject`, `fromEnvironment` (`ANDROID_HOME`), `fromHome`, `chooseAndroidHome`, `setCurrent`.
- [`IntellijGradleSyncProvider`](IntellijGradleSyncProvider.java)

## Invariants
- AndroidEmulator instance always has a non-null AndroidSdk and id.
- AndroidSdk instance always has a non-null Project and home VirtualFile.
- IntelliJAndroidSdk instance always has a non-null Sdk and home VirtualFile.
- The Android Studio sync provider is registered `order="first"` in [`studio-contribs.xml`](../../../../resources/META-INF/studio-contribs.xml) and the IntelliJ one `order="last"` in [`plugin.xml`](../../../../resources/META-INF/plugin.xml). `GradleUtils` uses the first registered provider.
- `IntelliJAndroidSdk.chooseAndroidHome` resolves in order: project SDK, then the Flutter `android-sdk` config (if allowed), then `ANDROID_HOME`.

## Side Effects
- AndroidEmulator.startEmulator() executes a shell process to launch the emulator and reports failures as notifications via `FlutterMessages.showError`. When the emulator is configured to run in a tool window (`EmulatorSettings`, located by reflection under two package names), it adds `-qt-hide-window -grpc-use-token -idle-grpc-timeout 300` and opens the Android Emulator tool window.
- AndroidSdk.getEmulators() synchronously executes the emulator tool with '-list-avds' as a shell process. It waits at most 10s and returns an empty list on timeout or non-zero exit.
- AndroidStudioGradleSyncProvider.scheduleSync() calls GradleSyncInvoker to trigger a background project synchronization. IntellijGradleSyncProvider.scheduleSync() is currently a no-op.
- IntelliJAndroidSdk.setCurrent() modifies the project root configuration to set the project SDK and must run in a write action.
- IntelliJAndroidSdk.chooseAndroidHome() can execute a shell process to query flutter tools for 'android-sdk' configuration.
