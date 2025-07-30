# 87
- Fixes to Flutter test execution (#8233, #8325)
- Make android dependencies optional, allowing the plugin to be used in more Jetbrains products (Rider, etc) (#7949, #8375)
- Internal: support for logging to a dedicated plugin log file (#8253)
- Fixes to ensure the Property Editor loads on all project opens (#8268)

# 86
- New message in DevTools windows for "Dock unpinned" IntelliJ feature (#8181)
- Fixes for Slow Operation notifications in the IDEA platform (#7792)
- Fix in Flutter project creation flow (#8259)
- Many code health improvements and code cleanups (#8025, #8027, #8021)
- Migration of deprecated API usages (#7718)
- Fix for empty menu item in the device selector (#8264)

# 85.3
- Add Property Editor side panel (#7957)
- Support removed for IDEA 2024.1 (Koala) and 2024.2 (Ladybug) (#8073)
- Various cleanups including migrating slow operations to non-blocking calls (#8089)

# 85.2
- Fix broken devtools inspector source navigation (#8041)

# 85.1
- Fix the disappearance of the New Flutter Project menu item (#8040)
- Add back the device name with the running tab (#7948)
- Update the `org.jetbrains.intellij.platform` version to `2.5.0` (#8038)
- Replace deprecated ComboBoxWithBrowserButton (#7931)
- Fix Flutter Outline View event over-subscriptions (#7980)

# 85
- Restored Test with coverage run configuration feature (#7810)
- Upgrade `org.jetbrains.intellij.platform` to 2.2.1 from 2.1.0 (#7936)
- Fix for DevTool windows not appearing (#8029)
- Support for Narwhal, Android Studio 2025.1 (#7963)
- Build changes to support newer required versions of Java to build the plugin (#7963)
- Cleanup: removal of pre Dart SDK 3.0 code (#7882)
- Cleanup: removal of the deprecated Swing-based Inspector window (#7861)
- Cleanup: removal of the deprecated Outline window (#7816)
- Cleanup: removal of the deprecated Performance page window (#7816)
- Migrated all instances of EditorNotifications.Provider to the new API (#7830)

# 84
- This version was not shipped due to issue #7968

# 83
- First version for Meerkat, Android Studio 2024.3 (#7799)
- Message in the Flutter Outline window that the window is now deprecated (#7778)
- Testing and cleanup now that the code is migrated to the new Gradle Plugin (#7670)

# 82.2
- Release of the plugin for 2024.3 (#7670)
- Migration to IntelliJ Platform Gradle Plugin (2.x) (#7670)
- The Flutter coverage runner support has been removed (#7670)

# 82.1
- Fix for Cannot invoke "com.intellij.openapi.wm.ToolWindow.setAvailable(boolean)" issue -- thanks to @parlough (#7691)
- New SDK notification to notify of old Flutter SDK usage (#7763)
- Progress on migrating off of old IDEA APIs (#7718)
- Significant code cleanup

# 82
- Various DevTools integration improvements (#7626) (#7621)
- Removal of the old Performance page, now replaced by DevTools (#7624)
- Add an option to reload a DevTools window (#7617)
- Fix to the developer build (#7625)

# 81.1
- Initial support 2024.2 & Android Studio Ladybug Canary 6 (#7595)

# 81
- New icons to match "New UI" features in IntelliJ and Android Studio (#6595)
- Restore Flutter test icons in the editor gutter (#7505)
- Fix widget tree highlighting in the editor (#7522)
- Resolve "Exception: Cannot invoke "org..AnAction.getTemplatePresentation()" exception (#7488)
- Resolve "Pubspec has been edited" editor notification is stuck (#7538)
- Resolve Released EditorImpl held by lambda in FlutterReloadManager (#7507)
- Configure the Project view for Flutter in AS, when creating a new Flutter project (#4470)
- Migrate to Kotlin UI DSL Version 2 (#7310)

# 80
- Resolve debugger issue with the new Dart macro file uri format (#7449)
- Hide deep links window when insufficient SDK version (#7478)
- Fix exceptions out of FlutterSampleNotificationProvider (#5634)
- Additional fixes for deprecation of `ActionUpdateThread.OLD_EDT` (#7330)
- Exception from EditorPerfDecorations fixed (#7432)
- Exception from FlutterColorProvider fixed (#7428)
- Fix top toolbar for new UI (#7423)
- Update JxBrowser to `v7.38.2` (#7413)
- "Open Android Module in Android Studio" action removed (#7103)
- Fix for deprecation of `ActionUpdateThread.OLD_EDT` (#7330)
- Deprecation of `ServiceExtensions.setPubRootDirectories` (#7142)
- Fix plugin not opening in Android Studio (#7305)
- Deadlock involving `WorkspaceCache.getInstance()` (#7333)
- Fix for `AlreadyDisposedException` from `DartVmServiceDebugProcess` (#7381)
- Memory leak fix out of `DartVmServiceDebugProcess` (7380)
- Memory leak fix in `FlutterSettings` and `JxBrowser` (#7377)
- Delete actions specific to legacy inspector (#7416)
