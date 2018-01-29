_Note: this doc is out of date_

## Building the plugin

We use ant to build the plugin; to check your ant install, run `ant -version`. We recommend
`1.8.2` or later (to install ant on a mac, use `brew install ant`).

To build the plugin, type:

```
ant build
```

Artifacts are written into the `build/` directory; for example:

```
build/flutter-intellij.jar
```

## Other commands

- `ant -p` - list all available ant tasks
- `ant build` - build the plugin and associated tests
- `ant test` - run the unit tests
- `ant all` - build the plugin and tests, and run the tests

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
