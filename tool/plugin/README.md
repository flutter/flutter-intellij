
# Flutter Plugin Maintenance Tool

The previous builder for the Flutter plugin was written in Ant. An expert in Ant would probably have no trouble maintaining it, but we have no such expert available. In order to increase stability and productivity we transformed the builder into a Dart command line tool, inspired by the Flutter tool. The tool is named `plugin`, since it helps with plugin development.


## Functions

The tool needs to perform several functions. Building is the basic one, but others are important too. Implementation note: these will be implemented as compound commands in the CLI tool (similar to `flutter doctor` or `flutter run` in the Flutter CLI tool).



*   `build`
*   `test`
*   `deploy`
*   `gen`
*   `lint`

Each command has its own set of optional arguments. If the CLI tool is invoked without a command then it will print its help message.

There are two global options. On the command line these are given prior to the command name.



*   `-r, --release=XX`
Use "release mode." The `XX` value specifies the release identifier, like 18 or 19.1. It will be shown in the "version" field of the plugin manager's listing for the Flutter plugin. In this mode additional constraints must be satisfied. The current git branch must have no unsaved changes and must be named "release_XX". The `.travis.yml` file must be newer than the `product-matrix.json` file (see the `gen` command).
*   `-d, --cwd=<working-directory>`
Set the root directory to the `working-directory` value. This should not be used when running the tool from the command line. It is only intended to be used when running tests from IntelliJ run configurations that have no way to specify the working directory.


### Build

Builds may be targeted for local development or distribution.



*   `-[no-]ij`
Build for IntelliJ. Default is true; may be negated by including `no`.
*   `-[no-]as`
Build for Android Studio. Default is true; may be negated by including `no`.
*   `-o, --only-version=ID`
Only build the specified IntelliJ version. `ID` is one of the "version" strings in product-matrix.json.
*   `-u, --unpack`
Normally the archive files are not unpacked if the corresponding directory exists. This flag forces the archive files to be unpacked.


The build function must generate the correct `plugin.xml` for each platform and generate the correct artifacts for each. In release mode the archive files are always unpacked (--unpack is implied). The plugin files are saved in the `artifacts` directory. In release mode a subdirectory of `artifacts` is created which has the name of the release and they are put there. During a local dev/test run the subdirectory is not created and the created files are children of `artifacts`.

Returns a success or failure code to play nice with shell commands.


### Test

Both unit and integration tests need to run, and tests should work whether running locally or on Travis.



*   `-u, --[no-]unit
`Run unit tests (or not).
*   `-i, --[no-]integration`
Run GUI-based integration tests (or not).

TBD: We may need some mechanism to automatically upload testing artifacts, since we can no longer attach zip files to email. This could also potentially be addressed by creating a 'dev' channel for the plugin on the JetBrains store, and publishing to it when we have release candidates.


### Deploy

Automatically upload all required artifacts to the JetBrains site. This function will print instructions to retrieve the password from valentine then prompt for the password to log into the JetBrains site. It has no options. Returns a success or failure code to play nice with shell commands.


### Gen

Generate a `plugin.xml` from `plugin.xml.template` to be used for launching a runtime workbench. This is different from the ones generated during building because it will have a version range that allows use in all targeted platforms.


### Lint

Run the lint tool. It checks for bad imports and prints a report of the Dart plugin API usage.


## Examples

Build the Flutter plugin for IntelliJ and Android Studio, in distribution mode.

	`plugin -r19.2 build`

Build a local version of the IntelliJ version of the plugin, not for distribution.

	`plugin build --no-as`

Run integration tests without doing a build, assuming an edit-build-test cycle during development.

	`plugin test --no-unit`

Build everything then run all tests. The big question here is: Can we get integration tests to run using the newly-built plugin rather than sources?

	`plugin build && plugin test`

## Debugging

It can be difficult to debug issues that only occur with a deployable
plugin built by `plugin build`. Here's how to set up remote
debugging with IntelliJ:

In IntelliJ create a "Remote" type of run configuration. It 
shows you the command-line arg you need to add in the top 
text field of the run config. Copy that text.

Now, (assuming you are on a Mac) find your IJ or AS app in 
the Finder. Right-click, 'Show Package Contents', open 
Contents, edit Info.plist. In the plist editor expand 
JVMOptions and then edit VMOptions. Append the copied text 
to the end. Save the plist editor and launch the AS or IJ app.

Back in IntelliJ, select your new run config and click the 
debug icon to attach the debugger to the app. The copied 
text includes "suspend=n". If you want to set a breakpoint 
before execution begins change that 'n' to 'y'.
