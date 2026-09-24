<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Pub Roots

## Overview
Abstracts interactions with Dart/Flutter package roots and tracks pubspec.yaml locations to understand workspace boundaries.

## Interface
- `class PubRoot`
- `PubRoot.forFile(VirtualFile)`
- `PubRoot.refresh()`
- `PubRoot.getRoot()`
- `PubRoot.getPath()`
- `PubRoot.getPubspec()`
- `PubRoot.declaresFlutter()`
- `PubRoot.declaresResolutionWorkspace()`
- `PubRoot.isFlutterPlugin()`
- `class PubRootCache`
- `PubRootCache.getInstance(Project)`
- `PubRootCache.getRoot(VirtualFile)`
- `PubRootCache.getRoots(Project)`
- `class PubRoots`
- `PubRoots.forProject(Project)`

## Invariants
- A PubRoot encapsulates a directory that is guaranteed to contain a valid pubspec.yaml file.
- PubRootCache is instantiated as a singleton per Project via `project.getService()`.
- PubRoots and most PubRoot static factory methods rely on the IntelliJ VFS cache rather than performing disk I/O, unless explicitly named with 'Refresh'.
- PubRoot.hasTests checks if a directory is within the pub root or well-known test directories.
- In PubRootCache, null is returned if no parent directory contains a pubspec.yaml.

## Side Effects
- PubRoot.forEventWithRefresh, PubRoot.forDirectoryWithRefresh, and PubRoot.refresh trigger synchronous or asynchronous VirtualFile cache refreshes via VFS.
- PubRoot.declaresFlutter and PubRoot.declaresResolutionWorkspace lazily parse and cache pubspec.yaml information, updating the cache if the file's modification stamp changes.
- PubRootCache modifies its internal HashMap state when querying for roots that are not yet cached.
- PubRoot.forDescendant performs a VFS read action to locate content roots.
