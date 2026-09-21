<!--* freshness: { reviewed: '2026-08-31' } *-->
# Android Facet Detection

## Overview
Detects standard Android modules (facets) within the project, bypassing detection for Gradle-based projects.

## Interface
- `class AndroidFrameworkDetector`
- `AndroidFrameworkDetector()`
- `List<? extends DetectedFrameworkDescription> detect(Collection<? extends VirtualFile> newFiles, FrameworkDetectionContext context)`
- `FacetType<AndroidFacet, AndroidFacetConfiguration> getFacetType()`
- `FileType getFileType()`
- `ElementPattern<FileContent> createSuitableFilePattern()`

## Invariants
- Framework detection is bypassed (returns empty list) if the project uses Gradle or has a top-level Gradle file.
- The detector looks for files of type `XmlFileType`.
- The detector specifically targets files named `AndroidManifest.xml` (`SdkConstants.FN_ANDROID_MANIFEST_XML`).
- Returns `AndroidFacet.getFacetType()` for the facet type.
- Contains an unused private helper `getFirstAsBoolean` and a commented-out legacy method `showDexOptionNotification`.

## Side Effects
- Reads project information (`Info.getInstance(project)`) to check Gradle usage.
