Contributing to Flutter Plugin for IntelliJ
=======================

<!-- TOC -->
* [Contributing to Flutter Plugin for IntelliJ](#contributing-to-flutter-plugin-for-intellij)
  * [Contributing code](#contributing-code)
  * [Getting started](#getting-started)
  * [Environment set-up](#environment-set-up)
  * [IntelliJ set-up](#intellij-set-up)
    * [Configure "Project Structure" settings](#configure-project-structure-settings)
    * [Configure the Gradle settings](#configure-the-gradle-settings)
    * [Build and run the plugin](#build-and-run-the-plugin)
  * [Provision Tool](#provision-tool)
  * [Running plugin tests](#running-plugin-tests)
    * [Using test run configurations in IntelliJ](#using-test-run-configurations-in-intellij)
    * [Using the command line](#using-the-command-line)
  * [Adding platform sources](#adding-platform-sources)
  * [Flutter DevTools Integration](#flutter-devtools-integration)
    * [Working with Embedded DevTools (JxBrowser)](#working-with-embedded-devtools-jxbrowser)
    * [Developing with local DevTools](#developing-with-local-devtools)
<!-- TOC -->

## Contributing code

![GitHub contributors](https://img.shields.io/github/contributors/flutter/flutter-intellij.svg)

We gladly accept contributions via GitHub pull requests!
If you are new to coding IntelliJ plugins,
here are a couple of links to get started:

- [INTRODUCTION TO CREATING INTELLIJ IDEA PLUGINS](https://developerlife.com/2020/11/21/idea-plugin-example-intro/)
- [ADVANCED GUIDE TO CREATING INTELLIJ IDEA PLUGINS](https://developerlife.com/2021/03/13/ij-idea-plugin-advanced/)

You must complete the [Contributor License Agreement](https://cla.developers.google.com/clas)
before any of your contributions with code get merged into the repo.
If you've never submitted code before, you must add your (or your organization's)
name and contact info to the [AUTHORS](AUTHORS) file.

## Getting started

1. Install Flutter SDK from [Flutter SDK download](https://flutter.dev/docs/get-started/install) or
   [GitHub](https://github.com/flutter/flutter) and set it up according to its instructions.
2. Verify installation from the command line:
    - Connect an Android device with USB debugging.
    - `cd path/to/flutter/examples/hello_world`
    - `flutter pub get`
    - `flutter doctor`
    - `flutter run`
3. Fork `https://github.com/flutter/flutter-intellij` into your own GitHub account.
   If you already have a fork and are now installing a development environment on a new machine,
   make sure you've updated your fork with the main branch
   so that you don't use stale configuration options from long ago.
4. `git clone https://github.com/<your_name_here>/flutter-intellij`
5. `cd flutter-intellij`
6. `git remote add upstream https://github.com/flutter/flutter-intellij`
   The name `upstream` can be whatever you want.

## Environment set-up

1. Install the latest [Java Development Kit](https://www.java.com/en/download/).
    - The current Java Development Kit version is: **23**.
    - **[Googlers only]** Install Java from go/softwarecenter instead.

2. Set your `JAVA_HOME` directory in the configuration file for your shell environment.
    - For example, on macOS, the following works:
      Check what version of Java you have:
      ```shell
      /usr/libexec/java_home -V
      ```
      This should print out a Java version such as:
      ```shell
      Matching Java Virtual Machines (1):
      23.0.2 (arm64) "Azul Systems, Inc." - "Zulu 23.32.11" /Library/Java/JavaVirtualMachines/zulu-23.jdk/Contents/Home
      /Library/Java/JavaVirtualMachines/zulu-23.jdk/Contents/Home
      ```
      In your shell configuration file (e.g. `.bashrc` or `.zshrc`), set your `JAVA_HOME` env variable to match that version.
      ```shell
      export JAVA_HOME=`/usr/libexec/java_home -v 23.0.2`
      ```

3. Set your `FLUTTER_SDK` path in the configuration file for your shell environment.
    - For example, on macOS, the following works:
      Check where your Flutter SDK is installed:
      ```shell
      which flutter
      ```
      This should print out a path to the Flutter SDK, such as:
      ```shell
      home/path/to/flutter/bin/flutter
      ```
      In your shell configuration file (e.g. `.bashrc` or `.zshrc`), set your `FLUTTER_SDK` env variable to match the path.
      ```shell
      export FLUTTER_SDK="$HOME/path/to/flutter"
      ```

4. Set your `DART_SDK` path in the configuration file for your shell environment.
    - In your shell configuration file (e.g. `.bashrc` or `.zshrc`), set your `DART_SDK` env variable to match the Dart SDK in your Flutter SDK. This should look like the `FLUTTER_SDK` path (added above) with `/bin/cache/dart-sdk` appended to the end. 
    ```shell
    export DART_SDK="$HOME/path/to/flutter/bin/cache/dart-sdk"
    ```

5. Add `DART_SDK`, `FLUTTER_SDK` and `JAVA_HOME` to your `PATH`.
    - In your shell configuration file (e.g. `.bashrc` or `.zshrc`), below where your `JAVA_HOME`, `FLUTTER_SDK`, `DART_SDK` env variables were set, add the following line:
    ```shell
    export PATH=$DART_SDK/bin:$FLUTTER_SDK/bin:$JAVA_HOME/bin:$PATH
    ```

6. Update your current `PATH`.
    - Either restart your terminal or run `source ~/.zshrc` / `source ~/.bashrc` to add the new environment variables to your `PATH`.

## IntelliJ set-up

1. Make sure you're using the latest stable release of IntelliJ,
   or download and install the latest version of IntelliJ (2023.1 or later). 
    - [IntelliJ Downloads](https://www.jetbrains.com/idea/download/)
    - Either the community edition (free) or Ultimate will work.

2. Start IntelliJ IDEA with the `flutter-intellij` project.
   - If you see a popup with "Gradle build scripts found",
     **confirm loading the Gradle project, and wait until syncing is done.**
     - If you didn't see the popup at the first start, **delete & re-clone the repo** and try again.
   - Install DevKit plugin when prompted (this is required for later steps)
   - Ignore suggestion for `protobuf-java` plugin, unless you want it.

3. Prepare other dependencies from the command line:
    - `cd path/to/flutter-intellij`
    - `dart pub get`
    - `(cd tool/plugin; dart pub get)`
    - `./gradlew test`
    - Note: If there are Dart errors during build originating in the `tool/plugin` directory, try deleting the contents of the `pubspec.lock` file and re-running the pub get steps. This will allow you to get a fresh set of pub packages.

### Configure "Project Structure" settings

1. From IntelliJ, Open the "Project Structure" dialog (`File | Project Structure`).

2. Add the IntelliJ JBR from disk:
    - Select "Platform Settings > SDKs"
    - Click the "+" sign at the top, click "Add New SDK (Alt+Insert)", then select "Add JDK from disk...".
    - Select your IntelliJ application (most likely under `Applications`) and from there, select the `Contents/jbr/Contents/Home` directory
    - **[For macOS]** You won't be able to select the `Contents` directory from Finder without right-clicking on the IntelliJ application, and selecting "Quick Look" from the dropdown that opens. From there, you can select the `Contents` directory.
    - Change the name so that you can easily identify it, e.g. `IDEA JBR 21`.
    - When you are done, your settings should look something like:
    ```
    Name: IDEA JBR 21
    JDK home path: /Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home
    ```

3. Add the IntelliJ Platform Plugin SDK
    - Select "Platform Settings > SDKs"
    - Click the "+" sign at the top, click "Add New SDK (Alt+Insert)", then select "Add IntelliJ Platform Plugin SDK...".
    - **[Note]** If you don't see this option, ensure you have the DevKit plugin installed.
    - Select your IntelliJ application (most likely under `Applications`) and from there, select the `Contents` directory
    - **[For macOS]** You won't be able to select the `Contents` directory from Finder without right-clicking on the IntelliJ application, and selecting "Quick Look" from the dropdown that opens. 
    - Remember the generated name (probably `IntelliJ IDEA IU-231.8109.175`) or change to name to a format like this.
    - Change the **Internal Java Platform** to the JBR you added in step 2. (e.g. `IDEA JBR 21`).
    - When you are done, your settings should look something like:
    ```
    Name: IntelliJ IDEA Community Edition IC-243.23654.189
    IntelliJ Platform Plugin SDK home path: /Applications/IntelliJ IDEA CE.app/Contents
    Internal Java Platform: IDEA JBR 21
    ``` 

4. Set the SDK for the Project
    - Select "Project Settings > Project"
    - Change the "SDK" selection to the **IntelliJ Platform Plugin SDK** you added in step 3.
    - When you are done, your settings should look something like:
    ```
    SDK: IntelliJ IDEA Community Edition IC-243.23654.189
    ```

5. Configure the modules for the Project
    - Select "Project Settings > Modules"
    - Select the `flutter-intellij` module
    - Switch to the "Paths" window
    - Select the **Inherit project compile output path** option, then apply.
    - Do the same for the subdirectories, `main` and `test`.

6. Change the modules SDK to the Project SDK
    - Select "Project Settings > Modules"
    - Select all the subdirectories under the `flutter-intellij` module
    - Switch to the "Dependencies" window
    - Change the "Module SDK" selection to `Project SDK`.

### Configure the Gradle settings

1. From IntelliJ, open the "Settings" dialog (`IntelliJ IDEA | Settings`).

2. Change the Gradle JVM to the Project SDK 
    - Select "Build, Execution, Deployment > Build Tools > Gradle"
    - Change "Gradle JVM" selection to "Project SDK".
    - When you are done, your settings should look something like:
    ```
    Gradle JVM: Project SDK IDEA JBR 21 
    ```   

3. Configure the Java compiler
    - Select "Build, Execution, Deployment > Compiler > Java Compiler"
    - Change the "Project bytecode version" to the major version of your Java version. 
    - For example, if your Java version is `23.0.2 `, set it to `23`.
    - When you are done, your settings should look something like:
    ```
    Project bytecode version: 23
    ```

4. Configure the Kotlin compiler
    - Select "Build, Execution, Deployment > Compiler > Java Compiler"
    - Change the "Target JVM version" to the same version as the Java compiler (step 3). 
    - When you are done, your settings should look something like:
    ```
    Target JVM version: 23
    ```

### Build and run the plugin

1. One-time Dart plugin install - first time a new IDE is installed and run, you will need to install the Dart plugin.
    - Find `Plugins` (in "File | Settings | Plugins") and install the Dart plugin, then restart the IDE if needed.

2. [Optional] Add a key for JX Browser (see **Working with Embedded DevTools (JxBrowser)** below)
    - **[Note]** This is only required if you are making changes to the embedded views.

3. Build the project using `Build` | `Build Project`.

4. Try running the plugin; select the `flutter-intellij [runIde]` run config, then click the Debug icon.
    This should open the "runtime workbench", a new instance of IntelliJ IDEA with the plugin installed.

5. If the Flutter Plugin doesn't load (Dart code or files are unknown), see above "One-time Dart plugin install".

6. Verify installation of the Flutter plugin:
    - Select `flutter-intellij [runIde]` in the Run Configuration drop-down list.
    - Click the Debug button (to the right of that drop-down).
    - In the new IntelliJ process that spawns, open the `path/to/flutter/examples/hello_world` project.
    - Choose `Edit Configurations...` in the Run Configuration drop-down list.
    - Expand `Edit configuration templates...` and verify that Flutter is present.
    - Click [+] and verify that Flutter is present.

## Provision Tool

This is not currently required. However, for debugging unit tests, it may be handy; please ignore for now.

The Gradle build script currently assumes that some dependencies are present in the `artifacts` directory.
This project uses an External Tool in IntelliJ to ensure the dependencies are present.
It appears that external tools are not shareable.
To make one, open the "Run Configuration" dialog and select "Flutter Plugin".
Look at the "Before launch" panel. It probably displays an Unknown External Tool.
Double-click that, then click the edit icon in the new panel (pencil).
Set the name to "Provision". Set the values:

- Program: `/bin/bash`
- Arguments: `bin/plugin test -s`
- Working directory: `$ProjectFileDir$`
    - You can select that from a list by clicking the "+" symbol in the field

There is a screenshot of this dialog in `resources/intellij/Provision.png`.

Save, close, and re-open the "Run Configuration" dialog.
Confirm that the External Tool is named "Provision".

If you know where the Application Support files are located for your version of IntelliJ,
you can drop the definition into its `tools` directory before starting IntelliJ.
The definition is in `resources/intellij/External Tools.xml`.

## Running plugin tests

### Using test run configurations in IntelliJ

[Note: this may be out-of-date. It has not been verified recently.]

The repository contains two pre-defined test run configurations.
One is for "unit" tests; that is, currently defined as tests
that do not rely on the IntelliJ UI APIs.
The other is for "integration" tests - tests that do use the IntelliJ UI APIs.
The integration tests are larger, long-running tests that exercise app use cases.

To be able to debug a single test class or test method, you need to do the following:

* Open the test source file in the editor. Navigate to `flutter-idea/testSrc/unit/...` to open it.
* Tests must run using Gradle, so be sure to open the source from the Gradle module.
* Find the test you want to run. Right-click the green triangle next to the test name and choose `Debug <test-name>`.

The test configuration can be tricky due to the IntelliJ platform versioning.
The plugin tool (below) can be a more reliable way to run tests.

### Using the command line

To run unit tests on the command line:

```
./gradlew test
```

If you wanted to run a subset of the tests, you could do so this way.
See the [Gradle docs](https://docs.gradle.org/current/userguide/java_testing.html)
for more info about testing.

## Adding platform sources

Sometimes browsing the source code of IntelliJ helps understand platform details that aren't documented.

- To have the platform sources handy, clone the IntelliJ IDEA Community Edition repo
  (`git clone https://github.com/JetBrains/intellij-community`)
- Sync it to the same version of IntelliJ as given by `baseVersion` in gradle.properties (`git checkout 211.7628`).
  It will be in "detached HEAD" mode.
- Open the Project Structure dialog (`File > Project Structure`). In the `IntelliJ IDEA Community Edition` SDK,
  head over to the `Sourcepaths` tab and add the path to `intellij-community`.
  Accept all the root folders found by the IDE after scanning.
- Do the same for the `intellij-plugins` repo to get Dart plugin sources. Sync to the same version as before.

## Flutter DevTools Integration

### Working with Embedded DevTools (JxBrowser)

We use [JxBrowser](https://www.teamdev.com/jxbrowser),
a commercial product, to embed DevTools within IntelliJ.
A license key is required to use this feature in development,
but it is not required for developing unrelated (most) features.

Getting a key to develop JxBrowser features:

- Internal contributors: Ask another internal contributor to give you access to the key.
- External contributors: Our license key cannot be transferred externally,
  but you can acquire your own trial or regular license from
  [TeamDev](https://www.teamdev.com/jxbrowser) to use here.

To set up the license key:

1. Copy the template at resources/jxbrowser/jxbrowser.properties.template
   and save it as resources/jxbrowser/jxbrowser.properties.
2. Replace `<KEY>` with the actual key.

### Developing with local DevTools

By default, the DevTools version in IntelliJ will match the DevTools version shipped with Flutter. To instead use a local DevTools version (which will be launched with `dt serve`), the following steps are required:

1. Make sure you have `dt` installed
    - Follow instructions in the [DevTools set-up guide](https://github.com/flutter/devtools/blob/master/CONTRIBUTING.md#set-up-your-devtools-environment)
2. Set the registry key to your DevTools directory
    - Go to Help > Find action > Registry > Find "flutter.local.devtools.dir" and set it to your DevTools directory. (e.g. Users/user/dev/devtools)
    - Optional: If you want to pass additional arguments to `dt serve`, put these in the option "flutter.local.devtools.args"
3. Restart IntelliJ
4. Open Help > Show log in finder > Open idea.log
    - Verify you see output like see output `"DevTools startup: <various messages that come from running dt serve>"`
5. To stop using local DevTools, go back to the registry key set in step 2 and remove it
