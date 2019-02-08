This module customizes the Flutter plugin for Android Studio.

To set up a development environment using sources for Android Studio:

1. Initialize Android Studio sources.
2. Checkout Flutter plugin sources, tip of tree.
3. Follow the directions for setting up the Dart plugin sources
   in intellij-plugins/Dart/README.md with these changes:
    - you do not need to clone the intellij-community repo
    - open studio-master-dev/tools/idea in IntelliJ
    - possibly skip running intellij-community/getPlugins.sh
4. Checkout Dart plugin sources, branch 173.
5. Build everything.
    - Do a blaze pre-build:
        - tools/base/bazel/bazel build //tools/adt/idea/android:artifacts
        - This generates some required classes that are not checked in.
    - Do a blaze build from a terminal window. Allow 15+ min for this.
    - OR in IntelliJ: Build > Rebuild Project
    - Some Android Studio classes need to be generated.
    - The Dart plugin cannot be built during this step.
    - Launch Android Studio in the debugger to ensure everything 
    got built correctly.
6. Using the Project Structure editor, import
    - intellij-plugins/Dart/Dart-community.iml
    - flutter-intellij/flutter-intellij-community.iml
    - flutter-intellij/flutter-studio/flutter-studio.iml
7. Select the `community-main` module and add module
   dependencies to `Dart-community`, `flutter-intellij-community`,
   and `flutter-studio`.
8. (temporary) Add a compile-exclusion for DartiumDebuggerEngine.kt.

Alternatively (especially if you do not want to do a blaze build),
set up your classpath the way it is done during travis runs.
Configure IntelliJ as you would for Flutter plugin development,
then import the flutter-studio plugin and add it as a dependent on
the Flutter plugin.

The GUI tests must be run in the same version of IntelliJ used by
the Android Studio dev team. Currently that is 2018.2.

To run the tests create a Junit Run Configuration for class
`io.flutter.tests.gui.NewProjectTest`. Set its working directory
to the root directory of the Android Studio sources. For
example: `/Volumes/android/studio-master-dev/tools/idea`
Set it to use the classpath of module `flutter-studio`.
It needs to run with Java 8 or later.

The VM options are a bit complex. Here's mine (formatted with 
newlines in place of spaces):
```bash 
-Xms512m
-Xmx4096m
-ea
-XX:ReservedCodeCacheSize=240m
-XX:+UseConcMarkSweepGC
-XX:SoftRefLRUPolicyMSPerMB=50
-XX:MaxJavaStackTraceDepth=10000
-Didea.is.internal=true
-Didea.platform.prefix=AndroidStudio
-Dandroid.extra_templates.path=../../../sdk/templates
-Dmrj.version=mac
-Dcom.apple.macos.useScreenMenuBar=true
-Dapple.laf.useScreenMenuBar=true
-Dcom.apple.mrj.application.apple.menu.about.name=AndroidStudio
-Dsun.awt.disablegrab=true
-Dawt.useSystemAAFontSettings=lcd
-Dsun.io.useCanonCaches=false
-Djava.net.preferIPv4Stack=true
-Didea.jre.check=true
-Didea.debug.mode=true
-Dflutter.home=/Users/messick/src/flutter/flutter
-Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine
-Dplugin.path=/Volumes/android/studio-master-dev/prebuilts/tools/common/kotlin-plugin/Kotlin
-Didea.config.path=/tmp/idea-test/config
-Didea.system.path=/tmp/idea-test/system
-Didea.plugins.path=/tmp/idea-test/plugins
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```
If you're not using a Mac then delete these:
 - mrj.version
 - com.apple.macos.useScreenMenuBar
 - apple.laf.useScreenMenuBar

The last line causes the test to be forked in a remote JVM
that's waiting for a debug connection. Make a 'Remote' run
config and launch it after launching the Flutter test run
config to get it running.

Got past React native issue by defining custom JDK that includes
Javascript and CSS lib dir contents from IntelliJ plugins.
Also disabled JS plugins, not sure that helped.
The custom JDK causes the Javascript plugin to be loaded
twice, so there's a hack in the test runner to compensate.
