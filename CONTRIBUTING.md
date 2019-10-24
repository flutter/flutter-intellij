## Contributing code

![GitHub contributors](https://img.shields.io/github/contributors/flutter/flutter-intellij.svg)

We gladly accept contributions via GitHub pull requests!

You must complete the
[Contributor License Agreement](https://cla.developers.google.com/clas).
You can do this online, and it only takes a minute. If you've never submitted code before,
you must add your (or your organization's) name and contact info to the [AUTHORS](AUTHORS)
file.

## Flutter plugin development

* Download and install the latest stable version of IntelliJ
  - [IntelliJ Downloads](https://www.jetbrains.com/idea/download/)
  - either the community edition (free) or Ultimate will work.
* Start IntelliJ
* In the Project Structure dialog (`File | Project Structure`), select "Platform Settings > SDKs" click the "+" sign at the top "Add New SDK (Alt+Insert)" to configure an IntelliJ Platform Plugin SDK
  - point it to the directory of your downloaded IntelliJ Community Edition installation (e.g, `IntelliJ IDEA CE.app/Contents` or `~/idea-IC-183.4886.37`)
  - change the name to `IntelliJ IDEA Community Edition`
  - extend it with additional plugin libraries (only until Android Q is released)
    - plugins/android/lib/android.jar
    - plugins/gradle/lib/gradle-common.jar
* In the Project Structure dialog (`File | Project Structure`), select "Libraries" 
  - click the "+" sign at the top "From maven..." to add libraries for PowerMock. Do this twice, for:
    - powermock-module-junit4
    - powermock-api-mockito2
    - com.google.protobuf:protobuf-java:3.5.1
  - Use the 2.0.0 version, or whatever is current in `flutter-intellij.iml`
* One-time Dart plugin install - first-time a new IDE is installed and run you will need to install the Dart plugin.  `Configure | Plugins` and install the Dart plugin, then restart the IDE
* Open flutter-intellij project in IntelliJ (select and open the directory of the flutter-intellij repository). Build it using `Build` | `Make Project`
* Try running the plugin; there is an existing launch config for "Flutter IntelliJ".
* If the Flutter Plugin doesn't load (Dart code or files are unknown) see above "One-time Dart plugin install"
* Install Flutter SDK from [Flutter SDK download](https://flutter.io/docs/get-started/install) or [github](https://github.com/flutter/flutter) and set it up according to its instructions.
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
currently defined as tests that do not rely on the IntelliJ APIs. The other is for 'integration'
tests - tests that do use the IntelliJ APIs. In the future we would like for the unit tests to be
able to access IntelliJ APIs, and for the integration tests to be larger, long-running tests that
excercise app use cases.

In order to be able to run a single test class or test method you need to do the following:

* Open Run | Edit Configurations, select 'Flutter tests' run configuration, copy its VM Options
  to clipboard
* In the same dialog (Run/Debug Configurations) expand Defaults node, find JUnit under it and paste
  VM Options to the corresponding field
* Repeat the same with Working directory field - it must point to intellij-community/bin

## Adding platform sources

  - In order to have the platform sources handy, clone the IntelliJ IDEA Community Edition repo
(`git clone https://github.com/JetBrains/intellij-community`)
  - Sync it to the same version of IDEA that you are using (`git checkout idea/171.3780.107`). It will be in 'detached HEAD' mode.
  - Open the Project Structure dialog (`File > Project Structure`). In the `IntelliJ IDEA Community Edition` sdk, go to the `Sourcepaths` tab and add the path to `intellij-community`. Accept all the root folders found by the IDE after scanning.
  - Do the same for the intellij-plugins repo to get Dart plugin sources. Sync to the same version as in lib/dart-plugin. (`git checkout webstorm/171.4006`)

## Working with Android Studio

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
