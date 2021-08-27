## Contributing code

![GitHub contributors](https://img.shields.io/github/contributors/flutter/flutter-intellij.svg)

We gladly accept contributions via GitHub pull requests!

You must complete the
[Contributor License Agreement](https://cla.developers.google.com/clas).
You can do this online, and it only takes a minute. If you've never submitted code before,
you must add your (or your organization's) name and contact info to the [AUTHORS](AUTHORS)
file.

## Flutter plugin development on MacOS and Linux

* Download and install the latest stable version of IntelliJ (2021.1 or later)
  - [IntelliJ Downloads](https://www.jetbrains.com/idea/download/)
  - Either the community edition (free) or Ultimate will work
  - Determine the directory of your downloaded IntelliJ Community Edition installation 
    (e.g, `IntelliJ IDEA CE.app`, `~/idea-IC-183.4886.37` or 
    `~/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/211.6693.111/IntelliJ IDEA.app`)
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
* One-time Dart plugin install - first-time a new IDE is installed and run you will need to install the Dart plugin. 
  Find `Plugins` (in Settings/Preferences) and install the Dart plugin, then restart the IDE
* Open flutter-intellij project in IntelliJ (select and open the directory of the flutter-intellij repository).
  Note that as of version 60 the project must be opened as a Gradle project. Wait for Gradle sync to complete.
  - Create an external tool named Provision. See the section below named `Provision Tool`.
  - Build the project using `Build` | `Build Project`
* Run the tests from the command line: `bin/plugin test`.
* Try running the plugin; there is an existing launch config for "Flutter IntelliJ". This should open the "runtime workbench", 
  a new instance of IntelliJ with the plugin installed.
* If the Flutter Plugin doesn't load (Dart code or files are unknown) see above "One-time Dart plugin install"
* Install Flutter SDK from [Flutter SDK download](https://flutter.dev/docs/get-started/install) or 
  [github](https://github.com/flutter/flutter) and set it up according to its instructions.
* Verify installation from the command line:
  - Connect an android device with USB debugging.
  - `cd <flutter>/examples/hello_world`
  - `flutter doctor`
  - `flutter run`
* Verify installation of the Flutter plugin:
  - Select `Flutter Plugin` in the Run Configuration drop-down list.
  - Click Debug button (to the right of that drop-down).
  - In the new IntelliJ process that spawns, open the hello_world example.
  - Choose Edit Configuration in the Run Configuration drop-down list.
  - Expand Defaults and verify that Flutter is present.
  - Click [+] and verify that Flutter is present.
* Note that as of version 60 the old `src` and `testSrc` trees in the root directory are no longer usable.
  Edit code using the files in the `flutter-idea` and `flutter-studio` modules instead.
  This change had to be made to allow debugging of tests in the IDE.

## Provision Tool

The Gradle build script currently assumes that some dependencies are present in the `artifacts` directory.
This project uses an External Tool in IntelliJ to ensure the dependencies are present. It appears that
external tools are not shareable. To make one, open the "Run Configuration" dialog and select "Flutter Plugin".
Look at the "Before launch" panel. It probably displays an Unknown External Tool. Double-click that, then click
edit icon in the new panel (pencil). Set the name to "Provision". Set the values:
- Program: /bin/bash
- Arguments: /bin/plugin test -s
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
  - *It must be imported as a Gradle module.*
- Add a dependency to it to `intellij.idea.community.main` using Project Structure
- Move it above Dart-community. This sets the class path to use the Flutter plugin
version of some code duplicated from the Dart plugin.
- Create an external tool named Provision. See the section above named `Provision Tool`.

## Running plugin tests

### Using test run configurations in IntelliJ

The repository contains two pre-defined test run configurations. One is for "unit" tests; that is
currently defined as tests that do not rely on the IntelliJ UI APIs. The other is for "integration"
tests - tests that do use the IntelliJ UI APIs. The integration tests are larger, long-running tests that
exercise app use cases.

In order to be able to debug a single test class or test method you need to do the following:

* Open the test source file in the editor. Navigate to `flutter-idea/testSrc/unit/...` to open it. Do *not*
  use `testSrc` in the root directory. Tests must run using Gradle, so be sure to open the source from the Gradle module.
  The reason `testSrc` exists in the root is historic; we lost the ability to run tests outside of Gradle.
  
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

## Adding platform sources
Sometimes browsing the source code of IntelliJ is helpful for understanding platform details that aren't documented.

  - In order to have the platform sources handy, clone the IntelliJ IDEA Community Edition repo
(`git clone https://github.com/JetBrains/intellij-community`)
  - Sync it to the same version of IDEA that you are using (`git checkout idea/171.3780.107`). It will be in "detached HEAD" mode.
  - Open the Project Structure dialog (`File > Project Structure`). In the `IntelliJ IDEA Community Edition` sdk, go to 
    the `Sourcepaths` tab and add the path to `intellij-community`. Accept all the root folders found by the IDE after scanning.
  - Do the same for the intellij-plugins repo to get Dart plugin sources. Sync to the same version as in lib/dart-plugin. 
    (`git checkout webstorm/171.4006`)

## Working with Android Studio

1. Initialize Android Studio sources.
2. Checkout Flutter plugin sources, tip of tree.
3. Follow the directions for setting up the Dart plugin sources in
   intellij-plugins/Dart/README.md with these changes:
    - you do not need to clone the intellij-community repo
    - open studio-master-dev/tools/idea in IntelliJ
    - possibly skip running intellij-community/getPlugins.sh
4. Checkout Dart plugin sources.
5. Using the Project Structure editor, import
    - intellij-plugins/Dart/Dart-community.iml
    - flutter-intellij -- Select the directory and choose `Import module from external model` and select `Gradle`
6. In the Gradle tool window, click the refresh icon (top left) to ensure all Gradle modules are loaded properly
7. Using the Project Structure editor, select the `studio` module and add a module dependency to all modules that begin with `flutter`

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

