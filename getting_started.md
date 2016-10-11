## Getting Started

### Supported IDEs

The Flutter plugin can be installed on following IntelliJ-based products:

* IntelliJ 2016.2+ ([Ultimate or Community](https://www.jetbrains.com/idea/download/))

### Install Dart

The Flutter plugin uses functionality from the Dart plugin. To install the Dart plugin:

1. Open plugin preferences (**Preferences>Plugins**)
1. Select **"Browse repositories…"**
1. search for `'Dart'`; install and restart IntelliJ

### Flutter plugin installation

During `alpha` development, the Flutter plugin is hosted in its own plugin repository. Before it
can be installed, the repository needs to be added to the IDE.

1. Open plugin preferences (**Preferences>Plugins**)
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

### Set up the Flutter SDK

See [flutter.io/setup](https://flutter.io/setup/) for instructions about installing the Flutter SDK
and configuring your machine for development.

### Creating a project

Choose `File` > `New` > `Project...` and select a new Flutter project.

### Running an app

Ensure a development device is available and running (a development enabled Android device, or the
iOS Simulator). In the IntelliJ toolbar, select the launch configuration for the created Flutter 
project and hit the debug button.
