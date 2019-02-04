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
See tool/plugin/README.md for details.
