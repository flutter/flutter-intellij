# Plugin Smoke Testing

Manual tests to execute before plugin releases.

## Setup

Pre-reqs: Run through the [flutter setup](https://flutter.io/docs/get-started/install) and
[flutter getting started](https://flutter.io/docs/development/tools/ide) guides.

* Run `flutter upgrade` in a terminal to get the latest version prior to starting testing.

## Testing matrix

* Testers should generally test against IntelliJ CE and Android Studio.
* We also support internal IntelliJ; we should have testers assigned to each (IntelliJ CE,
  internal IntelliJ, and Android Studio) for every roll.
* We support Windows, Mac, and Linux. We need to ensure the test script is run on all
  platforms for both IntelliJ and Android Studio.

## Project Creation

Validate basic project creation. Find your other.xml and remove FLUTTER_SDK_KNOWN_PATHS and remove `flutter` from your PATH.
This might require a reboot. Start from the Welcome screen at least once.

* Create a **simple project** (`File > New > Project...`, pick `Flutter`; on Android Studio, `File > New > New Flutter Project...`).
  * (select an `Application` type project)
* Confirm that:
  * Project contents are created.
    * Verify that a run configuration (`main.dart`) is enabled in the run/debug selector.
  * Navigation works.
    * Open `lib/main.dart` and navigate to `AppBar`, from line 69 or so.
    * Verify that the new editor includes a 'View hosted code sample' banner.
  * There are no analysis errors or warnings.
  * Pub operations work.
    * Open `pubspec.yaml` and click the "Packages get" and "Packages upgrade" links.
  * Flutter operations work.
    * From the `Tools` menu, select `Flutter` > `Flutter Doctor`.
  * Code completion works.
    * Change `primarySwatch: Colors` to some other color and validate that you
      get completions.
  * `File > Project Structure` works.

* Create a **plugin project** (`File > New > Project...`, pick `Flutter`; on Android Studio, `File > New > New Flutter Project...`), specify "Plugin" as the project type and select "Swift" for the iOS language.
* Confirm that:
  * Project contents are created.
    * Verify that `<project root>/ios/Classes/Swift<Project Name>Plugin.swift` exists.
    * Verify that a run configuration (`main.dart`) is enabled in the run/debug selector.
  * `Open Android module in Android Studio` does the right thing
    * Navigate to and select `<project root>/android/src/main`
    * Select `Flutter > Open Android module in Android Studio` from the project list menu
    * Verify that the new project window allows editing of `<project root>/example/android/app`
    
* Create a **module project** (`File > New > Project...`, pick `Flutter`; on Android Studio, `File > New > New Flutter Project...`), specify "Module" as the project type.
* Confirm that:
  * Project contents are created.
    * Verify that a run configuration (`main.dart`) is enabled in the run/debug selector.
    * Verify that directories `.ios` and `.android` exist.
* Convert to editable native code (`Tools > Flutter > Make host app editable`)
* Confirm that:
  * Project contents are created.
    * Verify that directories `ios` and `android` exist, in addition to `.ios` and `.android`.
* Run the app and verify that it starts correctly.
* Stop the app.
* Navigate to and select `<project root>/android`
* Select `Flutter > Open Android module in Android Studio` from the project list menu
  * Opening in a new window is recommended. If necessary change your preference/setting to allow that.
* Verify that Gradle sync completes normally
* Verify that the new project window allows editing of `app/java` (using the Android view of the project)
  * The file icon should be blue to indicate it is a source folder.

## Project Open

Validate that our example projects can be opened.

* close any open projects in IDEA
* from the Welcome screen choose `Open` (IntelliJ) or `Open an existing Android Studio project` (AS)
* browse to and select `<flutter-root>/examples/flutter_gallery`
* ensure there are no analysis errors or warnings
* test that code completion is working as expected
* ensure that the `main.dart` launch configuration shows up and is selected

Validate that a `flutter create` project can opened.

* close any open projects in IDEA
* run `flutter create` in a terminal to create a new project `testcreate`
* choose `File > Open...` and browse to and select the 'testcreate' project
  * or, type `idea .` or `studio .` from the CLI if you have the CLI launcher installed
* ensure there are no analysis errors or warnings
* test that code completion is working as expected
* ensure that the `main.dart` launch configuration shows up and is selected

## Device Detection

Validate device selection.

### Any OS

* Physical Android device
  * Plug in a physical Android device
  * Ensure that a menu item appears in the device pull down for the device.
  
* Emulated Android device
  * Start an Android emulator
  * Ensure that a menu item appears in the device pull down for the device.

### macOS only

* Verify that the simulator can be opened.
  * Quit any open iOS simulator.
  * From the device pull-down, select "Open iOS Simulator" and verify that the simulator opens.

## Run / Debug

Validate basic application running and debugging.

In the newly created app:
* plug in an Android device (or open the iOS Simulator)
* set a breakpoint on the `_counter++` line
* hit the `'debug'` icon to start the app running
* verify the app appears on the device
* tap the `'+'` icon on the app
* verify that the IDE pauses at the breakpoint, and that the `Variables` pane has
  the right value for `_counter`
* open the inspector
* change the display by clicking `Render Tree` and the refresh button
* open the performance view
* (in Android Studio): verify that the performance view is active and the memory view is not
* hit resume in the debugger

## Hot Reload

Validate basic hot reload functionality.

Assuming the app state from above (i.e., leave the Debug session running):
* modify the text for the `'You have pushed the button this many times:'` line
* change the `_counter++` line to `_counter--`
* hit the hot reload button in the debugger UI
* validate that
  1. the state persisted (the same number of clicks in the UI), and
  2. the text changed to end in an exclamation point
  3. the + button decreases the value

Keybindings:
* verify that the hot reload keybinding works (on a mac: `cmd-\ `)
* verify that the reload-on-save feature works (hitting cmd-s / ctrl-s triggers a reload)

## Hot Restart

* change the text and counter line back
* hit the `Flutter Hot Restart` button (or hit the Debug button again)
* validate that the text and state resets, and the count increases

## Debugging Sessions

Validate that a sequence of sessions works as expected.

After testing the above, terminate your debugging session and start another.
* validate that a breakpoint is hit
* verify that the reload keybinding works as expected

## Project Open Verification

Verify that projects without Flutter project metadata open properly and are given the Flutter module type.

* create a new Flutter project and delete IntelliJ metadata:
  * `flutter create foo_bar`
  * `cd foo_bar`
  * `rm -rf .idea` (Windows: `rd /s /q .idea`)
  * `rm foo_bar.iml` (Windows: `del foo_bar.iml`)
* open project ("File > Open")
* verify that the project has the Flutter module type (the device pull-down displays, the Flutter Outline window is present, etc.) and analyzes cleanly

## Fresh Install Configuration

Verify installation and configuration in a fresh IDEA installation.

* Follow the instructions to
  [simulate a fresh installation](https://github.com/flutter/flutter-intellij/wiki/Development#simulating-a-fresh-install).
* (If not running in a "runtime workbench", [install the plugins](https://flutter.io/docs/development/packages-and-plugins/using-packages).)
* Open "Languages & Frameworks>Flutter" in Preferences and verify that there is
  no Flutter SDK set.
* Set the Flutter SDK path to a valid SDK location.
  * Verify that invalid locations are rejected (and cannot be applied).
* Verify project creation, run/debug.

## Add-to-app module integration test
Create and debug a Flutter module in an Android app. Do this twice, once 
using Java and again with Kotlin as the language choice for the Android app.
The location of the Flutter module is important. Put it in the Android app
directory once and put it outside the app another time.

###Create an Android app

Start Android Studio and use the File > New > New Project menu to fire 
up the new project wizard. Choose the template that includes a Basic
Activity. One the next page, use Kotlin and minimum API level 16.
Let the system stabilize (Gradle sync).

Close the editor for content_main.xml. Switch the Project view to
the Project Files mode. Collapse the Gradle window if it is open.

### Create a Flutter module

Use the File > New > New Module menu to start the new module wizard.
Choose the Flutter Module template. Fill out the wizard pages and
click Finish, then wait a bit. Android Studio needs to do two
successive Gradle sync's. The first one generates an error message,
which is corrected by the second one. Ignore the error. Let the
system stabilize again.

### Link the Flutter module to the Android app

Go to the editor for MainActivity.kt. Change the onCreate method:
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        val engine = FlutterEngine(this.applicationContext)
        engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        fab.setOnClickListener { view ->
            FlutterEngineCache.getInstance().put("1", engine)
            startActivity(FlutterActivity.withCachedEngine("1").build(this))
        }
    }
```
If you opted to use Java, use this:
```java
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FlutterEngine engine = new FlutterEngine(getApplicationContext());
        engine.getDartExecutor().executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault());
        findViewById(R.id.fab).setOnClickListener(view -> {
            FlutterEngineCache.getInstance().put("1", engine);
            startActivity(FlutterActivity.withCachedEngine("1").build(this));
        });
    }
```
You need to add some imports. Click on each red name then type
`alt-return`. Accept the suggestion. "FlutterActivity" has two choices;
use the one from io.flutter.embedGradle.

Open an editor on the AndroidManifest.xml. Add this line to the
<application> tag:
```xml
        <activity android:name="io.flutter.embedding.android.FlutterActivity" />
```
  
At this point, click the "hammer" icon to build the application.

### Debug the app

The run config should show the Flutter run config, which makes the Flutter Attach
button active. Click the Flutter Attach button, or use the menu item Run > Flutter
Attach. (Note: with the kotlin code above you need to start the attach process
before starting the Android app.)

Change the run config to "app" for the Android app. Click the Debug button.
Wait for the app to launch.

When the app is visible, click the mail icon at bottom right. The Flutter
screen should become visible. At this point you can set breakpoints and
do hot reload as for a stand-alone Flutter app.
