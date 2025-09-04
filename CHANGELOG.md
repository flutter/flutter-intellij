## Unreleased

### Added

### Removed

- The Flutter version is now read from the file ./bin/cache/flutter.version.json, required in Flutter 3.33+ (#8465)
- Notification of required pub actions at the top of Dart files (#7623, #8481)

### Changed

- Resolved a "Slow operations are prohibited on EDT" exception on Flutter Project creation (#8446, #8447, #8448)
- Made dev release daily instead of weekly
- Set the device selector component to opaque during its creation to avoid an unexpected background color (#8471)
- Refactored `DeviceSelectorAction` and add rich icons to different platform devices (#8475)
- Fix DTD freezes when opening projects, and EDT freezes when the theme is changed and opening embedded DevTools (#8477)
- Fix `DeviceSelectorAction` `NoSuchElementException` in the toolbar layout (#8515)

## 87.1.0

- Register VM service with DTD (#8436)
- Fix for ClassCastException: BadgeIcon on flutter runs (#8426)

## 87.0.0

- Fixes to Flutter test execution (#8233, #8325)
- Make Android dependencies optional, allowing the plugin to be used in more Jetbrains products (Rider, etc) (#7949, #8375)
- Internal: support for logging to a dedicated plugin log file (#8253)
- Fixes to ensure the Property Editor loads on all project opens (#8268)
- Fix the hang after opening a new project in Android Studio (#8390)
