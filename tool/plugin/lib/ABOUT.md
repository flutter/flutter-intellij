<!--* freshness: { reviewed: '2026-10-25' } *-->
# plugin/lib

## Overview
Core library for the `plugin` tool. It implements the `lint`, `generate`, and `deploy` commands. It also still contains code from the old
Dart-based builder (`BuildSpec`, `applyEdits`, `runGradleCommand`, `RenamePackageCommand`) that nothing calls any more; the plugin is now
built with Gradle.

## Files
- [`plugin.dart`](plugin.dart): `main` (registers `LintCommand`, `DeployCommand`, `GenerateCommand`), `ProductCommand` (shared options and
  release-mode logic), `DeployCommand`, `GenerateCommand`, `performReleaseChecks`, the unregistered `RenamePackageCommand`, and file helpers.
- [`lint.dart`](lint.dart): `LintCommand`, which checks for bad imports and prints Dart plugin API usage.
- [`runner.dart`](runner.dart): `BuildCommandRunner`, which defines the global `--release` and `--cwd` options.
- [`globals.dart`](globals.dart): Shared constants and mutable globals.
- [`util.dart`](util.dart): Process, logging, git-history, filesystem, and keystore helpers.
- [`build_spec.dart`](build_spec.dart): `BuildSpec` (unused legacy build target model).
- [`edit.dart`](edit.dart): `EditCommand` and `applyEdits` (unused legacy source edits; `editCommands` is empty).

## Interface
- `class BuildSpec` (never instantiated)
- `Set<EditCommand> appliedEditCommands`
- `void checkAndClearAppliedEditCommands()`
- `List<EditCommand> editCommands` (empty; entries are commented out)
- `Future<int> applyEdits(BuildSpec spec, Future<int> Function() compileFn)` (never called)
- `class EditCommand`
- `const Map<String, String> pluginRegistryIds`
- `const int cloudErrorFileMaxSize`
- `String rootPath`
- `String lastReleaseName`
- `DateTime lastReleaseDate`
- `int pluginCount`
- `class LintCommand extends Command<int>`
- `Future<int> main(List<String> args)`
- `void addProductFlags(ArgParser argParser, String verb)`
- `void copyResources({required String from, required String to})`
- `List<File> findJars(String path)`
- `List<String> findJavaFiles(String path)`
- `bool isPresubmitFileValid()` (only checks that `.github/workflows/presubmit.yaml` exists)
- `Future<int> jar(String directory, String outFile)`
- `Future<bool> performReleaseChecks(ProductCommand cmd)`
- `class DeployCommand extends ProductCommand`
- `class GenerateCommand extends ProductCommand`
- `abstract class ProductCommand extends Command<int>`
- `class RenamePackageCommand extends ProductCommand` (not registered in `main`)
- `class BuildCommandRunner extends CommandRunner<int>` (its `runGradleCommand` and `runShellScript` are never called)
- `Future<int> exec(String cmd, List<String> args, {String? cwd})`
- `Future<String> makeDevLog(BuildSpec spec)`
- `Future<DateTime> dateOfLastRelease()`
- `Future<String> lastRelease()`
- `final Ansi ansi`
- `void separator(String name)`
- `void log(String s, {bool indent = true})`
- `void createDir(String name)`
- `Future<int> curl(String url, {required String to})`
- `Future<void> removeAll(String dir)`
- `bool isNewer(FileSystemEntity newer, FileSystemEntity older)`
- `String readTokenFromKeystore(String keyName)`
- `int get devBuildNumber`
- `String buildVersionNumber(BuildSpec spec)`

## Invariants
- Globals like `rootPath`, `lastReleaseName`, and `lastReleaseDate` are initialized early during `ProductCommand` execution (the last two only on the dev channel).
- `applyEdits` restores any skipped files or modified contents in a `finally` block.
- `EditCommand` objects require the lengths of the `initials` and `replacements` lists to match.
- `ProductCommand` determines release validity using a strictly formatted version regex (`major.minor` with an optional `-dev.digit` suffix).
- `DeployCommand` requires `--channel=dev`, while `performReleaseChecks` rejects the dev channel, so `deploy` can't be used in release mode.
- `performReleaseChecks` validates the existence of `resources/jxbrowser/jxbrowser.properties` and a valid license key (calling `readAsStringSync` prior to `existsSync`, raising a `PathNotFoundException` if the file is missing). It only reaches that check when the git/branch/presubmit checks fail; when they pass it returns `true` early.

## Side Effects
- Executes external processes (`git`, `jar`, `sh`, `curl`; plus `bash` and `gradlew` in the unused `BuildCommandRunner` methods).
- Performs destructive file system modifications: `GenerateCommand` rewrites `resources/liveTemplates/flutter_miscellaneous.xml`; the unused `applyEdits` and `RenamePackageCommand` would edit, rename, and delete sources.
- The unused `runShellScript` (reached only from `runGradleCommand` when `spec.version == '4.1'`) writes a temporary bash script named `script` that sets `JAVA_HOME` and calls `./gradlew`, then runs and deletes it.
- Modifies the working directory globally (`Directory.current`) during `DeployCommand`.
- Sends HTTP requests via shell curl to upload `build/distributions/flutter-intellij-kokoro.zip`, using a token read from the Kokoro keystore file (`readTokenFromKeystore`).
- `BuildSpec` reads `CHANGELOG.md` directly from the filesystem.
- Prints logging output to stdout.

## Verification
- Build / Analysis: `dart analyze`
- Test: `dart test`
