# Flutter Plugin Maintenance Tool

The previous builder for the Flutter plugin was written in Ant. An expert in Ant would probably have no trouble maintaining it, but we have
no such expert available. In order to increase stability and productivity we transformed the builder into a Dart command line tool, inspired
by the Flutter tool. The tool is named `plugin`, since it helps with plugin development.

The plugin itself is now built and tested with Gradle, so the tool no longer has build or test commands. Run it from the repository root
with the [`bin/plugin`](../../bin/plugin) wrapper (`./bin/plugin <command>`), which runs [`bin/main.dart`](bin/main.dart). The commands
are implemented in [`lib/plugin.dart`](lib/plugin.dart) and [`lib/lint.dart`](lib/lint.dart).

## Functions

The tool supports a few compound commands (similar to `flutter doctor` or `flutter run` in the Flutter CLI tool):

* `deploy`
* `generate`
* `lint`

Each command has its own set of optional arguments. If the CLI tool is invoked without a command then it will print its help message.

`deploy` and `generate` share the product options `--[no-]ij` and `--[no-]as` (both default to on) and `-c, --channel=<stable|dev>`
(default `stable`).

`lib/plugin.dart` also defines a `rename` command (`RenamePackageCommand`), but `main` does not register it, so it cannot be run.

There are two global options. On the command line these are given prior to the command name.

* `-r, --release=XX`
  Use "release mode." The `XX` value specifies the release identifier, like 18 or 19.1. It will be shown in the "version" field of the
  plugin manager's listing for the Flutter plugin. In this mode additional constraints are checked (`performReleaseChecks`):
  * The channel must not be `dev`, and the identifier must have the form `major.minor` (optionally with a `-dev.N` suffix).
  * The current git branch must have no uncommitted changes and must be named `release_XX` (a branch like `release_XX.N` is also
    accepted).
  * The `.github/workflows/presubmit.yaml` file must exist.
  * `resources/jxbrowser/jxbrowser.properties` must contain a real `jxbrowser.license.key=` value.
* `-d, --cwd=<working-directory>`
  Set the root directory to the `working-directory` value. This should not be used when running the tool from the command line. It is only
  intended to be used when running tests from IntelliJ run configurations that have no way to specify the working directory.

### Deploy

Upload the plugin to the JetBrains Marketplace (plugin ID 9212). It requires `--channel=dev` and refuses to run otherwise. It reads the
upload token from the Kokoro keystore file `$KOKORO_KEYSTORE_DIR/${FLUTTER_KEYSTORE_ID}_${FLUTTER_KEYSTORE_NAME}` and uploads
`build/distributions/flutter-intellij-kokoro.zip` with `curl`. Returns a success or failure code to play nice with shell commands.

Note that Gradle produces `build/distributions/Flutter.zip`, not `flutter-intellij-kokoro.zip`. The dev channel Kokoro job
([`tool/kokoro/deploy.sh`](../kokoro/deploy.sh)) no longer uses this command; it uploads `Flutter.zip` with `curl` directly.

### Generate

This is now only used for live templates. It copies each `resources/liveTemplates/*.txt` fragment into the matching entry of
`resources/liveTemplates/flutter_miscellaneous.xml`. In release mode it also runs the release-mode checks. CI runs it in the `DART_BOT`
job and fails if the generated file differs from the committed one.

### Lint

Run the lint tool. It checks for bad imports and prints a report of the Dart plugin API usage.

## Debugging

It can be difficult to debug issues that only occur with a deployable plugin. Here's how to set up remote debugging with IntelliJ:

In IntelliJ create a "Remote" type of run configuration. It shows you the command-line arg you need to add in the top text field of the run
config. Copy that text.

Now, (assuming you are on a Mac) find your IJ or AS app in the Finder. Right-click, 'Show Package Contents', open Contents, edit Info.plist.
In the plist editor expand JVMOptions and then edit VMOptions. Append the copied text to the end. Save the plist editor and launch the AS or
IJ app.

Back in IntelliJ, select your new run config and click the debug icon to attach the debugger to the app. The copied text includes "
suspend=n". If you want to set a breakpoint before execution begins change that 'n' to 'y'.
