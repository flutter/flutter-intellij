# Creating a new release 

## From IntelliJ:
- ensure that the changelog in `CHANGELOG.md` is up-to-date (from the current milestone)
  - edit `CHANGELOG.md` to update the release notes
  - re-generate the plugin.xml files using `bin/plugin generate`
  - commit these changes
- create a new branch locally from that commit (`git checkout -B release_<version> SHA`, replacing `<version>` and `SHA` as appropriate)
- in a terminal emulator, `bin/plugin --release=<version> build`, again replacing `<version>` with the current version number
- the plugin is created as three separate `flutter-studio.zip` products in `/project/root/releases/release_<version>` (all three will be uploaded separately)
  - 2017.3
  - 2018.1
- push your release branch upstream (`git push -u upstream release_21`)

## From plugins.jetbrains.com:
- sign into https://plugins.jetbrains.com
- from https://plugins.jetbrains.com/plugin/9212?pr=idea, select `'update plugin'`
- upload the `flutter-studio.zip` files (one for each product) created from the earlier step (note: the `.zip` file)

# Pushing a patch to an existing release

When it's necessary to patch a previously released build:

## Change to the release branch

- the fix should have already been committed to master
- `git pull` to get the latest branch information
- change to the branch for that release (`git checkout release_21`)

## Cherry-pick the fix

- cherrypick the specific commit (`git cherry-pick 2d8ac6a`)
- ensure that the changelog in `resources/META-INF/plugin.xml` is up-to-date (from the current milestone)
  - edit `CHANGELOG.md` to update the release notes
  - again, to re-generate the plugin.xml files use `bin/plugin generate`
- commit these changes
- push your release branch upstream (`git push -u upstream release_21`)

## Re-deploy the new Jar

- in a terminal emulator, `bin/plugin --release=<version> build`, again replacing `<version>` with the current version number
- the plugin is created as three separate `flutter-studio.zip` products in `/project/root/releases/release_<version>` (all three will be uploaded separately)
  - 2017.3
  - 2018.1
  - 2018.2
- sign into https://plugins.jetbrains.com
- from https://plugins.jetbrains.com/plugin/9212?pr=idea, select `'update plugin'`
- upload the `flutter-studio.zip` created from the earlier step (note: the `.zip` file)