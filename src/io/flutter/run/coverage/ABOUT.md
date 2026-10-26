<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Coverage

## Overview
Configures test coverage execution and parsing (LCOV) for Flutter applications.

## Interface
- [`FlutterCoverageAnnotator`](FlutterCoverageAnnotator.java)
- [`FlutterCoverageEnabledConfiguration`](FlutterCoverageEnabledConfiguration.java)
- [`FlutterCoverageEngine`](FlutterCoverageEngine.java)
- [`FlutterCoverageProgramRunner`](FlutterCoverageProgramRunner.java)
- [`FlutterCoverageRunner`](FlutterCoverageRunner.java)
- [`FlutterCoverageSuite`](FlutterCoverageSuite.java)
- [`LcovInfo`](LcovInfo.java)

## Invariants
- FlutterCoverageAnnotator does not collect coverage inside library directories, and limits coverage roots to the content roots of modules with Flutter contents.
- FlutterCoverageEnabledConfiguration expects coverage file at 'coverage/lcov.info' relative to the pub root.
- FlutterCoverageEngine applies only to [`TestConfig`](../test/TestConfig.java) runs (a `WrappingRunConfiguration` is unwrapped to its peer before the check).
- FlutterCoverageEngine only creates suites from a FlutterCoverageEnabledConfiguration; the legacy `createCoverageSuite(..., filters, timestamps, ...)` overload always returns null.
- FlutterCoverageEngine editor highlighting applies only to Dart files in 'lib' subdirectory.
- FlutterCoverageEngine canHavePerTestCoverage is always true.
- FlutterCoverageProgramRunner ID is 'FlutterCoverageProgramRunner'.
- FlutterCoverageProgramRunner executes only for CoverageExecutor on TestConfig.
- FlutterCoverageRunner ID is 'FlutterCoverageRunner', extension is 'info', presentable name is 'Flutter'.
- FlutterCoverageRunner accepts only FlutterCoverageEngine and FlutterCoverageSuite.
- FlutterCoverageSuite deleteCachedCoverageData is a no-op.
- LcovInfo assumes base path ends before 'coverage' in file path.
- LcovInfo gracefully handles integer parsing failures by falling back to 0.

## Side Effects
- FlutterCoverageEnabledConfiguration adds coverage suite to CoverageDataManager asynchronously.
- FlutterCoverageProgramRunner adds a process listener. It removes the listener only after processing coverage, when `coverage/lcov.info` exists and runner settings are non-null; if the file is missing it logs an error and the listener stays attached.
- FlutterCoverageProgramRunner invokes VfsUtil to refresh the coverage directory on process termination.
- FlutterCoverageProgramRunner invokes CoverageDataManager.processGatheredCoverage on process termination.
- LcovInfo.readInto modifies the passed ProjectData object, appending parsed coverage data.
