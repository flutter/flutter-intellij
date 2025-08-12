# Creating a new release 

## Building the plugin zip file:
- Make changes to the repository for the version.
  - Edit `CHANGELOG.md` to include the current milestone and associated release notes.
  - Increment the version for the dev release in `tool/kokoro/deploy.sh` (e.g. if the release about to go out is M90, the dev release version will be 91.0).
  - Commit these changes.
- Update `gradle.properties` with the release number.
- Run `./gradlew buildPlugin` - this will generate a `flutter-intellij.zip` file in `build/distributions`.

## Uploading the plugin:
- Sign in to https://plugins.jetbrains.com.
- From https://plugins.jetbrains.com/plugin/9212?pr=idea, select `'update plugin'`.
- Upload the `flutter-intellij.zip` file created from the earlier step (note: the `.zip` file).
