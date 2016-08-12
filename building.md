## Building the plugin

We use gradle to build the plugin, and require version 2.1 or greater (`gradle -version`).

To build the plugin, type:

```
gradle build
```

Artifacts are written into the `build/` directory. For example:

```
build/libs/flutter-intellij-0.0.1.jar
```

## More information

Documentation for the options in `gradle.properties` is available at
[github.com/JetBrains/gradle-intellij-plugin](https://github.com/JetBrains/gradle-intellij-plugin).

## Other commands

- `gradle tasks` - get a list of available gradle tasks
- `gradle build` - build the plugin
- `gradle clean` - delete the `build/` directory
- `gradle test` - run the plugin tests
