# Editing Android Native Code

This is a replacement for:
https://flutter.dev/using-ide/#edit-android-code

***

## Getting Started

We recommend using the latest stable version of
Android Studio to edit the native Android
code in your Flutter app. The Flutter project contains a directory
named `android`. This is the root project of the Android code.
If your Flutter app is named `flutter_app` then you would open
Android Studio on the project located at `flutter_app/android`.

You may also use Android Studio to edit your Flutter code.
To do so you must first install the Dart and Flutter plugins
into Android Studio. Then you would open the `flutter_app` in
Android Studio.

It is fine to have both projects open at the same time. The
IDE gives you the option to use separate
windows or to replace the existing window with the new project
when opening a second project. We recommend opening multiple
projects in separate windows in Android Studio.

Why do you need to open Android Studio twice? There are some
technical issues that require the Android project to be opened
as a root project. Doing so provides the best editing
experience for Android code. In the future we hope to allow
Android code to be edited normally by opening the Flutter
project but this requires some changes to Android Studio to
implement.

If you have not run your Flutter app you may see Android Studio
report a build error when you open the `android` project.
Use Build>Make Project to fix it.

## Configuration

Open Android Studio on `flutter_app/android` then check
that a few settings are correct.
1. Open the Project Structure editor using File>Project Structure.
2. Select `SDK Location` in the left-hand list and ensure
that the location of the
Android SDK is correct. If you do not have an Android SDK
installed you can use the SDK Manager in Android Studio
to download one.
3. Select `app` in the left-hand list. In the Properties tab
make sure the Compile Sdk Version matches
the one used by Flutter (as reported by `flutter doctor`).
4. Save your changes and close the editor by clicking `OK`.

## Dependencies

By default Flutter does not add any dependencies to the
Android code. Normally, an Android project includes a
dependency on the appcompat-v7 library. That dependency
enables a lot of nice features, like code completion for
commonly used classes. If you are going to be using those
classes you'll need to add that dependency. You can add it
using the Project Structure editor.
1. Open the Project Structure editor using File>Project Structure.
2. Select `app` in the left-hand list and open the
Dependencies tab.
3. Click the `+` near the bottom of the editor and choose
`Library dependency`.
4. Select the libraries you need and click `OK`.

Caution: adding dependencies also adds code to your app.
This increases the size of the installed binary on user's
devices. The appcompat-v7 library adds almost 7 megabytes.

## Plugins

Editing the native Android code of a Flutter plugin is a bit
more tricky. Let's say we have created a new plugin called `fpl`.
It might have been created using an IDE or perhaps it was made
at the command line:

    flutter create --template=plugin fpl

To edit the native Android code you need to follow these steps
the first time, to get dependencies and paths configured.
1. Open `fpl` as the top-level project in Android Studio or IntelliJ.
2. Use the device picker to start the Android emulator.
3. Run the default run configuration, which launches the `example`
   app on the emulated device. This causes Flutter to build all
   the dependent binaries and get the paths set up correctly,
   the Flutter equivalent to doing a Gradle build.

    Alternatively, if you have an Android device connected (or
    an emulator running) these first three steps could be done
    from the command line.

        cd fpl/example
        flutter run

4. Once the app appears on the emulated device you can terminate
the app and, if desired, close that instance of Android Studio.
5. Open Android Studio on the native code in the example app,
`fpl/example/android`.
6. The Project tool window defaults to the Android view. The list
on the left-hand side of the Android Studio window should have
three items:
    * **app**
    * **fpl**
    * Gradle Scripts
7. The native Android code for your plugin is in **fpl** (or whatever
name you chose for your project). The example app native code is
in **app** and you probably won't need to edit that. You can
ignore Gradle Scripts unless you're doing some advanced
Android programming.
8. Navigate to `fpl/java` to find the Java package for your
new plugin.

Your plugin will probably need to import some dependencies.
You can add dependencies as described in the `Dependencies`
section. Just be sure to select your plugin module (**fpl**
in this example) in the module list.
