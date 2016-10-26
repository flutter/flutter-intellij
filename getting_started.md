## Getting Started

### Set up the Flutter SDK

See [flutter.io/setup](https://flutter.io/setup/) for instructions about installing the Flutter SDK
and configuring your machine for Flutter development.

### Supported IDEs

The Flutter plugin can be installed on following IntelliJ-based products:

* IntelliJ 2016.2+ ([Ultimate or Community](https://www.jetbrains.com/idea/download/))

### Install the Dart and Flutter plugins

The Flutter plugin uses functionality from the Dart plugin. To install the Dart plugin:

1. Open plugin preferences (**Preferences>Plugins** on macOS, **File>Settings>Plugins** on Linux)
1. Select **"Browse repositories…"**
1. search for `'Dart'`; select the option to install the plugin
1. search for `'Flutter'`; install (and restart IntelliJ)

### Flutter plugin configuration

1. Open  preferences (**Preferences** on macOS, **File>Settings** on Linux)
1. Select **Languages & Frameworks>Flutter**
1. Set the path to the root of your flutter repo
1. Click OK

### Creating a project

1. Choose **File>New>Project** (or **Create new project** in the welcome dialog)
2. In the **New Project Wizard**, select Flutter as your project type (in the left column) and click **Next**
3. Give your project a name (change location if desired) and click **Finish**
4. Expand the project view, and double-click `main.dart` inside `lib` to see the main program entrypoint

### Running an app

Ensure a development device is available and running (a development enabled Android device, or the
iOS Simulator). In the IntelliJ toolbar, select the launch configuration for the created Flutter 
project and hit the debug button.

### Additional details

For tips on opening existing projects, debugging, etc. see the [user journeys page](/docs/user_journeys.md).
