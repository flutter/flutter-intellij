## Getting Started

### Set up the Flutter SDK

See [flutter.io/setup](https://flutter.io/setup/) for instructions about installing the Flutter SDK
and configuring your machine for Flutter development.

### Supported IDEs

The Flutter plugin can be installed on following IntelliJ-based products:

* IntelliJ 2016.2+ ([Ultimate or Community](https://www.jetbrains.com/idea/download/))

### Install Dart

The Flutter plugin uses functionality from the Dart plugin. To install the Dart plugin:

1. Open plugin preferences (**Preferences>Plugins** on macOS, **File>Settings>Plugins** on Linux)
1. Select **"Browse repositories…"**
1. search for `'Dart'`; install and restart IntelliJ

### Flutter plugin installation

During `alpha` development, the Flutter plugin is hosted in its own plugin repository. Before it
can be installed, the repository needs to be added to the IDE.

1. Open plugin preferences (**Preferences>Plugins** on macOS, **File>Settings>Plugins** on Linux)
1. Select **"Browse repositories…"**
1. In the **Browse Repositories** dialog, click **"Manage repositories…"**
1. In the resulting **Custom Plugin Repositories** pop-up, click the **+** button and paste
   `https://flutter.github.io/flutter-intellij/alpha/updatePlugins.xml` into the **Repository URL:**
   field and click **OK**
1. In the **Browse Repositories** dialog, select **`flutter-intellij`** from the list of plugins (be
   sure it is not being filtered out of your results view by specifying **Category: All** or limiting
   it to the Flutter repository in the pull-down by the search box) and click the green **Install**
   button
1. Once downloaded, click the **Restart IntelliJ IDEA** button

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
