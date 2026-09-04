## Unreleased

### Added

### Changed

### Removed

### Fixed

## 509.0.0

### Added
- Populating Dart Analysis tool window errors and warnings using Language Server Protocol (LSP) publishDiagnostics notifications (#520)
- Go to Type Declaration implemented with JetBrains LSP (#615)
- Type and Call Hierarchy navigation implemented with JetBrains LSP (#403)
- Find Usages / references with JetBrains LSP (experimental feature) (#396)

### Changed
- Diagnostics server port retrieval via LSP has passed the experimental phase and is now enabled for all users with an appropriate SDK (#625)
- Hover via LSP is now enabled for all users (#626)

### Fixed
- Quick Documentation from code completion popup window (#632)

## 508.1.0

### Added
- Highlighting read vs write variable occurrences (JetBrains LSP, experimental feature) (#552)

### Fixed
- Code completion and parameter info tooltips for constructor shorthand '.new' argument lists (#573)
- Avoid passing unsupported '--ping-interval' flag to DTD on Dart SDKs older than 3.8.0-16.0.dev (#599)

## 508.0.0

### Added
- Retrieving diagnostics server with JetBrains LSP (#493)
- Documenting the process for installing dev builds (#544)

### Changed
- Go to Definition implemented with JetBrains LSP (#539)

### Removed
- untilBuild restriction (#553)

### Fixed
- Duplicate analysis errors on Windows due to drive letter casing in LSP file URIs (#537)
- DTD connection timeouts by enabling a 15-second ping interval (#525)

## 507.0.0

### Added
- Support for IDEA platform 2026.2 (#499)

### Changed
- Hover implemented with JetBrains LSP as an experimental feature (#443)

### Removed
- Support for platform versions 251 and 252 (#473)

### Fixed
- Prevent memory leak by closing DTD WebSocket connections and clearing listeners when disposing projects (#449)
- Split screen `RunContentDescriptor` error during app run/debug (#506)

## 506.1.0

### Added

### Changed

### Removed

### Fixed
- Support Go to Declaration and symbol resolution for 'new' constructor syntax (#457)

## 506.0.0

### Added
- Hover implemented with lsp4ij and experimental features setting (#357)
- Retrieving diagnostics server with lsp4ij (#391)

### Changed

### Removed

### Fixed
- Avoid UI thread freezes during 'Invalidate Caches' by performing dartServer cache deletion asynchronously (#106)
- Avoid UI thread freezes by moving DTD process creation to a pooled thread (#437)
- Avoid UI thread freezes during project startup by batch-updating module root directory exclusions for `pubspec.yaml` files (#149)
- Avoid AssertionErrors / UI crashes when navigating subclasses, superclasses, or overriding methods by migrating from deprecated `PsiElementListNavigator` to modern `PsiTargetNavigator`.
- Avoid potential NullPointerExceptions in the indexer when recovering from malformed class declarations (#375)
- Prevent duplicate application of `workspace/applyEdit` messages not from lsp4ij  (#409)
- Forward cancelRequest for LSP-over-legacy requests (#410)

## 505.0.0

### Added
- Support for the Dart primary constructors language feature (#330)
- Dependency on lsp4ij with basic initialization (#348)

### Changed

### Removed

### Fixed
- Avoid potential exceptions by wrapping project creation in a WriteAction (#332)

## 504.0.0

### Added

### Changed
- Log to same file as Flutter plugin (#276)

### Removed

### Fixed
- Avoid potential UI freezes by terminating DTD and DevTools processes asynchronously (#287)

## 503.0.0

### Added
- Analytics instrumentation (#230, #225)
- Logging with `PluginLogger` (#245, #222)
- Support for IDEA platform 2026.1 (#272)

### Changed
- Migrate from deprecated `ProcessAdapter` to `ProcessListener` (#265)
- Build system: migrate from `community` to `intellijIdea` dependency (#209)

### Removed
- Stale `helpTopic` and `helpId` references (#232, #227)

### Fixed
- Improved cancellation handling for refactoring operations to prevent potential UI freezes (#264)
- Keep New Project dialog details in sync with the selected Dart SDK (#215)
- Downgrade Kotlin to be compatible with a 2.1.0 compiler (#254)

## 502.0.0

### Added

### Changed

- Update stagehand mentions to dart create (#171)

### Removed

- Dropped support for Dart SDK versions older than 2.12.
- Unsupported Dart in HTML logic (#173)
- Outdated web template (#172)

### Fixed

- Fixed resolution for Dart dot shorthands (e.g. `.new`, `.named`). (#89)
- UI freeze during refactoring operations (e.g. Move File) when Analysis Server is slow (#122)

## 500.0.0

### Added

- Support in the "Invalidate Caches..." dialog to remove ~/.dartServer cache

### Removed

- "Scope analysis to the current package" feature from the Dart problem view
- Code Coverage support, all references to com.intellij.coverage.*
- Dart embedded and syntax highlighting in HTML support

### Changed

- Vendor change from "JetBrains" to "Google"
- Build system change from Bazel to Gradle

## 251.27812.12

### Added

 - New Dart language feature: Dot Shorthands (https://youtrack.jetbrains.com/issue/IDEA-370100)
 - Support new 'Null-Aware Elements’ syntax (https://youtrack.jetbrains.com/issue/IDEA-374053)

### Removed

### Changed

 - Fix: IDE freezes after inserting a piece of Dart code (https://youtrack.jetbrains.com/issue/IDEA-231468)
 - Fix: DartParser performance issue: sluggish typing in the editor (https://youtrack.jetbrains.com/issue/IDEA-365957)
 - Fix: Format of very long line gives uncompilable code (https://youtrack.jetbrains.com/issue/IDEA-328345)
 - Fix: Cannot save Dart line length in Settings (https://youtrack.jetbrains.com/issue/IDEA-322573)
 - Closing labels should look like inlay hints, not like real comments (https://youtrack.jetbrains.com/issue/IDEA-289260)