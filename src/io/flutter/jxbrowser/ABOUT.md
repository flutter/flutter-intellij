<!--* freshness: { reviewed: '2026-08-17' } *-->
# Embedded JxBrowser

## Overview
Manages the lifecycle, downloading, and initialization of the JxBrowser embedded browser engine used for DevTools.

## Interface
- `class EmbeddedBrowserEngine`
- `class EmbeddedJxBrowser`
- `enum FailureType`
- `class InstallationFailedReason`
- `class JxBrowserManager`
- `enum JxBrowserStatus`

## Invariants
- EmbeddedBrowserEngine is an application service.
- EmbeddedJxBrowser is a project service.
- JxBrowserManager operates as a singleton.
- JxBrowser installation status is globally tracked via a single CompletableFuture.
- Installation progresses asynchronously off the Event Dispatch Thread to prevent UI freezes.

## Side Effects
- Downloads JxBrowser platform, API, and Swing files to the IDE plugins directory.
- Deletes existing JxBrowser files prior to re-downloading.
- Creates a JxBrowser 'user-data' directory on the filesystem.
- Dynamically injects downloaded JxBrowser jars into the runtime classloader.
- Sets global JVM properties for JxBrowser (e.g., license key, DPI awareness, logging configuration).
