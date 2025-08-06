# Flutter Plugin Maintenance Tool

The previous builder for the Flutter plugin was written in Ant. An expert in Ant would probably have no trouble maintaining it, but we have
no such expert available. In order to increase stability and productivity we transformed the builder into a Dart command line tool, inspired
by the Flutter tool. The tool is named `plugin`, since it helps with plugin development.

## Functions

The tool needs to perform several functions. Building is the basic one, but others are important too. Implementation note: these will be
implemented as compound commands in the CLI tool (similar to `flutter doctor` or `flutter run` in the Flutter CLI tool).

* `deploy`
* `generate`
* `lint`

Each command has its own set of optional arguments. If the CLI tool is invoked without a command then it will print its help message.

There are two global options. On the command line these are given prior to the command name.

* `-r, --release=XX`
  Use "release mode." The `XX` value specifies the release identifier, like 18 or 19.1. It will be shown in the "version" field of the
  plugin manager's listing for the Flutter plugin. In this mode additional constraints must be satisfied. The current git branch must have
  no unsaved changes and must be named "release_XX". The `.github/workflows/presubmit.yaml` file must be newer than the
  `product-matrix.json` file (see the `generate` command).
* `-d, --cwd=<working-directory>`
  Set the root directory to the `working-directory` value. This should not be used when running the tool from the command line. It is only
  intended to be used when running tests from IntelliJ run configurations that have no way to specify the working directory.

### Deploy

Automatically upload all required artifacts to the JetBrains site. This function will print instructions to retrieve the password from
valentine then prompt for the password to log into the JetBrains site. It has no options. Returns a success or failure code to play nice
with shell commands.
(Used primarily by the dev channel Kokoro job. Not sure if it works for uploading to the stable channel.)

### Generate

This is now only used for live templates (see `resources/liveTemplates`).

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
