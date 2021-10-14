## The build pre-reqs

Several large files required for building the plugin are downloaded from Google Storage
and cached in the `artifacts/` directory. We use timestamps to know if the local cached
copies are up-to-date.

### install gs_util

If necessary, install the gs_util command-line utility from
[here](https://cloud.google.com/storage/docs/gsutil_install).

### to list existing artifacts

To see a list of all current IntelliJ build pre-reqs:

```shell
$ gsutil ls gs://flutter_infra/flutter/intellij/
```

### uploading new artifacts

In order to update or add a new pre-req:

```shell
$ gsutil cp <path-to-archive> gs://flutter_infra/flutter/intellij/
```
## The plugin tool

Building is done by the `plugin` tool.
See [tool/plugin/README.md](../tool/plugin/README.md) for details.

## Gradle vs IntelliJ projects

To debug unit tests the project needs to be opened as a Gradle project. Close the
project in IntelliJ, then delete .idea/modules.xml. You may need to delete other
files in ~/Library/Application Support/Caches/JetBrains/<IDE>/. Look in `conversion`
and `external_build_systrm`. It may take some experimentation. When the project
is opened in IntelliJ, ensure that there are `flutter-idea` and `flutter-studio`
modules in the project structure.

To re-open the project as an IntelliJ (not Gradle) project, close it in IntelliJ,
then restore .idea/modules.xml and delete .idea/gradle.xml. Again, you may need to
remove some cached files from ~/Library/... as above. When the project is opened
in IntelliJ there should only be one module, `flutter-intellij`.
