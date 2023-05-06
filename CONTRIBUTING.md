Contributing to Flutter Plugin for IntelliJ
=======================

<!-- TOC -->
* [Contributing to Flutter Plugin for IntelliJ](#contributing-to-flutter-plugin-for-intellij)
  * [Contributing code](#contributing-code)
  * [Getting started](#getting-started)
  * [Setting environments](#setting-environments)
    * [Handle symlinks](#handle-symlinks)
  * [Provision Tool](#provision-tool)
  * [Running plugin tests](#running-plugin-tests)
    * [Using test run configurations in IntelliJ](#using-test-run-configurations-in-intellij)
    * [Using the plugin tool on the command line](#using-the-plugin-tool-on-the-command-line)
  * [Adding platform sources](#adding-platform-sources)
  * [Working with Android Studio](#working-with-android-studio)
  * [Working with Embedded DevTools (JxBrowser)](#working-with-embedded-devtools-jxbrowser)
  * [Signing commits](#signing-commits)
<!-- TOC -->

## Contributing code

![GitHub contributors](https://img.shields.io/github/contributors/flutter/flutter-intellij.svg)

We gladly accept contributions via GitHub pull requests!
If you are new to codiing IntelliJ plugins,
here are a couple links to get started:

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
    - Connect an android device with USB debugging.
    - `cd path/to/flutter/examples/hello_world`
    - `flutter pub get`
    - `flutter doctor`
    - `flutter run`
3. Fork `https://github.com/flutter/flutter-intellij` into your own GitHub account.
   If you already have a fork, and are now installing a development environment on a new machine,
   make sure you've updated your fork with the master branch
   so that you don't use stale configuration options from long ago.
4. `git clone -c core.symlinks=true https://github.com/<your_name_here>/flutter-intellij`
5. `cd flutter-intellij`
6. `git remote add upstream https://github.com/flutter/flutter-intellij`
   The name `upsteram` can be whatever you want.

## Setting environments

The current Java Developmenet Kit version is: **17**.

1. Set your `JAVA_HOME` directory in your environment.
    - For example, on macOS, the following works:
      Check what version of java you have:
      ```shell
      /usr/libexec/java_home -V
      ```
      Set your `JAVA_HOME` env variable to match that version.
      ```shell
      export JAVA_HOME=`/usr/libexec/java_home -v 17`
      ```
2. Set your `FLUTTER_SDK` directory to point to `/path/to/flutter`.
3. Also set your `DART_SDK` directory to `/path/to/flutter/bin/cache/dart-sdk`.
4. Ensure both `DART_SDK`, `FLUTTER_SDK` and `JAVA_HOME` are added to the `PATH`
   in the shell initialization script that runs at login.
   (not just for the one used for every interactive shell).
   ```shell
   export PATH=$DART_SDK/bin:$FLUTTER_SDK/bin:$JAVA_HOME/bin:$PATH
   ```
5. Make sure you're using the latest stable release of IntelliJ,
   or sownload and install the latest version of IntelliJ (2023.1 or later).
    - [IntelliJ Downloads](https://www.jetbrains.com/idea/download/)
    - Either the community edition (free) or Ultimate will work.
    - Determine the directory of your downloaded IntelliJ IDEA installation. e.g.
        * `IntelliJ IDEA CE.app` (macOS)
        * `~/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/231.8109.175/IntelliJ IDEA.app` (macOS)
        * `~/idea-IC-231.8109.175` (Linux)
        * `X:\path\to\your\IDEA-U\ch-0\231.8109.175` (Windows after installed)
6. Start the IntelliJ IDEA with the `flutter-intellij` project.
   If you see a popup with "Gradle build scripts found",
   **confirm loading the Gradle project, and wait until syncing is done.**
   If you didn't see the popup at the first start, **delete & re-clone the repo** and try again.
    - Ignore suggestion for `protobuf-java` plugin, unless you want it.
7. Prepare other dependencies from the command line:
    - `cd path/to/flutter-intellij`
    - `dart pub get`
    - `(cd tool/plugin; dart pub get)`
    - `bin/plugin test`
8. In the "Project Structure" dialog (`File | Project Structure`):
    - Select "Platform Settings > SDKs", click the "+" sign at the top "Add New SDK (Alt+Insert)",
      then select "Add JDK...".
        - Point it to the directory of the jbr which is under the IDEA's content (e.g. `IntelliJ IDEA CE.app/Contents/jbr`).
        - Change the name to `IDEA JBR 17` (or any names that your can easily identify).
    - Select "Platform Settings > SDKs", click the "+" sign at the top "Add New SDK (Alt+Insert)",
      then select "Add IntelliJ Platform Plugin SDK...".
        - Point it to the directory of the content which is under the IDEA's installation.
        - Remember the generated name (probably `IntelliJ IDEA IU-231.8109.175`) or change to name to format like this.
        - Change the "Internal Java Platform" to the previous `IDEA JBR 17`.
    - Select "Platform Settings > Project", change the "SDK" selection to **the previous IntelliJ Platform Plugin SDK**
      (probably `IntelliJ IDEA IU-231.8109.175 java version 17`).
    - Select "Platform Settings > Modules".
        - Select "flutter-intellij > flutter-idea > main" module, switch to the "Paths" window,
          select the **Inherit project compile output path** option then apply.
          This step can be repeated after everytime the project is open.
        - Select every module from the top (flutter-intellij) to the bottom (test) (could be 6 modules in summary),
          switch to the "Dependencies" window, change the "Module SDK" selection to `Project SDK`.
9. In the "File | Settings | Build, Execution, Deployment | Build Tools | Gradle" setting:
    - Change "Gradle JVM" selection to "Project SDK".
10. In the "File | Settings | Build, Execution, Deployment | Compiler" setting:
    - In "Java Compiler", change the "Project bytecode version" to the same version of the JDK.
    - In "Kotlin Compiler", change the "Target JVM version" to the same version of the JDK.
11. One-time Dart plugin install - first-time a new IDE is installed and run you will need to install the Dart plugin.
    - Find `Plugins` (in "File | Settings | Plugins") and install the Dart plugin, then restart the IDE if needed.
12. Build the project using `Build` | `Build Project`.
13. Try running the plugin; select the `flutter-intellij [runIde]` run config then click the Debug icon.
    This should open the "runtime workbench", a new instance of IntelliJ IDEA with the plugin installed.
14. If the Flutter Plugin doesn't load (Dart code or files are unknown) see above "One-time Dart plugin install".
15. Verify installation of the Flutter plugin:
    - Select `flutter-intellij [runIde]` in the Run Configuration drop-down list.
    - Click Debug button (to the right of that drop-down).
    - In the new IntelliJ process that spawns, open the `path/to/flutter/examples/hello_world` project.
    - Choose `Edit Configurations...` in the Run Configuration drop-down list.
    - Expand `Edit configuration templates...` and verify that Flutter is present.
    - Click [+] and verify that Flutter is present.

### Handle symlinks

If exceptions like these occurred:

```
A problem occurred configuring project ':flutter-idea'.
> Source directory 'X:\path\to\your\flutter-intellij\flutter-idea\resources' is not a directory.
```

Check out if the directory is a symlink by open the link in IDEA, and it'll display as:

```symlink
../resources
```

Delete the file, then re-clone the repo using the below command:

```shell
git clone -c core.symlinks=true https://github.com/<your_name_here>/flutter-intellij
```

**NOTE**: Avoid symlinks addition during development as possible as you can,
since they can lead to various of file-based issues during the development.

## Provision Tool

This is not currently required. However, for debugging unit tests it may be handy; please ignore for now.

The Gradle build script currently assumes that some dependencies are present in the `artifacts` directory.
This project uses an External Tool in IntelliJ to ensure the dependencies are present.
It appears that external tools are not shareable.
To make one, open the "Run Configuration" dialog and select "Flutter Plugin".
Look at the "Before launch" panel. It probably displays an Unknown External Tool.
Double-click that, then click edit icon in the new panel (pencil).
Set the name to "Provision". Set the values:

- Program: /bin/bash
- Arguments: bin/plugin test -s
- Working directory: $ProjectFileDir$
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
One is for "unit" tests; that is currently defined as tests
that do not rely on the IntelliJ UI APIs.
The other is for "integration" tests - tests that do use the IntelliJ UI APIs.
The integration tests are larger, long-running tests that exercise app use cases.

In order to be able to debug a single test class or test method you need to do the following:

* Open the test source file in the editor. Navigate to `flutter-idea/testSrc/unit/...` to open it.
* Tests must run using Gradle, so be sure to open the source from the Gradle module.
* Find the test you want to run. Right-click the green triangle next to the test name and choose `Debug <test-name>`.

The test configuration can be tricky due to IntelliJ platform versioning.
The plugin tool (below) can be a more reliable way to run tests.

### Using the plugin tool on the command line

To run unit tests on the command line:

```
bin/plugin test
```

See `TestCommand` in `tool/plugin/lib/plugin.dart` for more options.

It is also possible to run tests directly with Gradle, which would allow passing more command-line arguments:

```
./gradlew test
```

If you wanted to run a subset of the tests you could do so this way.
See the [Gradle docs](https://docs.gradle.org/current/userguide/java_testing.html)
for more info about testing.
*However*, you must have run the tests once using the plugin tool,
to ensure all the dependencies have been configured.

## Adding platform sources

Sometimes browsing the source code of IntelliJ is helpful for understanding platform details that aren't documented.

- In order to have the platform sources handy, clone the IntelliJ IDEA Community Edition repo
  (`git clone https://github.com/JetBrains/intellij-community`)
- Sync it to the same version of IntelliJ as given by `baseVersion` in gradle.properties (`git checkout 211.7628`).
  It will be in "detached HEAD" mode.
- Open the Project Structure dialog (`File > Project Structure`). In the `IntelliJ IDEA Community Edition` sdk,
  head over to the `Sourcepaths` tab and add the path to `intellij-community`.
  Accept all the root folders found by the IDE after scanning.
- Do the same for the intellij-plugins repo to get Dart plugin sources. Sync to the same version as before.

## Working with Android Studio

Android Studio cannot use the Gradle-based project definition,
so it still needs the `flutter-intellij-community.iml` file.
Obviously, unit tests can only be run from the command line.

1. Initialize Android Studio sources.
2. Checkout Flutter plugin sources, tip of tree.
3. Follow the directions for setting up the Dart plugin sources in
   `intellij-plugins/Dart/README.md` with these changes:
    - you do not need to clone the intellij-community repo
    - open studio-main/tools/adt/idea in IntelliJ
    - possibly skip running `intellij-community/getPlugins.sh`
4. Checkout Dart plugin sources.
5. Using the Project Structure editor, import
    - intellij-plugins/Dart/Dart-community.iml (if there are lots of errors, see step 7)
    - flutter-intellij/flutter-intellij-community.iml
6. Using the Project Structure editor, expand the tree to show `intellij > android > adt > ui`.
   Select the `ui` module then add a module dependency from it to `flutter-intellij-community`.
   Also add a dependency on the Dart module unless using step 7.
7. (Optional, when Dart sources are not usable.) Make sure the `flutter-intellij-community` module
   has a dependency on a library named `Dart`. It should be pre-defined, but if it is out-of-date
   then adjust it to point to `flutter-intellij/third_party/lib/dart-plugin/xxx.yyyy/Dart.jar`.
   Delete the Dart module from the Project Structure modules list.

## Working with Embedded DevTools (JxBrowser)

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

## Signing commits

We require that all commits to the repository are signed with a GPG key.
[GitHub's documentation](https://docs.github.com/en/authentication/managing-commit-signature-verification/about-commit-signature-verification)
provides instructions, but if you are on macOS, the following tips may help:

- Download GPG's tarball along with its dependencies from [here](https://www.gnupg.org/download/).
  GPG is the first item, the dependencies are in the block below (Libgpg-error, Libgcrypt, etc.).
- To install these tarballs on macOS, follow these instructions:
    - Download the desired .tar.gz or (.tar.bz2) file
    - Open Terminal
    - Extract the .tar.gz or (.tar.bz2) file with the following commands (Follow these steps for the dependencies first, then for GPG):
    ```bash
    tar xvjf PACKAGENAME.tar.bz2
    # Navigate to the extracted folder using cd command
    cd PACKAGENAME
    # Now run the following command to install the tarball
    ./configure
    make
    sudo make install
    ```
- You may need to install pinentry (`brew install pinentry`)
- If the Pinentry continues to not work, check its path (`which pinentry`) and add it to the file `~/.gnupg/gpg-agent.conf`, i.e.:
  ```bash
  pinentry-program /path/to/pinentry
  ```
- You may need to set the tty `export GPG_TTY=$(tty)` if you get this error when trying to commit:
  ```bash
  error: gpg failed to sign the data
  fatal: failed to write commit object
  ```
