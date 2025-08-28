## Building the plugin for release

Update the `gradle.properties` file

- IntelliJ IDEA versions can be found here: https://www.jetbrains.com/idea/download/other.html
- Dart Plugin versions can be found here: https://plugins.jetbrains.com/plugin/6351-dart
- Android Studio versions can be found
  here: https://developer.android.com/studio/archive & https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html
    - To view the current sources from Android Studio, use https://cs.android.com/android-studio

Edit `CHANGELOG.md` to include the new version number and associated release notes.

Commit these changes.

Verify that the file `./resources/jxbrowser/jxbrowser.properties` has been copied from `./resources/jxbrowser/jxbrowser.properties.template`
with the `<KEY>` replaced with a valid JXBrowser key to be used in the built Flutter plugin.

Run gradle:

- Check that `$JAVA_HOME` is set (see CONTRIBUTING.md for instructions if not set)
- Run `./gradlew buildPlugin -Prelease` to build the plugin for the settings specified in `gradle.properties`
- The output .zip file will be in `<flutter-intellij root>/build/distributions`

### Test and upload to the JetBrains Servers

Once plugin files are generated, upload release files to Drive for manual testing.

When ready to release to the public, go to the [Flutter plugin site](https://plugins.jetbrains.com/plugin/9212-flutter). You will need to be
invited to the organization to upload plugin files.

## The plugin tool

Building for releases was formerly done by the `plugin` tool when we wanted to run a script to generate multiple release versions at once.
See [tool/plugin/README.md](../tool/plugin/README.md) for details.

The `plugin` tool is being retained currently for deploying the dev build, linting, and generating live templates.

### install gs_util

It is no longer necessary to use gs_util. All artifact management is automated.
This section is being retained in case someone wants to clean up the cloud storage.

If necessary, install the gs_util command-line utility from
[here](https://cloud.google.com/storage/docs/gsutil_install).

### to list existing artifacts

To see a list of all current IntelliJ build pre-reqs:

```shell
$ gsutil ls gs://flutter_infra_release/flutter/intellij/
```

### uploading new artifacts

Do not upload new artifacts.

In order to update or add a new pre-req:

```shell
$ gsutil cp <path-to-archive> gs://flutter_infra_release/flutter/intellij/
```
