## Building the plugin

We use ant to build the plugin; to check your ant install, run `ant -version`.

To build the plugin, type:

```
ant build
```

Artifacts are written into the `build/` directory. For example:

```
build/flutter-intellij.jar
```

## Other commands

- `ant -p` - list all available ant tasks
- `ant build` - build the plugin and associated tests
- `ant test` - run the unit tests
- `ant all` - build the plugin and tests, and run the tests
