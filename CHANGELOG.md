## 42.1
- Enable Hot UI for Flutter Interact
- Fix two NPEs from the outline view (#4188)
- Fix an NPE from CommonTestConfigUtils.getTestsFromOutline (#4184)

## 42.0
- Hot UI implementation (#4160)
- Support new Inspector select api and a few other minor tweaks (#4158)
- Services to manage tracking inspector and editor events more effectively
- Support Inspector APIs needed for HOTUI (#4153)
- Smooth out the rough spots in add-to-app support (#4129)
- Remove an unused field (#4152)
- Switch getWidgetDescription to use CompletableFuture as it sometimes takes more than 100ms (#4151)
- Update the product matrix for 2019.3 stable (#4148)
- Fix an NPE in PubRoot.forDescendant (#4147)
- Remove FlutterSampleManager; for embedded flutter samples, open the hosted docs (#4139)
- Make the async rate limiter implementation a bit more robust (#4144)
- Refactor of Color parsing code to share more logic with HotUI (#4141)
- Show error dialog when creating a project with canary builds (#4140)
- Misc test cleanup (#4138)
- Add null check to guard against missing property (#4135)
- Add some more logging information to help diagnose the 'method not found' errors (#4125)
- Force VFS refresh to prevent adding multiple include statements (#4126)
- Fix an npe when getting the target fps for a device (#4124)
- Add missing Nullable annotation (#4122)
- Remove the two secondary UI guides settings (#4121)
- Allow defining location of Android add-to-app modules (#4117)
- Use a LinkLabel for the privacy link in the settings page (#4120)
- Fix cases where ActiveEditorsOutlineService was not thread safe (#4119)
- Refactor the UI and location of the Flutter Performance tool window (#4111)
- Support removing widget property values (#4118)
- Build for Android Studio beta 4 and later (#4109)
- Support for debugging add-to-app modules in Android Studio (#4097)
- Update FlutterDartAnalysisServer to support Widget manipulation methods (#4102)
- Improve devtools launching (#4105)
- Reduce the severity of a warning (#4107)
- Address an 'Unknown platform' message in the IntelliJ log (#4104)
- Address a npe / race condition in StdoutJsonParser.java (#4101)
- Remove support for package:flutter_web (#4088)
- Don't try to format a read only doc (#4098)
- Address a disposable issue (#4094)
- Add more diagnostics for an error (#4092)
- Silence a log warning (#4091)
- Use NotNull and remove an if check (#4087)
- Address a resource leak on shutdown (#4074)
- Fix a null assertion (#4076)
- Remove a System.out.print reference (#4075)
- Address an npe in FlutterConsole.java (#4077)
- Misc cleanup to the HeapMonitor class (#4078)
- Add diagnostics for 'Runner must be specified' error (#4068)
- Prevent an exception related to use of a disposed Alarm (#4071)

## 41.1
- Adjust build range for AS beta 3 (#4061)
- Fix error from undefined macro (#4056)

## 41.0
- Remove support for file path breakpoints; handle multiple vm breakpoints better (#4019)
- Force Android Studio to show the Project view (#4031)
- Build for ADS4 canary 1 (#4017)
- Check for project disposed during format on save (#4022)
- Remove an Observatory link handler from DartVmServiceDebugProcess.java (#4018)
- Detect whether the VM service connection supports the getMemoryUsage API (#4016)
- Don't invoke eval when shutting down (#4015)
- Re-enable our disabled tests (#4013)
- Co-edit module created in Android Studio (#4004)
- Address several warnings in the IntelliJ log about leaked resources (#3999)
- Remove an unused method (#4009)
- Update CONTRIBUTING.md
- Change the inspector tool window to not only show when an app is running (#4000)
- Remove the Performance tab from the Inspector; remove Observatory actions (#4008)
- Hide the custom logging view setting (#4005)
- Fix a project assert in the Flutter editor notification (#3994)
- Address a few warnings about 'mostly idle daemon process' (#3995)
- Add more info to eval() exceptions (#3992)
- Fix an NPE when switching platforms (#3990)
- Fix an NPE in the feedback button (#3989)
- Rename two wrap with Flutter actions (#3988)
- Remove support for the older `_Logging` event (#3986)
- Various bits of code cleanup (#3981)
- Remove two reload and restart keybindings (#3979)
- Fix the offset of console hyperlink detection (#3978)
- Fix an issue where non-ephemeral device selection wouldn't persist (#3977)

## 40.2
- Increase version range for AS 3.6 beta 1 (#3973)
- Fix NPE in when querying display refresh rate (#3927)
- Fix a ConcurrentModificationException from ActiveEditorsOutlineService (#3954)
- Ignore errors from `app.stop` (#3957)

## 40.1
- Unhook Gradle listeners from IntelliJ (#3941)
- Fix a ConcurrentModificationException (#3939)

## 40.0
- Change args to work with new defaults of flutter create (#3902)
- Fix the changelog markdown translation (#3904)
- Use the new getMemoryUsage() API (#3877)
- Turn on the detailed test output by default for Bazel (#3876)
- Query display refresh rate and use in Performance window (#3890)
- Fix an NPE in FlutterIconProvider.java (#3893)
- Remove use of some deprecated calls (#3886)
- Build for 2019.3 EAP and 3.6 canary 11 (#3872)
- Update to latest version of VmService (#3889)
- Fix merge error (#3888)
- Support co-editing Flutter and Android in a single project (#3850)
- Replace some deprecated API calls (#3875)
- Add build actions (#3868)
- Add auto-edits of iml (#3870)
- More normalization of flutter error codes (#3866)
- Fix a couple AS issues (#3864)
- Fix a ConcurrentModificationException in FlutterSdk.getFlutterSdk() (#3863)
- Address instances of process execution on the EDT (#3858)
- Fix open widget sample opening only counter sample app (#3854)
- Add Hide Notification hyperlink to Notification Panel (#3761)

## 39.0
- Changed project creation to default to AndriodX deselected until it works for Flutter modules
- Enabled structured errors by default
- Fix #3731: Synchronous execution on EDT (#3823)
- Make the new languages be default (#3819)
- Don't call reload for the unforked flutter web impl (#3816)
- Perform additional normalization on flutter error codes (#3813)
- Fix an issue related to the error reporter (#3811)
- Improve computeDefaultPresentation (#3803)
- Convert an error to a warning (#3810)
- Fix ArrayIndexOutOfBounds for target platform selector (#3809)
- Flutter web inspector (#3792)
- Rev to the latest vm service library (#3801)
- Adapt to API changes in ASc6 (#3802)
- Switch to using the VM Service directly instead of the Daemon protocol when invoking service extensions (#3790)
- Update no-response.yml (#3789)
- Upgrade the version of the dart plugin that we compile against (#3784)
- Address a setPreferredFocusableComponent() warning in the IntelliJ log (#3783)
- Fix a regression in the Flutter Outline view (#3782)
- Cache the results of parsing the pubspec file (#3773)
- Only parse analysis server events we're interested in (#3772)
- Optimize FlutterUtils.isInTestDir (#3774)
- Add platforms to testing matrix (#3768)
- Make part of the dart code use implicit-casts false (#3762)

## 38.2
- Fix bug on Windows that prevented outlines from displaying
- Restore the ability to run individual tests 
- Fix a couple other issues

## 38.1
- Fix first-time installation issue

## 38.0
- Add AndroidX option to new project wizards (#3705)
- Fix break due to Android Studio 3.6 API change (#3712)
- Fix logger npe (#3711)
- Integration test update (#3710)
- Fix highlighting of project descriptions (#3709)
- Re-enable tests on the bots (#3702)
- Update the flutter error display (#3682)
- Relabel 'samples'to 'widget samples' (#3701)
- Address some issues with blocking the EDT thread in 2019.2 (#3700)
- Remove the web/desktop user preference (#3698)
- Fix the logging tests (#3699)
- Use DAS test annotations to flag runnable tests (#3662)
- Add short-lived prompt for Q3 user survey (#3691)
- Add a user preference to opt-in to showing structured errors (#3692)
- Add target platform selector for togglePlatform service extension (#3688)
- Update the widgets.json catalog file (#3687)
- Restore stack traces in generated error reports (#3685)
- Make the event stream tests pass and re-enable them (#3684)
- Init default settings for the run console text wrapping (#3661)
- Send flutter.error analytics (#3659)
- Remove extra console whitespace (#3660)
- Fix an issue with some daemon json output appearing in the console (#3658)
- Initial work on displaying Flutter.Error events (#3644)
- Show truncated logging messages (#3641)
- Name the Bazel test config factories to match assumed names in g3 (#3636)
- Remove no longer used functionality related to restart warnings (#3645)
- Fix per SDK Stream<Uint8List> breaking changes (#3640)
- Fix New Pproject Wizard (#3631)
- Adjust the text for the desktop/web device preference (#3629)
- Issue 3615. Fix message for 'Remove widget' (#3622)
- Support showing desktop and web devices (#3618)

## 37.0
- Fix an offset issue with the UI guides code (#3574)
- Add IDE query param to DevTools url. (#3592)
- Treat FlutterApp as a long-running process (#3599)
- Fix links for test URLs (#3600)

## 36.0
- Add Gradle build script (#3529)
- Update for new platform releases (#3527)
- Add in a preference to toggle closing labels (#3528)
- Don't disable closing labels as part of UI Guides (#3525)
- Enable devtools launching from Bazel (#3511)
- Guard against null project basedir (#3524)
- Change DeviceDaemon to show a detailed error when it fails too many times. (#3513)
- Add an inline run menu option to run or watch Bazel Flutter tests (#3507)
- Make the save dialog refer to the save all action, not the save action (#3505)
- Introduce an opt-in detailed test runner for Bazel tests (#3451)
- Remove the android studio specific memory view (#3497)
- Limit the amount of time we wait for a graceful app shutdown (#3490)
- Mark the device daemon process as a background process (#3488)
- Fix errors in AS 3.5 beta 1 (#3487)
- Remove the preview view from the flutter outline view (#3481)
- Remove a println in WidgetIndentsHighlightingPass.java (#3468)
- Fix an npe from the FlutterErrorReportSubmitter.java class (#3469)
- Handle notifications when a project has been disposed more gracefully. (#3472)
- Fix two bugs for widget guide outlines. (#3470)

## 35.1.3
- Support IntelliJ 2019.1.2 RC
- Support Android Studio 3.5 beta 1
- Bug fixes

## 35.1
- Add an option to hide closing labels in Dart source code when UI guides are on (#3438)
- Create "Editor" section of Flutter Settings (#3434)
- Support UI as Code Widget Guides (#3420)
- Add checkbox to skip the dart analyzer error check before a hot reload (#3414)
- Remove the option to disable the memory view from the settings (#3408)
- Track API changes (#3427)

## 35.0
- Sample panel layout improvements (#3396)
- Remove unneeded logging (#3394)
- Java analysis lints cleanup (#3395)
- Update subscriptions after analysis server restart (#3393)
- Read sample index from flutter_tool call (#3379)
- Update README (#3387)
- Fix unit tests
- Update build for canary 11 (#3380)
- Integration test update (#3374)
- Make the inspector easier to test (#3373)
- Adjust build to make plugin for testing (#3366)
- Address reported Java lints (#3356)
- Adjust build for AS canary 10
- Address an array index out of bounds (#3355)
- Address an NPE (#3354)
- Upgrade the service protocol library (#3353)
- Address a number format exception (#3352)
- Update how we manipulate the service protocol url (#3351)
- Remove some uses of reflection (#3350)
- Some initial work for FlutterWeb apps (#3342)
- Fix an NPE when sample content generation is disabled (#3336)
- Add inspector dependency to test (#3316)
- Make Dart constructor calls pop out in light mode  (#3327)

## 34.0
- Update build for Android Studio 3.3.2 and IntelliJ 2019.1 (#3321)
- Fix issue preventing plugin from working in AS Canary 8 (#3321)
- Provides a better display if the variable has a `toStringDeep()` method defined. (#3291)
- Don't show a background square in the inspector summary tree. (#3326)
- Make FlutterModuleUtils consistently robust to disposed projects. (#3323)
- Fix NPE issue sometimes hit evaluating expressions. (#3324)
- Fix widget names. (#3322)
- Make Perf and Inspector views only display when a Flutter app is being debugged. (#3320)
- Support the inspector for flutter_web libraries. (#3310)
- Detect when integrations tests are running (#3308)
- Add in support for reloading and restarting all running apps (#3268)
- Log tree path selection fixes (#3302)
- Throttle logger updates (#3280)
- New method in FlutterUtils: declaresFlutterWeb, this method checks for dependencies: fluttler_web in a pubspec file. (#3275)
- Update a comment in FlutterSaveActionsManager (#3277)
- Remove the second parameter (the Project) from SdkFields constructor, it isn't used anymore. (#3261)
- Add a comment to a recent change (#3267)
- Fix a file handle leak (#3264)
- Port inferPubRootDirectoryIfNeeded from devtools (#3242)
- Add support for matching customized Widget tests. (#3249)
- Hide DevTools debugger when launching from IntelliJ. (#3252)
- Migrate to GearPlain (#3248)
- Minor cleanup (#3247)
- Inline sample index reading (#3245)
- Make a newer daemon protocol field optional (#3230)
- Link to the plugins readme file from the building instructions. (#3222)

## 33.3
- Fix an issue with an IllegalArgumentException when running Flutter apps

## 33.2
- Support IntelliJ 2018.3.3

## 33.1
- add menu and toolbar button to open Flutter DevTools
- fix Gradle sync issue for Android Studio 3.3.1
- fix highlighting of the Go link in sample banner

## 33.0
- update build for Android Studio 3.3.1
- reorder console filters so links work
- more intelligently enable support for detaching from Flutter apps on exit
- change the icon used for paint baselines
- prevent bazel test run configurations from generating in a non-bazel workspace
- support 2019.1 eap
- mention 'Dart' in the plugin description
- correct the bazel output for debugging bazel tests
- simplify the bazel parameters we pass to Bazel Run configurations
- pin flutter error events in the log
- propagate node selections to inspector
- link support for log data entries
- fix category cell rendering
- add sample creation banner
- add sample apps to Android Studio New Project Wizard
- update log entry data badge

## 32.0
- address an NPE in FlutterWidgetPerfManager.java
- added overlay renderered for GC, snapshot and memory reset events
- consolidated all adt-ui API changes in FlutterStudioMonitorStageView
- support for creating projects w/ sample content from the IDEA New Project Wizard
- basic ansi color support for entries in the Flutter Logging View
- restore log level combo to the Logging View
- support to fill in truncated log entries
- add keyboard shortcut for widget extraction
- add debugPaint and debugAllowBanner icons
- add repaint rainbow icon
- handle cases where script.tokenPosTable is null
- auto-hide details pane
- guard against disposed when querying project type
- fix an issue with escaped test names
- refactor service extensions and set button text based on extension state
- shorten message for debug mode perf disclaimer
- listen for ServiceExtensionStateChanged events
- restore service extension states from device on start and attach
- don't use LOG.error()
- refactor the Bazel Test configuration to support running tests on a single file or a single test
- fix enabled/disabled text for service extensions
- fix NPE in bazel config

## 31.3
- fix NPE in sdk installation (#2965)
- fix NPE caused by internal inconsistency (#2963)

## 31.2
- show memory profiler legend with proper line chart color or line style
- prevent the (IntelliJ) New Project wizard from completing when there is no Flutter SDK
- fix a race condition causing unexpected conditions in attach
- added control of RSS display to memory profiler
- when running the flutter doctor command, use the -v flag
- make attach use selected device

## 31.1
- perf table polish and fix links to tip docs
- fix Split Mode resize issue
- rebuild stats wording tweaks

## 31.0
- change FPS display to "Frame Rendering Time" and improve UI
- reorganize inspector tools
- better error reporting for Flutter runtime issues
- fewer Flutter runtime issues
- updated icons for Material and Cupertino
- searchable preferences/settings
- added refactoring to outline view: extract widget
- new menu item to run 'flutter make-host-app-editable'
- code cleanup and bug fixes

## 30.0
- performance inspector changes
- log view tweaks
- memory profiler updates
- support 'flutter attach' in the IDE (both IJ and AS)
- support offline project creation in the AS wizard
- code cleanup and bug fixes

## 29.1
- address an issue with an NPE when debugging

## 29.0
- add 'Wrap with Container' to preview
- fix test navigation
- clear log on restart
- experimental memory profiler; enable in preferences
- build for 2018.3 EAP
- bug fixes

## 28.0
- build for Android Studio 3.3 Canary and 3.2 Beta
- add UI support for importing Flutter modules into Android apps
- add more details to logging output
- bug fixes

## 27.1
- change the preference for --track-widget-creation to default to off

## 27.0
- add a setting to control syncing Android libraries
- fixes related to evaluating expressions when not on a call frame
- auto-disable scroll to end when the user manually scrolls the log up
- add the "module" template to new-module and project wizards in Android Studio
- improve copy / paste in the Logging View
- some tweaks to the open in Android Studio functionality
- validate android package names
- add Android module libraries to Flutter projects
- validate org in the project wizard
- default log coloring to on and update logger defaults
- fix log entry browser links
- support hyperlinks in flutter console log
- add InheritedWidget and Stateful Widget with Animation live templates
- lower case the log level names

## 26.0
- updates to support Android Studio 3.2 Beta
- removes the Inspector's empty content message
- support setting log color from flutter log settings page
- support hiding/showing log categories (#2398)
- add flutter log color settings page (#2394)
- change the default for the open inspector setting
- look for the emulator tool in the 'emulator/' directory first (#2383)
- support filtering by log level (#2380)
- fix the flutter log view while resizing (#2379)
- log entry coloring (#2382)
- log tree rendering refactor (#2381)
- for BazelRunConfig launches, print the command-line to the console (#2368)
- refactor the Flutter debugging client code (#2359)
- support match case/regex filter in log view (#2350)
- fix auto-scroll to catch up to fully rendered log tree (#2342)
- use the log category name from the dart:developer event (#2339)
- fix-up missing create project mnemonics (#2326)
- handle reload errors (#2321)
- fixes for the native editor banner

## 25.0
- remove the user preference to disable --preview-dart-2
- don't use 'new' for the stless, stfull, stanim templates
- add support for IntelliJ 2018.2 EAP (#2270)
- added a new (very experimental) logging view
- update the extract widget refactoring visibility (#2251)
- launch a simulator device if none is running (#2234)
- improvements to the preview view on Windows (#2239)
- open the selected file for editing when opening a new project (#2236)
- open selected file when launching Android Studio (#2230)
- add a command bar to editors that can open in a native-code editor (#2216)
- rename full restart to hot restart (#2225)

## 24.2
- fix the --track-widget-creation flag implementation on Windows
- fix for an exception in the Outline view on older Flutter SDKs

## 24.1
- update Flutter icons
- fix an exception when the selection changes and the outline view isn't visible
- fix for an issue with reload on save in profile runs
- fix for a 2017.3 issue with a 'no running apps' message in the inspector

## 24.0
- inspector: significant UI refactoring to show the tree in a master / details format
- inspector: add a 'Performance' tab to the Inspector, to show application FPS and memory usage
- inspector: fix issues turning --track-widget-creation on and off
- inspector: handle apps with multiple isolates in the inspector

- live preview: suggest 'Add forDesignTime() constructor' for widgets
- live preview: make the preview area smaller if the widget is not renderable
- live preview: fixes to make outline preview working on Windows
- live preview: sort children outlines by their RenderObject.depth during preview

- simplify how we recognize Flutter projects when using Bazel
- fix the "Open in Android Studio" action to not show for the ios dir
- add an option to create projects in “offline” mode
- better support for using multiple Flutter modules per IntelliJ project
- improvments to the "Open in XCode…" menu item
- better support for importing Flutter projects
- several fixes for issues with using resources that had been disposed
- add local history labels on reloads and restarts
- have the 'reloading...'' notification timeout after the reload completes
- improved support of running in --profile mode
- expose the new 'Extract Widget' refactoring

## 23.2
- updated some Bazel breakpoint logic

## 23.1
- disabled an Android facet's ALLOW_USER_CONFIGURATION setting, to address a continuous indexing issue

## 23.0
- outline view: removed the experimental flag
- outline view: filter the outline view to only show widgets by default
- inspector: several stability and polish improvements
- inspector: now supports inspecting multiple running apps at the same time
- we now show material icons and colors in code completion (requires 2017.3 or AS 3.1)
- running and debugging flutter test adding for Bazel launch configurations
- added an 'Extract method' refactoring
- the preview dart 2 flag can now accept the SDK default, be set to on, or set to off
- Android Studio: we now support 3.1
- Android Studio: fixed an issue where Android Studio was indexing frequently
- experimental: added a live sparkline of the app's memory usage
- experimental: added a live preview area in the Outline view
- experimental: added the ability to format (and organize imports) on save

## 22.2
- when installing the Flutter SDK, use the 'beta' channel instead of 'dev'

## 22.1
- when installing the Flutter SDK, use the 'dev' channel instead of 'alpha'
- fix an issue with the Flutter Outline view on Windows

## 22.0
inspector view:
- support for multiple running applications
- basic speed search for the Inspector Tree
- restore flutter framework toggles after a restart
- expose the observatory timeline view (the dashboard version) (#1744)
- live update of property values triggered each time a flutter frame is drawn. (#1721)
- enum property support and tweaks to property display. (#1695)
- HD inspector Widgets (#1682)
- restore inspector splitter position (#1676)
- open the inspector view at app launch (#1670)

outline view:
- rename 'Add widget padding' assist to 'Add padding' (#1771)
- bind actions to move widget down/up. (#1768)
- rename 'Replace with children' to 'Remove widget'. (#1764)
- add action for 'Replace with children' assist. (#1759)
- update messages for wrapping with Column/Row. (#1745)
- add icons and actions for wrapping into Column and Row. (#1743)
- show build() methods in bold (#1731)
- associate the Center action with the corresponding Quick Assist. (#1726)
- navigate from source to Preview view. (#1710)
- add speed search to the Preview view. (#1696)
- add basic Flutter Preview view. (#1678)

platforms:
- support 2018.1 EAP
- no longer build for 2017.2

miscellaneous:
- fix for displaying the flutter icon for flutter modules
- fix for issue 1772, Switch Bazel flag for launching apps (#1775)
- add support for displaying flutter color shades in the editor ruler (#1770)
- add a flag to enable --preview-dart-2 (#1709)
- smarts to run `flutter build` before trying open Xcode (#1373). (#1694)
- harden error reporting on iOS simulator start failures (#1647). (#1681)

## 21.2
- Fix an NPE when the Flutter SDK version file contains the text 'unknown'


## 21.1
- Fix an NPE when reading the Flutter SDK version file

## 21.0
- select an existing config at launch
- fix test discovery for plugin example tests
- fix discovery of tests in example subdirs
- improve pub root detection for flutter tests
- actionable “restart” debugging console output
- improve console hyperlinking for local files
- fix run config autoselection for plugin projects
- for non-bazel project configurations, don't show the FlutterBazelRunConfigurationType
- update FlutterViewCondition to be bazel project aware
- remove the preference for the Inspector view (it's now on by default)
- rename the Flutter view to Flutter Inspector
- clean up of the Flutter Inspector View icons
- show color properties with a nice color swatch icons
- add a notification for reloaded but not run elements
- show flutter material icons in the inspector
- for Bazel launch configurations, update the android_cpu architecture type from armeabi to armeabi-v7a


## 20.0
- improved console filtering
- improved unit test running support to allow running package:flutter tests
- improved "Open with Xcode..." logic to work better for plugin projects
- fixed project creation to properly respect custom creation options (such as target language)
- fixed an NPE sometimes encountered when deleting projects


## 19.1
- Bazel run configuration updates

## 19.0
- fixed an issue with reload when multiple project windows are open
- fixed running Flutter tests in nested groups
- fixed miscellaneous project wizard issues
- fix to ensure we don't create Flutter library entries for non-Flutter projects
- fixed project name validation in the new project creation wizard to be more performant
- fixed project opening to only open main.dart if no other editors are open
- fix to limit Flutter icon contributions to Flutter projects
- reload on save updated to ignore errors in test files
- IDEA EAP support
- fix to give restarted apps focus on iOS
- miscellaneous Android Studio support fixes
- fixed check for Flutter tests to not mis-identify vanilla Dart tests
- improved error reporting on project creation failures


## 18.4
- Revert to 18.1 to address an NPE in the FlutterInitializer class
- fixed an issue where reload on save could not be disabled

## 18.3
- fixed a build problem that prevented the Android Studio plugin from creating projects

## 18.2
- fixed an issue where reload on save could not be disabled
- fixed an exception that could occur on project creation

## 18.1
- fixed hot reload issue when multiple project windows were open
- fixed 'Open Observatory timeline' action

## 18.0
- Android Studio support
- for flutter launches, support passing in a --flavor param
- reload on save now on by default
- improved and reorganized the Flutter view's toolbar
- analysis toast provides a new hyperlink to open the analysis view
- reloads disallowed while another reload is taking place
- support to show referenced flutter plugin in the project view


## 17.0
- improved new project wizard
- improvements to the reload-on-save behavior
- improved and reorganized the Flutter view's toolbar
- fixes to the Flutter icon decorations in the editor ruler
- fixes to group handling for widget tests
- display a ballon toast if there are analysis issues when running apps
- added a toggle inspect mode toolbar button
- speed improvements to the device switcher pulldown


## 16.0
- device list refresh fixes
- support for flutter run in profile and release modes
- support for reading the android sdk location from flutter tools
- support for discovering and running Flutter widget tests
- Flutter test console improvements
- support for running flutter doctor in a Bazel workspace
- test file icon annotations
- support for locating a missing flutter SDK in .packages files
- open emulator action sorting
- test state icons for Flutter tests
- editor line markers for Flutter tests
- added a new restart daemon action
- open emulator action sorting
- run/debug button enablement improvements
- fix to ensure the `Install SDK…` action is always visible
- support for running a single Flutter test, by name
- install creation progress UI fixes
- project creation fixes for small IDEs
- fixes to android emulator launching


## 15.2
- fix for an exception in the new project wizard in WebStorm (#1234)

## 15.1
- fix for a file watching related NPE on build systems using Bazel (#1191)

## 15.0
- UI for starting android emulators from the device pull-down
- workflow for installing a Flutter SDK from the New Flutter Project wizard
- Flutter SDK configuration inspection improvements
- improved error reporting on project creation failures
- improved app reload feedback
- Flutter View toolbar tweaks
- initial support for running unit tests with `flutter test`
- new action to open iOS resources in Xcode


## 14.0
- user toggleable option to enable more verbose debug logging of Flutter app runs
- fixes to the new Flutter Module workflow
- improved console logging on Flutter app termination
- improved error reporting on Observatory connection and Flutter View open failures
- removed Flutter SDK settings from default projects
- improved project name validation (to align with checks in `flutter create`)
- console hyperlinks for Xcode resources
- fix to inherit Android JDK setting when creating Flutter projects
- fix to ensure Flutter console filtering is only applied to Flutter consoles
- improved device daemon interop
- improved SDK version checking


## 13.1
- project opening improvements
- new action to open the Flutter view
- module name validation on creation
- fix to ensure all open files are saved to disk before running Flutter actions
- improved progress reporting during calls to 'flutter create'
- miscellaneous fixes and analytics improvements

## 13.0
- small IDE support improvements
- android module enablement on project creation
- project explorer icon customizations
- support for Flutter drop frame debugging
- hot reload UX improvements
- Bazel run config refinements
- support for toggling OS in the Flutter View
- Flutter CLI interop fixes (proper env setup)
- color icon improvements
- bump to require 2017.1+


## 12.1
- fix an issue with enabling Dart support for modules from the Flutter settings page

## 12.0
- support for IDEA `2017.1`
- new Flutter `stless`, `stful`, and `stanim` live templates
- new assists for editing the widget hierarchy:
    - move widget up or down
    - re-parent widget or list of widgets
    - convert `child:` keyword to `children:`
- support for specifying "Additional Args" to Flutter application launches
- default run configuration creation on project open (when possible)
- device menu improvements
- miscellaneous bug fixes


## 0.1.11.2
- fix an NPE in the Flutter View when launching an app

## 0.1.11.1
- fix to a use after dispose exception in the Flutter View

## 0.1.11
- Flutter tool window badging when active
- iOS console output folding improvements
- Flutter reload actions added to main "Run" menu
- devices menu fixes
- improved tooltips for pubspec editor notifications


## 0.1.10
- fixes to pubspec timestamp checking
- analytics events for run, debug, and process stop
- fix to `flutter doctor` to better support multiple runs
- fix to the reload action for apps launched from 'run'


## 0.1.9.1
- fix button enablement in the Flutter View
- fix the reload action for apps launched from 'run'

## 0.1.9
- added a 'Flutter' view to allow users to toggle Flutter framework debugging features while running
- fixes to the visibility of the "Tools" menu
- inspection to detect pubspec modifications (that may imply out of date package dependencies)
- key bindings fixes
- support for opening source folders as Flutter projects (using "Open...")
- run and debug button enablement fixes
- fix to bring iOS simulator to front on run/debug
- fix to handle devfs breakpoints for projects without pubspecs


## 0.1.8.1
- improve handling of breakpoints for the bazel launch config

## 0.1.8
- fixed race condition in console reporting on project creation
- improved interaction between Flutter and Dart plugins during project creation (no more unnecessary nags to run pub)
- improvements to version checking
- settings UI refinements
- new "Help > Flutter Plugin" top-level menu
- added reload/restart actions in the main toolbar
- improved console folding for iOS messages
- fixed NPE in project creation


## 0.1.7
- improved console output folding when running iOS apps
- actions for Flutter package get and package update
- a new top level Flutter menu (Tools>Flutter) with common Flutter actions
- updated hot reload and restart icons
- editor annotations showing Flutter colors and icons in the editor ruler
- better console filtering (less noise)
- improved detection of Flutter projects missing a Flutter module type


## 0.1.6
- reload and restart keybinding mapping fixes
- new butter bar with actions for flutter.yaml files
- "run" behavior re-designed to support reload
- improved console output for reloading and restarting
- miscellaneous fixes and stability improvements


## 0.1.5
- console filtering for flutter run output
- improved messaging for incomplete Flutter SDK configurations
- support for new application events produced by Flutter tools
- fixed duplicate service protocol console logging
- Flutter run configuration cleanup
- fixed NPE in showing progress from Flutter tools tasks
- migration away from storing Flutter SDK location in an application library


## 0.1.4.1
- removed an exception notification when we receive unknown events from the flutter tools

## 0.1.4
- first public release


## 0.1.3
- notifications for projects that look like Flutter apps but do not have Flutter enabled
- improved Flutter preference UI and SDK configuration
- IDEA version constraints to ensure that the plugin cannot be installed in incompatible IDEA versions


## 0.1.2
- fixed device selector filtering


## 0.1.1
- removed second (redundant) "open observatory" button
- filtering to ensure the Flutter device selector only appears for Flutter projects
- fixed hangs on app re-runs


## 0.1.0
- initial alpha release
