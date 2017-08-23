This module customizes the Flutter plugin for Android Studio.

To set up a development environment:

1. Initialize Android Studio sources.
2. Checkout Flutter plugin sources, tip of tree.
3. Follow the directions for setting up the Dart plugin sources
   in intellij-plugins/Dart/README.md with these changes:
    - you do not need to clone the intellij-community repo
    - open studio-master-dev/tools/idea in IntelliJ
    - possibly skip running intellij-community/getPlugins.sh
4. Checkout Dart plugin sources, branch 171.4772.
5. Using the Project Structure editor, import
    - intellij-plugins/Dart/Dart-community.iml
    - flutter-intellij/flutter-intellij-community.iml
    - flutter-intellij/flutter-studio/flutter-studio.iml
6. Select the `community-main` module and add module
   dependencies to `Dart-community`, `flutter-intellij-community`,
   and `flutter-studio`.
