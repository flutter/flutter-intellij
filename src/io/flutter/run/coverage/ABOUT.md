<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Coverage

## Overview
Configures test coverage execution and parsing (LCOV) for Flutter applications.

## Interface
- `FlutterCoverageAnnotator`
- `FlutterCoverageEnabledConfiguration`
- `FlutterCoverageEngine`
- `FlutterCoverageProgramRunner`
- `FlutterCoverageRunner`
- `FlutterCoverageSuite`
- `LcovInfo`

## Invariants
- FlutterCoverageAnnotator does not collect coverage inside library directories.
- FlutterCoverageEnabledConfiguration expects coverage file at 'coverage/lcov.info' relative to the pub root.
- FlutterCoverageEngine applies only to TestConfig runs.
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
- FlutterCoverageProgramRunner adds and later removes a process listener.
- FlutterCoverageProgramRunner invokes VfsUtil to refresh the coverage directory on process termination.
- FlutterCoverageProgramRunner invokes CoverageDataManager.processGatheredCoverage on process termination.
- LcovInfo.readInto modifies the passed ProjectData object, appending parsed coverage data.
