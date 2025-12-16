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

## Branching to manage IDEA platform versions

### Strategy

(The below is a summary of an internal doc on our strategy for version management.)

The main branch intends to support multiple IDEA platform versions (e.g. currently, 2025.1 to 2025.3). This simplifies
development as only one plugin is typically built and released from main.

However, there may be times when we need different builds for different versions of the IDEA platform; for example, if we introduce a
feature that requires platform version 2025.2 and above, but we still want to release the plugin for version 2025.1. In this case, we plan
to use just-in-time version branching to create a "maintenance" branch for an older platform version. We may also start a maintenance branch
because we no longer find it necessary to release new plugins for an outdated platform.

### Branching procedure

This is an example of the procedure:

1. Notice that a change that breaks previous versions has been introduced.
2. Find the last commit that was included in the last plugin release that covered the old platform version (you may need to refer
   to https://plugins.jetbrains.com/plugin/9212-flutter/edit/versions/stable) along with commit history.
3. At this commit, create a branch for the old platform version(s) where the breaking change cannot be applied, e.g. `platform-251` to
   indicate 2025.1. To create a branch on an old commit on GitHub, find the commit, click the `<>` button ("browser repository at this
   point"), click on the tree icon at the top left, and then type in the new branch name.

### Releasing from a maintenance branch

In many cases, we may not ever build a release from a maintenance branch, and we will just keep it for history. If we do want to release
from a maintenance branch, do the following:

1. Cherry-pick commits to the maintenance branch (e.g. `platform-251`) that should be released for the old platform version.
2. Check out the maintenance branch and build according to the instructions above.

### Existing maintenance branches

| Old IDEA platform version (branch)                                                     | Last released Flutter Plugin version |
|----------------------------------------------------------------------------------------|--------------------------------------|
| 2024.3 ([platform-243](https://github.com/flutter/flutter-intellij/tree/platform-243)) | 88.1.0                               |
