## Contributing code

![GitHub contributors](https://img.shields.io/github/contributors/flutter/flutter-intellij.svg)

We gladly accept contributions via GitHub pull requests! If you are new to codiing IntelliJ
plugins, here are a couple links to get started:

- [INTRODUCTION TO CREATING INTELLIJ IDEA PLUGINS](https://developerlife.com/2020/11/21/idea-plugin-example-intro/)
- [ADVANCED GUIDE TO CREATING INTELLIJ IDEA PLUGINS](https://developerlife.com/2021/03/13/ij-idea-plugin-advanced/)

You must complete the
[Contributor License Agreement](https://cla.developers.google.com/clas).
You can do this online, and it only takes a minute. If you've never submitted code before,
you must add your (or your organization's) name and contact info to the [AUTHORS](AUTHORS)
file.

* Install Flutter SDK from [Flutter SDK download](https://flutter.dev/docs/get-started/install) or
  [github](https://github.com/flutter/flutter) and set it up according to its instructions.
* Verify installation from the command line:
    - Connect an android device with USB debugging.
    - `cd path/to/flutter/examples/hello_world`
    - `flutter pub get`
    - `flutter doctor`
    - `flutter run`
* Fork `https://github.com/flutter/flutter-intellij` into your own GitHub account. 
  If you already have a fork, and are now installing a development environment on a new machine,
  make sure you've updated your fork so that you don't use stale configuration options from long ago.
* `git clone https@github.com:<your_name_here>/flutter-intellij.git`
* `cd flutter-intellij`
* `git remote add origin https@github.com:flutter/flutter-intellij.git`
  (So that you fetch from the master repository, not your clone, when running git fetch et al.)
  The name `origin` can be whatever you want

## Flutter plugin development on MacOS and Linux

* Download and install the latest stable version of IntelliJ (2021.1 or later)
  - [IntelliJ Downloads](https://www.jetbrains.com/idea/download/)
  - Either the community edition (free) or Ultimate will work
  - Determine the directory of your downloaded IntelliJ Community Edition installation 
    (e.g, `IntelliJ IDEA CE.app`, `~/idea-IC-183.4886.37` or 
    `~/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/211.6693.111/IntelliJ IDEA.app`)
* Set your JAVA_HOME directory.
    - On Mac, the following works:
      Check what version of java you have:
      ```
      /usr/libexec/java_home -V
      ```
      Set your JAVA_HOME env variable to match that version. In this example, my Java version was `11.0.14.1`

      ```
      export JAVA_HOME=`/usr/libexec/java_home -v 11.0.14.1`
      ```
* Download Dart and other dependencies from the command line:
    - `cd path/to/flutter-intellij`
    - `flutter pub get`
    - `(cd tool/plugin; flutter pub get)`
    - `bin/plugin test`
    - `bin/plugin make -csetup -osetup -u` # configures the IDE used to launch the debugger
* Start IntelliJ
* In the "Project Structure" dialog (`File | Project Structure`):
  - Select "Platform Settings > SDKs" click the "+" sign at the top "Add New SDK (Alt+Insert)" to configure the JDK
    - Point it to the directory of the jbr which is under the IDEA's content (e.g. `IntelliJ IDEA CE.app/Contents/jbr`)
    - Change the name to `IntelliJ IDEA jbr 11`
  - Select "Platform Settings > SDKs" click the "+" sign at the top "Add New SDK (Alt+Insert)" to configure an IntelliJ Platform Plugin SDK
    - Point it to the directory of the content which is under the IDEA's installation 
      (e.g, `IntelliJ IDEA CE.app/Contents`)
    - Change the name to `IntelliJ IDEA Community Edition`
    - Change the "Internal Java Platform" to `IntelliJ IDEA jbr 11`
    - Extend it with additional plugin libraries by adding to `Classpath`:
      - plugins/git4idea/lib/git4idea.jar
      - plugins/android/lib/android.jar
      - plugins/yaml/lib/yaml.jar
* In the "Java Compiler" preference page, make sure that the "Project bytecode version" is set to `11` or `Same as language level`
* In the "Kotlin Compiler" preference page, make sure that the "Target JVM Version" is set to `11` or `Same as language level`
* One-time Dart plugin install - first-time a new IDE is installed and run you will need to install the Dart plugin
  - Find `Plugins` (in Settings/Preferences) and install the Dart plugin, then restart the IDE
* Open the flutter-intellij project in IntelliJ (select and open the directory of the flutter-intellij repository).
  - If you see a popup with "Gradle build scripts found", confirm loading the Gradle project and confirm that you trust it
  - Ignore suggestion for protobuf-java plugin, unless you want it
  - Build the project using `Build` | `Build Project`
* Try running the plugin; elect the `flutter-intellij [runIde]` run config then click the Debug icon. This should open the "runtime workbench", 
  a new instance of IntelliJ with the plugin installed.
  - If this causes an error message `Specified localPath '/.../flutter-intellij/artifacts/ideaIC' doesn't exist or is not a directory`, you may need to first build the plugin on the command line with `bin/plugin make`.
* If the Flutter Plugin doesn't load (Dart code or files are unknown) see above "One-time Dart plugin install"
  - Install the Dart plugin, exit
* Verify installation of the Flutter plugin:
  - Select `flutter-intellij [runIde]` in the Run Configuration drop-down list
  - Click Debug button (to the right of that drop-down)
  - In the new IntelliJ process that spawns, open the `path/to/flutter/examples/hello_world` project
  - Choose `Edit Configurations...` in the Run Configuration drop-down list
  - Expand `Edit configuration templates...` and verify that Flutter is present
  - Click [+] and verify that Flutter is present

## Provision Tool

This is not currently required. However, for debugging unit tests it may be handy; please ignore for now.

The Gradle build script currently assumes that some dependencies are present in the `artifacts` directory.
This project uses an External Tool in IntelliJ to ensure the dependencies are present. It appears that
external tools are not shareable. To make one, open the "Run Configuration" dialog and select "Flutter Plugin".
Look at the "Before launch" panel. It probably displays an Unknown External Tool. Double-click that, then click
edit icon in the new panel (pencil). Set the name to "Provision". Set the values:
- Program: /bin/bash
- Arguments: bin/plugin test -s
- Working directory: $ProjectFileDir$
  - You can select that from a list by clicking the "+" symbol in the field

There is a screenshot of this dialog in `resources/intellij/Provision.png'.
`
Save, close, and re-open the "Run Configuration" dialog. Confirm that the External Tool is named "Provision".

If you know where the Application Support files are located for your version of IntelliJ,
you can drop the definition into its `tools` directory before starting IntelliJ.
The definition is in `resources/intellij/External Tools.xml`.

## Flutter plugin development on Windows

These are notes taken while making a Windows dev env for the Flutter plugin.
It assumes familiarity with the section about set-up on MacOS.
However, this configuration includes IntelliJ source code. Before starting,
ensure that `git` is installed. If needed, download from https://gitforwindows.org/.
Also, Set the configuration property `core.longpaths` to true; see the Git for Windows
FAQ for more info about that.
```bash
Launch command-line tool: git-bash (may need to install -- don't remember)
$ mkdir intellij
$ cd intellij
$ git clone https://github.com/JetBrains/intellij-plugins.git --branch 211.7442 --depth 1
$ git clone https://github.com/JetBrains/intellij-community.git --branch 211.7442 --depth 1
$ cd intellij-community
$ git clone https://github.com/JetBrains/android.git android --branch 211.7442 --depth 1
$ cd ..
$ git clone -c core.symlinks=true git@github.com:flutter/flutter-intellij.git
```
- Copy the `org_mockito*` files from `flutter-intellij/.idea/libraries`
to `intellij-community/.idea/libraries`
- Launch IntelliJ 211.7442 or later
- Open `intellij-community` as described in its docs
- Add `Dart-community` as described in its docs
- Open Project Structure on `Dart-community`
- Add dependencies, if needed
    - modules
      - `intellij.platform.ide.util.io`
      - `intellij.platform.core.ui`
      - `intellij.platform.codeStyle.ui`
      - `intellij.yaml`
    - libraries
      - `fastutil-min`
      - `caffeine`
      - `javstringsimilarity` (the jar file is in the lib directory)

- Save and close Project Structure
- Open the Gradle tool window
- Expand the tree: `dependencies > Tasks > other`
- Scroll down and double-click: `setupBundeledMaven`
- Select the run config: IDEA
- Click the Debug button. If this is the first build, be prepared to wait a half hour.
- The IDE should launch. Dart should be listed in the list of project types when
creating a project.
- Shut it down.

- In Project Structure, import the module `flutter-intellij`
  - *Do not import it as a Gradle module.*
- Add a dependency to it to `intellij.idea.community.main` using Project Structure
- Move it above Dart-community. This sets the class path to use the Flutter plugin
version of some code duplicated from the Dart plugin.
- Create an external tool named Provision. See the section above named `Provision Tool`.
* Download Dart dependencies from the command line:
    - `cd path/to/flutter-intellij`
    - `flutter pub get`
    - `cd tool/plugin`
    - `flutter pub get`

## Running plugin tests

### Using test run configurations in IntelliJ

THIS SECTION IS OUT OF DATE SINCE THE CONVERSION TO GRADLE

The IntelliJ test framework now requires unit tests to be run in a Gradle project. It is possible to
import `flutter-intellij` as a Gradle project, but it is difficult, and not covered here. Instead,
run tests using the plugin tool, as described below.

The repository contains two pre-defined test run configurations. One is for "unit" tests; that is
currently defined as tests that do not rely on the IntelliJ UI APIs. The other is for "integration"
tests - tests that do use the IntelliJ UI APIs. The integration tests are larger, long-running tests that
exercise app use cases.

In order to be able to debug a single test class or test method you need to do the following:

* Open the test source file in the editor. Navigate to `flutter-idea/testSrc/unit/...` to open it. 
* Tests must run using Gradle, so be sure to open the source from the Gradle module.
* Find the test you want to run. Right-click the green triangle next to the test name and choose `Debug <test-name>`.

The test configuration can be tricky due to IntelliJ platform versioning. The plugin tool (below) can be a more 
reliable way to run tests.

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

If you wanted to run a subset of the tests you could do so this way. See the 
[Gradle docs](https://docs.gradle.org/current/userguide/java_testing.html) for more info about testing.
*However*, you must have run the tests once using the plugin tool, to ensure all the dependencies have
been configured.

## Adding platform sources
Sometimes browsing the source code of IntelliJ is helpful for understanding platform details that aren't documented.

  - In order to have the platform sources handy, clone the IntelliJ IDEA Community Edition repo
(`git clone https://github.com/JetBrains/intellij-community`)
  - Sync it to the same version of IntelliJ as given by `baseVersion` in gradle.properties (`git checkout 211.7628`). It will be in "detached HEAD" mode.
  - Open the Project Structure dialog (`File > Project Structure`). In the `IntelliJ IDEA Community Edition` sdk, go to 
    the `Sourcepaths` tab and add the path to `intellij-community`. Accept all the root folders found by the IDE after scanning.
  - Do the same for the intellij-plugins repo to get Dart plugin sources. Sync to the same version as before.

## Working with Android Studio

Android Studio cannot use the Gradle-based project definition, so it still needs the `flutter-intellij-community.iml`
file. Obviously, unit tests can only be run from the command line.

1. Initialize Android Studio sources.
2. Checkout Flutter plugin sources, tip of tree.
3. Follow the directions for setting up the Dart plugin sources in
   intellij-plugins/Dart/README.md with these changes:
    - you do not need to clone the intellij-community repo
    - open studio-main/tools/adt/idea in IntelliJ
    - possibly skip running intellij-community/getPlugins.sh
4. Checkout Dart plugin sources.
5. Using the Project Structure editor, import
    - intellij-plugins/Dart/Dart-community.iml (if there are lots of errors, see step 7)
    - flutter-intellij/flutter-intellij-community.iml
6. Using the Project Structure editor, expand the tree to show `intellij > android > adt > ui`. Select the `ui` 
   module then add a module dependency from it to `flutter-intellij-community`. Also add a dependency on the Dart
   module unless using step 7.
7. (Optional, when Dart sources are not usable.) Make sure the `flutter-intellij-community` module has a dependency on a library named
   `Dart`. It should be pre-defined, but if it is out-of-date then adjust it to point to `flutter-intellij/third_party/lib/dart-plugin/xxx.yyyy/Dart.jar`.
   Delete the Dart module from the Project Structure modules list.

## Working with Embedded DevTools (JxBrowser)

We use [JxBrowser](https://www.teamdev.com/jxbrowser), a commercial product, to embed DevTools within IntelliJ. A license key 
is required to use this feature in development, but it is not required for developing unrelated (most) features.

Getting a key to develop JxBrowser features:
- Internal contributors: Ask another internal contributor to give you access to the key.
- External contributors: Our license key cannot be transferred externally, but you can acquire your own trial or regular 
  license from [TeamDev](https://www.teamdev.com/jxbrowser) to use here.

To set up the license key:
1. Copy the template at resources/jxbrowser/jxbrowser.properties.template and save it as resources/jxbrowser/jxbrowser.properties.
2. Replace `<KEY>` with the actual key.

## Signing commits

We require that all commits to the repository are signed with gpg. [Github's documentation](https://docs.github.com/en/authentication/managing-commit-signature-verification/about-commit-signature-verification) provides instructions, but if you are on OSX, the following tips may help:
- Download gpg's tarball along with its dependencies from [here](https://www.gnupg.org/download/). gpg is the first item, the dependencies are in the block below (Libgpg-error, Libgcrypt, etc.).
- To install these tarballs on mac, follow these instructions:
  - Download the desired .tar.gz or (.tar.bz2) file
  - Open Terminal
  - Extract the .tar.gz or (.tar.bz2) file with the following commands (Follow these steps for the dependencies first, then for gpg):
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
- If pinentry continues to not work, check its path (`which pinentry`) and add it to the file `~/.gnupg/gpg-agent.conf`, i.e.:
  ```bash
  pinentry-program /path/to/pinentry
  ```
- You may need to set the tty `export GPG_TTY=$(tty)` if you get this error when trying to commit:
  ```bash
  error: gpg failed to sign the data
  fatal: failed to write commit object
  ```
