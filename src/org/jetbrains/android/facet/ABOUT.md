<!--* freshness: { reviewed: '2026-10-25' } *-->
# Android Facet Detection

## Overview
Detects standard Android modules (facets) within the project, bypassing detection for Gradle-based projects. The class lives in the Android plugin's `org.jetbrains.android.facet` package (shadowing it) and depends on Android plugin classes such as `com.android.tools.idea.gradle.project.Info`.

It is not registered as a framework detector extension. Its only consumer is `excludeAndroidFrameworkDetector` in [ProjectOpenActivity](../../../../io/flutter/ProjectOpenActivity.java), which instantiates it to get `getFrameworkType()` and exclude Android framework detection for Flutter projects.

## Interface
- [`AndroidFrameworkDetector`](AndroidFrameworkDetector.java) - extends `FacetBasedFrameworkDetector<AndroidFacet, AndroidFacetConfiguration>`
- `AndroidFrameworkDetector()` - calls `super("android")` (detector ID)
- `List<? extends DetectedFrameworkDescription> detect(Collection<? extends VirtualFile> newFiles, FrameworkDetectionContext context)`
- `FacetType<AndroidFacet, AndroidFacetConfiguration> getFacetType()`
- `FileType getFileType()`
- `ElementPattern<FileContent> createSuitableFilePattern()`

## Invariants
- Framework detection is bypassed (returns empty list) if the project uses Gradle or has a top-level Gradle file. The Gradle check is skipped when `context.getProject()` is null, in which case detection falls through to `super.detect`.
- The detector looks for files of type `XmlFileType`.
- The detector specifically targets files named `AndroidManifest.xml` (`SdkConstants.FN_ANDROID_MANIFEST_XML`).
- Returns `AndroidFacet.getFacetType()` for the facet type.
- Contains an unused private helper `getFirstAsBoolean` and a commented-out legacy method `showDexOptionNotification`.

## Side Effects
- Reads project information (`Info.getInstance(project)`) to check Gradle usage.
