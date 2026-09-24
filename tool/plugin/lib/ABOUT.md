<!--* freshness: { reviewed: '2026-08-31' } *-->
# plugin/lib

## Overview
Core library implementing plugin generation, linting, building, and deployment commands.

## Interface
- `class BuildSpec`
- `Set<EditCommand> appliedEditCommands`
- `void checkAndClearAppliedEditCommands()`
- `List<EditCommand> editCommands`
- `Future<int> applyEdits(BuildSpec spec, Future<int> Function() compileFn)`
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
- `bool isPresubmitFileValid()`
- `Future<int> jar(String directory, String outFile)`
- `Future<bool> performReleaseChecks(ProductCommand cmd)`
- `class DeployCommand extends ProductCommand`
- `class GenerateCommand extends ProductCommand`
- `abstract class ProductCommand extends Command<int>`
- `class RenamePackageCommand extends ProductCommand`
- `class BuildCommandRunner extends CommandRunner<int>`
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
- Globals like `rootPath`, `lastReleaseName`, and `lastReleaseDate` are initialized early during `ProductCommand` execution.
- `applyEdits` restores any skipped files or modified contents in a `finally` block.
- `EditCommand` objects require the lengths of the `initials` and `replacements` lists to match.
- `ProductCommand` determines release validity using a strictly formatted version regex (`major.minor` with an optional `-dev.digit` suffix).
- `performReleaseChecks` validates the existence of `resources/jxbrowser/jxbrowser.properties` and a valid license key (calling `readAsStringSync` prior to `existsSync`, raising a `PathNotFoundException` if the file is missing).

## Side Effects
- Executes external processes (`git`, `jar`, `sh`, `bash`, `gradlew`, `curl`).
- Performs destructive file system modifications like file copies, temporary backups, edits, and deletions (e.g. `GenerateCommand`, `RenamePackageCommand`).
- Writes to temporary directories and generates a temporary bash script (named `script`) that sets `JAVA_HOME` and calls `./gradlew`, which is executed and deleted.
- Modifies the working directory globally (`Directory.current`) during `DeployCommand`.
- Sends HTTP requests via shell curl to upload plugin distributions using tokens fetched from the environment (`readTokenFromKeystore`).
- `BuildSpec` reads `CHANGELOG.md` directly from the filesystem.
- Prints logging output to stdout.

## Verification
- Build / Analysis: `dart analyze`
- Test: `dart test`
