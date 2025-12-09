## 88.2.0

### Added

### Changed

- Made log file `dash.log` (instead of `flutter.log`). (#8638)

### Removed

### Fixed

- Fixed crash when using 3rd party loggers that don't implement `setLevel`. (#8631)

## 88.1.0

### Added

- Widget preview tool window (#8595, #8599).

### Changed

- Made Flutter project open to project view by default. (#8573)
- Fixed incorrect colors for the device selector when using light themes. (#8576)

## 88.0.0

### Added

- Support for Android Studio 2025.2.

### Removed

- Notification of required pub actions at the top of Dart files. (#7623, #8481)

### Changed

- Updated the Flutter version reading to use the file `./bin/cache/flutter.version.json`, as required in Flutter 3.33+. (#8465)
- Resolved a "Slow operations are prohibited on EDT" exception on Flutter Project creation. (#8446, #8447, #8448)
- Made dev release daily instead of weekly.
- Set the device selector component to opaque during its creation to avoid an unexpected background color. (#8471)
- Refactored `DeviceSelectorAction` and added rich icons to different platform devices. (#8475)
- Fixed DTD freezes when opening projects and EDT freezes when the theme is changed and opening embedded DevTools. (#8477)
- Fixed `DeviceSelectorAction` `NoSuchElementException` in the toolbar layout. (#8515)
- Fixed `DeviceSelectorAction`'s concurrent modification exception. (#8550)

## 87.1.0

### Changed

- Registered VM service with DTD. (#8436)
- Fixed a ClassCastException for BadgeIcon on flutter runs. (#8426)

## 87.0.0

### Added

- Internal support for logging to a dedicated plugin log file. (#8253)

### Changed

- Made Android dependencies optional, allowing the plugin to be used in more Jetbrains products (Rider, etc). (#7949, #8375)
- Fixed issues with Flutter test execution. (#8233, #8325)
- Fixed issues to ensure the Property Editor loads on all project opens. (#8268)
- Fixed a hang after opening a new project in Android Studio. (#8390)
