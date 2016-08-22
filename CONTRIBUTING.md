## Contributing code

We gladly accept contributions via GitHub pull requests!

You must complete the
[Contributor License Agreement](https://cla.developers.google.com/clas).
You can do this online, and it only takes a minute. If you've never submitted code before,
you must add your (or your organization's) name and contact info to the [AUTHORS](AUTHORS)
file.

## Flutter plugin development

First, set up Dart plugin development as described in the
[ReadMe.txt](https://github.com/JetBrains/intellij-plugins/blob/master/Dart/ReadMe.txt)
file in the Dart plugin.

* Open intellij-community project in IntelliJ, compile it.
  - Open File | Project Structure | Modules | [+] | Import Module, select
    flutter-intellij/flutter-intellij-community.iml (from same dir as this README).
  - In the same Project Structure dialog open the Dependencies tab of the community-main module,
    click [+] at the bottom (Mac) or right (Win/Linux) to add a module dependency on the
    flutter-intellij-community module.
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
