## Development Notes

### Simulating a fresh install

To simulate a fresh install, you need to delete any accrued application state.  To do this, you'll want to delete select folders in your plugin sandbox.
(To find your sandbox, launch the `runIde` run/debug configuration and in this newly launched IDE instance, open `Help > Show Log in Finder` (macOS) or 
`Help > Show Log in Explorer` (Windows), or `Help > Show Log in Files` (Linux). The log directory is usually inside a system folder, which itself is
inside your sandbox.)

* **config** folder contains IDE app-level configuration, including app-level libraries, SDKs, recent projects, etc.
* **system** folder contains indexes and caches, deleting it is similar to File | Invalidate Caches action.
* **plugins** folder contains plugins that you additionally manually installed to the started IDE.  (Likely don't want to delete `Dart`.)
* **test** folder will also appear as soon as you run some tests. (Deleting it when tests fail with no visible reason can sometimes help.)

(_**NOTE:**_ after removing configuration info, you may need to re-prepare the `flutter-intellij` plugin for deployment in order for it to show up in your fresh runtime workbench: `Build > Prepare Plugin Module 'flutter-intellij' For Deployment`.)
