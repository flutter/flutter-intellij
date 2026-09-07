<!--* freshness: { reviewed: '2026-08-17' } *-->
# Android

## Overview
Detects and interacts with the underlying Android SDK and emulators for Flutter projects.

## Interface
- `AndroidEmulator`
- `AndroidSdk`
- `AndroidStudioGradleSyncProvider`
- `GradleSyncProvider`
- `IntelliJAndroidSdk`
- `IntellijGradleSyncProvider`

## Invariants
- AndroidEmulator instance always has a non-null AndroidSdk and id.
- AndroidSdk instance always has a non-null Project and home VirtualFile.
- IntelliJAndroidSdk instance always has a non-null Sdk and home VirtualFile.

## Side Effects
- AndroidEmulator.startEmulator() executes a shell process to launch the emulator, opens the Android Emulator tool window if applicable, and shows error dialogs if it fails.
- AndroidSdk.getEmulators() synchronously executes the emulator tool with '-list-avds' parameter as a shell process to parse the result.
- AndroidStudioGradleSyncProvider.scheduleSync() calls GradleSyncInvoker to trigger a background project synchronization.
- IntelliJAndroidSdk.setCurrent() modifies the project root configuration to set the project SDK and must run in a write action.
- IntelliJAndroidSdk.chooseAndroidHome() can execute a shell process to query flutter tools for 'android-sdk' configuration.
