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
