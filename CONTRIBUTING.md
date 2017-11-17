## Contributing code

We gladly accept contributions via GitHub pull requests!

You must complete the
[Contributor License Agreement](https://cla.developers.google.com/clas).
You can do this online, and it only takes a minute. If you've never submitted code before,
you must add your (or your organization's) name and contact info to the [AUTHORS](AUTHORS)
file.

## Flutter plugin development

* Download and install the latest stable version of IntelliJ
  - https://www.jetbrains.com/idea/download/
  - either the community edition (free) or Ultimate will work. We are currently using 2017.2.
* Start IntelliJ
* In the project structure dialog, configure an IntelliJ platform SDK
  - point it to the `Contents` directory in your just downloaded copy of IntelliJ Community Edition (e.g, `IntelliJ IDEA CE.app/Contents`)
  - name it `IntelliJ IDEA Community Edition`
  - In order to have the platform sources handy, clone the IntelliJ IDEA Community Edition repo
(`git clone https://github.com/JetBrains/intellij-community`)
  - Sync it to the same version of IDEA that you are using (`git checkout idea/171.3780.107`). It will be in 'detached HEAD' mode.
  - In the `IntelliJ IDEA Community Edition` sdk, go to the `Sourcepaths` tab and add the path to `intellij-community`. Accept all the root folders found by the IDE after scanning.
  - Do the same for the intellij-plugins repo to get Dart plugin sources. Sync to the same version as in lib/dart-plugin. (`git checkout webstorm/171.4006`)
* Open flutter-intellij project in IntelliJ. Build it using `Build` > `Make Project`
* Try running the plugin; there is an existing launch config for "Flutter IntelliJ".
* If the Flutter Plugin doesn't load, check to see if the Dart Plugin is installed in your runtime workbench; if it's not, install it (`Preferences > Plugins`) and re-launch.
* Install Flutter from [github](https://github.com/flutter/flutter) and set it up according
  to its instructions.
* Verify installation from the command line:
  - Connect an android device with USB debugging.
  - `cd <flutter>/examples/hello_world`
  - `flutter doctor`
  - `flutter run`
* Verify installation of the Flutter plugin:
  - Select IDEA in the Run Configuration drop-down list.
  - Click Debug button (to the right of that drop-down).
  - In the new IntelliJ process that spawns, open the hello_world example.
  - Choose Edit Configuration in the Run Configuration drop-down list.
  - Expand Defaults and verify that Flutter is present.
  - Click [+] and verify that Flutter is present.

## Running plugin tests

The repository contains two pre-defined test run configurations. One is for 'unit' tests; that is
currently definied as tests that do not rely on the IntelliJ APIs. The other is for 'integration'
tests - tests that do use the IntelliJ APIs. In the future we would like for the unit tests to be
able to access IntelliJ APIs, and for the integration tests to be larger, long-running tests that
excercise app use cases.

In order to be able to run a single test class or test method you need to do the following:

* Open Run | Edit Configurations, select 'Flutter tests' run configuration, copy its VM Options
  to clipboard
* In the same dialog (Run/Debug Configurations) expand Defaults node, find JUnit under it and paste
  VM Options to the corresponding field
* Repeat the same with Working directory field - it must point to intellij-community/bin

## Working with Andriod Studio

1. Initialize Android Studio sources.
2. Checkout Flutter plugin sources, tip of tree.
3. Follow the directions for setting up the Dart plugin sources in
   intellij-plugins/Dart/README.md with these changes:
    - you do not need to clone the intellij-community repo
    - open studio-master-dev/tools/idea in IntelliJ
    - possibly skip running intellij-community/getPlugins.sh
4. Checkout Dart plugin sources, branch 171.
5. Using the Project Structure editor, import
    - intellij-plugins/Dart/Dart-community.iml
    - flutter-intellij/flutter-intellij-community.iml
6. Select the `community-main` module and add a module dependency to
   `flutter-intellij-community`.
