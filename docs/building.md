## The plugin tool

Building is done by the `plugin` tool. See [tool/plugin/README.md](../tool/plugin/README.md) for details.

## Releasing the plugin

Update the [product-matrix.json](../product-matrix.json):
- IntelliJ IDEA versions can be found here: https://www.jetbrains.com/idea/download/other.html
  - Version numbers for the `product-matrix.json` should be taken from the name of the downloaded file, not versions listed on the website.
- Dart Plugin versions can be found here: https://plugins.jetbrains.com/plugin/6351-dart
- Android Studio versions can be found here: https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html
  - To view the current sources from Android Studio, use https://cs.android.com/android-studio
- To get versions of the Android plugin for IntelliJ, versions can be pulled from https://plugins.jetbrains.com/plugin/22989-android

Update the changelog, then generate `plugin.xml` changes using `./bin/plugin generate`. Commit and submit these changes.

Verify that the file `./resources/jxbrowser/jxbrowser.properties` has been copied from `./resources/jxbrowser/jxbrowser.properties.template` with the `<KEY>` replaced with a valid JXBrowser key to be used in the built Flutter plugin.

For major releases:
- Name the branch `release_<release number>` and push to GitHub
- Run `./bin/plugin make -r<release number>` to build the plugin for all supported versions

For minor releases:
- Fetch and checkout the branch `release_<release number>` from GitHub for the latest release (e.g. pull `release_64` if about to release 64.1)
- Run `./bin/plugin make -r<release number>.<minor number>` (e.g. `-r64.1`) to build the plugin for all supported versions
- Push the updated branch `release_<release number>` to GitHub. The release branch will be on:
  - Releases 1 to 70 can be found at https://github.com/flutter/flutter-intellij/branches/all?query=release
  - Releases 71 to 75 can be found at https://github.com/stevemessick/flutter-intellij/branches/all?query=release
  - Releases from 76 can be found at https://github.com/jwren/flutter-intellij/branches/all?query=release

### Some additional notes to build and test the Flutter Plugin

To test that building works, the `-r` flag is not needed, just run `./bin/plugin make`.

To build a specific version of the Flutter Plugin append `--only-version=`, i.e. `./bin/plugin make -r77 --only-version=2023.3`

### Test and upload to the JetBrains Servers

Once plugin files are generated, upload release files to Drive for manual testing.

When ready to release to the public, go to the [Flutter plugin site](https://plugins.jetbrains.com/plugin/9212-flutter). You will need to be invited to the organization to upload plugin files.
