<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Pub Roots

## Overview
Abstracts interactions with Dart/Flutter package roots and tracks pubspec.yaml locations to understand workspace boundaries.

Key files:
- [PubRoot.java](PubRoot.java): an immutable snapshot of a directory containing `pubspec.yaml`, with helpers for well-known children (`lib`, `test`, `android`, `ios`, `example`, `.dart_tool/package_config.json`).
- [PubRootCache.java](PubRootCache.java): project service (registered as `projectService` in [plugin.xml](../../../../resources/META-INF/plugin.xml)) that memoizes `PubRoot` lookups.
- [PubRoots.java](PubRoots.java): static helpers that list the pub roots of a module or project.

## Interface
- `class PubRoot`
  - Constants: `DOT_DART_TOOL`, `PACKAGE_CONFIG_JSON`, `PUBSPEC_YAML`
  - Factories: `forFile(VirtualFile)`, `forPsiFile(PsiFile)`, `forDescendant(VirtualFile, Project)`, `forDirectory(VirtualFile)`, `forDirectoryWithRefresh(VirtualFile)`, `forEventWithRefresh(AnActionEvent)`
  - `refresh()`
  - `getRoot()`, `getPath()`, `getPubspec()`, `getRelativePath(VirtualFile)`
  - `declaresFlutter()`, `declaresResolutionWorkspace()`, `isFlutterPlugin()`
  - `getPackageConfigFile()`, `getPackagesMap()`, `hasUpToDatePackages()`
  - `getLib()`, `getLibMain()`, `getFileToOpen()`, `getExampleDir()`, `getExampleLibMain()`, `getIntegrationTestDir()`, `getAndroidDir()`, `getiOsDir()`
  - `hasTests(VirtualFile)`, `hasAndroidModule(Project)`, `getModule(Project)`
  - `static isPubspec(VirtualFile)`
- `class PubRootCache`
- `PubRootCache.getInstance(Project)`
- `PubRootCache.getRoot(VirtualFile)`, `PubRootCache.getRoot(PsiFile)`
- `PubRootCache.getRoots(Module)`, `PubRootCache.getRoots(Project)`
- `class PubRoots`
- `PubRoots.forModule(Module)`, `PubRoots.forProject(Project)`

## Invariants
- A PubRoot encapsulates a directory that contained a `pubspec.yaml` file when the object was created. The file's existence is checked only at construction and its content is not validated, so a PubRoot can become stale. Call `refresh()` to get an up-to-date snapshot (or null).
- PubRootCache is instantiated as a singleton per Project via `project.getService()`.
- PubRoots and most PubRoot static factory methods rely on the IntelliJ VFS cache rather than performing disk I/O, unless explicitly named with 'Refresh'. The exception is `hasUpToDatePackages()`, which compares `java.io.File.lastModified()` timestamps of `pubspec.yaml` and `package_config.json` directly on disk.
- `PubRoot.forFile` never checks the file's immediate directory. For a directory it checks the directory's *parent*, and for a file it recurses into the parent directory, which in turn checks that directory's parent.
- `PubRoot.hasTests(dir)` returns true only if `dir` equals the pub root, or if its path contains `/test/`, `/integration_test/`, `/test_driver/` or `/testing/`.
- In PubRootCache, null is returned if no parent directory contains a pubspec.yaml.
- PubRootCache entries are never invalidated, and the backing `HashMap` is not synchronized.

## Side Effects
- `PubRoot.forEventWithRefresh`, `PubRoot.forDirectoryWithRefresh` and `PubRoot.refresh` call `dir.refresh(false, false)`. This is a synchronous, non-recursive VFS refresh of the root directory only. Despite the Javadoc, the `lib` directory is not refreshed.
- PubRoot.declaresFlutter and PubRoot.declaresResolutionWorkspace lazily parse and cache pubspec.yaml information, updating the cache if the file's modification stamp changes.
- PubRootCache modifies its internal HashMap state when querying for roots that are not yet cached.
- PubRoot.forDescendant performs a VFS read action to locate content roots.
