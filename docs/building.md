## The plugin tool

Building is done by the `plugin` tool.
See [tool/plugin/README.md](../tool/plugin/README.md) for details.

## Releasing the plugin

Update the changelog, then generate plugin.xml changes using `bin/plugin generate`. Commit and submit these changes.

For major releases:
- Name the branch `release_<release number>` and push to github
- Run `bin/plugin make -r<release number>` to build the plugin for all supported versions

For minor releases:
- Fetch and checkout the branch `release_<release number>` from github for the latest release (e.g. pull `release_64` if about to release 64.1)
- Run `bin/plugin make -r<release number>.<minor number>` (e.g. `-r64.1`) to build the plugin for all supported versions
- Push the updated branch `release_<release number>` to GitHub. The release branch will be on:
  - Releases 1 to 70 can be found at https://github.com/flutter/flutter-intellij/branches/all?query=release
  - Releases 71 to 75 can be found at https://github.com/stevemessick/flutter-intellij/branches/all?query=release
  - Releases from 76 can be found at https://github.com/jwren/flutter-intellij/branches/all?query=release

Once plugin files are generated, upload release files to Drive for manual testing.

When ready to release to the public, go to the [Flutter plugin site](https://plugins.jetbrains.com/plugin/9212-flutter). You will need to be invited to the organization to upload plugin files.

## The build pre-reqs

This section is obsolete.

Several large files required for building the plugin are downloaded from Google Storage
and cached in the `artifacts/` directory. We use timestamps to know if the local cached
copies are up-to-date.

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
