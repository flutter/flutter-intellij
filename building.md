## Building the plugin

We use ant to build the plugin; to check your ant install, run `ant -version`. We
recommend `1.8.2` or later (to install ant on a mac, use `brew install ant`).

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
