## The build pre-reqs

Several large files required for building the plugin are downloaded from Google Storage
and cached in the `artifacts/` directory. We use timestamps to know if the local cached
copies are up-to-date.

To see a list of all current IntelliJ build pre-reqs:

```shell
$ gsutil ls gs://flutter_infra/flutter/intellij/
```

In order to update or add a new pre-req:

```shell
$ gsutil cp <path-to-archive> gs://flutter_infra/flutter/intellij/
```
## The plugin tool

Building is done by the `plugin` tool.
See tool/plugin/README.md for details.


# Android Studio Canary 14 Notes

Using IJ Ultimate to build the sources current a/o May 14 produced a bunch of compile errors.
Dropping back to sources for Canary 14 and compiling with IJ CE worked. There are a few compile errors
related to JavaScriptDebugger and ChromeDebugger. I had to define IDEA_ULTIMATE_PLUGINS to point to the
plugins directory of an Ultimate distro to compile well enough to launch.

Hot reload is broken, possibly because of compile errors.

There is one incompatible API change. Icon -> Image for icons in the wizard. This will cause problems
for building both Canary and Stable plugins for Android Studio.

It appears many module names were automatically changed in the .iml file. There are two .iml files
but only one has been changed so far.
