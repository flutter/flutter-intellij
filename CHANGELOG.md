# 79
- Support IntelliJ 2024.1 (#7269)
- Check version before starting ToolEvent stream (#7317)
- Convert hot reload notifications to not use deprecated methods (#7337)
- Add separate browser windows and save content manager for deep links (#7325)

# 78.5
- Support IntelliJ 2024.1, EAP (#7269)
- Add panel for DevTools deep links (#7307)

# 78.4
- Use Dart plugin's DevTools instance with DTD (#7264)

# 78.2, 78.3
- Fix debugger variable information (#7228)

# 78, 78.1
- Fix DevTools Inspector for all Android Studio users (#7147)

# 77.2
- Update the vendor information for the JetBrains plugin marketplace (#7193)

# 77, 77.1
- Report IDE feature source when opening DevTools (#7108)
- Remove listener for file path on project dispose (#7093)
- Dispose SDK config correctly on app ending (#7064)
- Remove deprecated notification group usage from deep link (#7061)
- Update plugin for Android Studio 2023.3 (Iguana) and IntelliJ 2023.3 (#7113)

# 76.3
- Unmigrate change to use the new ActionListener API from IntelliJ as it introduced an issue with FlutterReloadManager (#6996)
- Remove JX Browser usages and references (#7059)
- Log and show link to open DevTools in separate browser if JCEF fails (#7057)

# 76.2
- Fix for IndexOutOfBounds on startup (#6942)

# 76.1
- Fix for JXBrowser key not provided (#6992)

# 76
- Widget inspector doesn't jump to source code (#6875)
- Change to use `org.apache.commons.lang3.*`, from `org.apache.commons.lang.*` (#6933)

# 75
- Use pooled thread to find location of Android Studio (#6849)
- Update build script for AS canary and IJ stable (#6846)
- Remove isEnableEmbeddedBrowsers setting (#6845)
- Stop showing an error after running tests with coverage (#6843)
- Add gradle to ignore list (#6839)
- Update VM service protocol to 4.11 (#6838)
- Make AS 2022.2 the oldest supported platform (#6837)
- Clear browser tabs when window closes (#6835)
- Use BGT to update UI during reload/restart (#6836)
- Default to JCEF browser (#6834)
- Debug with 2023.2 (#6826)
- Update Java, Gradle, plugins, and clean up (#6825)
- Use EAP to run unit tests (#6822)
- FlutterSdkVersion.version needs to be nullable (#6821)
- Update build for latest EAP (#6820)
- Disable Java indexing in AS canary (#6815)
- add Open in Xcode for macOS (#6791)
- Remove deprecated strong-mode entry in analysis options (#6800)
- Update EAP build (#6797)
- Add JCEF browser (#6787)

# 74
- Support multiple running instance for inspectors (#6772)
- Add Short super.key  (#6757)
- Enable envars for run configs (#6765)
- Save pub root for attach (#6764)
- Build for 2023.2 EAP (#6763)
- Use VM service URI instead of observatory URI for bazel test startup (#6742)
- Reorg CONTRIBUTING.md (#6740)
- Improve run configurations (#6739)
- Allow making the plugin from multiple platforms (#6730)
- Delete `flutter-idea/artifacts` link (#6729)
- Remove use of legacy inspector (#6728)
- Use BGT to update UI for restart/reload (#6727)
- Update versions in build script (#6721)
- Update Dart version for latest EAP build (#6720)
- Fix generation of presubmit.yaml (#6708)
- Add a readme for kokoro (#6707)
- Fix typo in icon file name (#6705)
- Fix presubmit template (#6706)

# 73.1
- Build for Android Studio Hedgehog

# 73
- Prevent NPE when process is stopped while record fields are displayed
- Check lcov files for files with no test coverage (#6692)
- Add FLUTTER_SDK to setup instructions (#6684)
- Fix DevTools opening for bazel workspaces (#6682)
- Eliminate the dependency on artifacts (#6681)
- Update Refresh on BGT (#6679)
- Run unit tests on linux bots (#6675)
- Follow-up on #6500, don't use setExceptionPauseMode() if possible (#6674)
- Run unit tests on Linux (#6669)
- Add the run configuration to make the plugin (#6639)
- Remove some obsolete code (#6667)
- Update on BGT (#6664)
- Update VM service protocol (#6653)
- Use 2023.1 to build (#6651)
- Use 2022.3 for building (#6496)
- Use `Directory.delete` instead of `rm` (#6649)
- Always use `Utf8Codec` for plugin logs (#6648)
- Use `FLUTTER_STORAGE_BASE_URL` for `ArtifactManager` (#6647)
- Always open Android module in new window (#6646)
- View record fields in the debugger (#6638)
- Update CONTRIBUTING.md (#6637)
- Update VM service protocol to 4.2 (#6636)
- Fix debugger and BoundField.getName() (#6630)
- Use setIsolatePauseMode (#6629)
- Update VM service protocol to spec version 4.1 (#6628)

# 72.1
- Eliminate more potentially nested service creations (#6626)
- Create only a single service at a time (#6618)
- Use reflection to find EmulatorSettings in both IDEs (#6625)
- Check version of SDK for forming DevTools URL (#6614)
- Open chrome devtools from JxBrowser (#6615)
- Attempt to fix error email (#6605)
- Fix debugger display of Uint8List elements (#6603)

# 72.0
- Build 2023.1 (#6593)
- Update settings to emphasize global options (#6592)-
- Run update() on BGT (#6556)
- Build AS canary with 2022.3 (#6583)
- Catch UnsatisfiedLinkError for inspector (#6585)
- Stop logging to improve completion times (#6584)
- Allow auto pre-commit test to run prior to git commit (#6557)
- Ignore disposed project in FlutterAppManager (#6554)
- Ignore empty files that the Dart plugin says have errors (#6553)
- Fix creating package project (#6542)

# 71.3
- Fix the "Empty menu item text" problem

# 71.2
- Always show device selector in IntelliJ 2022.3 due to: https://youtrack.jetbrains.com/issue/IDEA-308897/IntelliJ-2022.3-causes-custom-toolbar-widget-to-flash?s=IntelliJ-2022.3-causes-custom-toolbar-widget-to-flash
- Re-enable embedded DevTools

# 71.1
- Tweak device selector code
- Add new project types plugin_ffi and empty (#6433)
- Update device selector in background (#6429)
- Catch exception if default project was disposed (#6401)
- Fix test coverage for monorepo projects (#6391)
- Permit attach in bazel context (#6389)
- Change Container to Placeholder in live templates (#6390)
- Move tests to 2022.2 (#6386)
- Remove some deprecated API uses (#6383)

# 71.0
- Remove the process listener after emulator termination (#6377)
- Remove obsolete code from NPW  (#6374)
- Check for disposed project (#6371)
- Remove platform availability channel warning (#6356)
- Show values of TypedDataList in debugger (#6369)

# 70.0
- Respect embedded emulator settings (#6279)
- Update to JX Browser 7.27 and change linux mode (#6283)
- Guard against JSON file problems (#6273)
- Add missing null check (#6268)
- Check for disposed editor (#6265)
- Log bazel mapping differences from analyzer (#6263)
- Update icon version (#6262)
- Use new flutter.json file location (#6261)
- Delete FlutterBazelSettingsNotificationProvider (#6256)

# 69.0
- Build for canary 221 (#6248)
- Revert "Delete code duplicated from Dart (#6113)" (#6246)
- Update .iml for 221 (#6241)
- Disable reader mode for Flutter plugins (#6238)
- Update the build for 2022.2 EAP (#6224)
- Avoid using canonical path for bazel (#6227)
- Reduce error logging for not-useful errors (#6221)
- Ensure disconnect even if import cancelled (#6220)
- Improve import for monorepo projects (#6217)
- Update Flutter commands on Build and Tools menu to run for all Flutter modules (#6215)
- Change how Open in Xcode determines what to open (#6211)
- Update survey announcement (#6210)

# 68.0
- Use distributed icons for current SDK (#6208)
- Update icons (#6207)
- Enable stable platforms (#6202)
- Use correct code to shut down process (#6201)
- Use canonical paths to map symlinks(#6203)
- Enable Windows platform for Flutter 2.10+ (#6195)
- Escape spaces for VM mapping (#6194)
- Stop relying only on .packages for pub banner (#6188)
- Update icon previews to handle new format (#6186)
- Fix typo in actions (#6180)
- Send timing data as a GA event (#6179)
- Check for project disposal before download (#6173)
- Add default token permissions values + pin dependencies (#6152)
- Show meaningful device names (#6158)
- Specify dart bin path (#6153)
- Update CONTRIBUTING.md (#6146)

# 67.1
- Specify dart bin path (#6153)

# 67.0
- Disable new analytics for M67 (#6142)
- Stop running setup on the bots (#6141)
- Update Dart plugin version (#6139)
- Change setting on EDT (#6137)
- Stop using a deprecated method (#6132)
- Refactor Transport for easier logging while debugging (#6129)
- Fix child lines when folding code (#6128)
- Fix analytics (#6119)
- Fix disposer bug (#6120)
- Remove the 30-char limit for project names (#6121)
- Use devtools script to launch bazel devtools (#6115)
- Report flutter SDK version on startup (#6114)
- Add dart devtools for starting server (#6112)
- Delete code duplicated from Dart (#6113)
- Update web URI mapping version (#6110)
- Work around Kokoro Dart plugin problem (#6109)
- Plugin tool improvements (#6106)
- Fix layout issue (#6105)
- Clean up edit.dart (#6104)
- Add more analytics (#5985)
- Update build for 2022.1 (#6102)
- Enable Dart with multiple modules (#6099)
- Look for a single module by name (#6098)
- Add links to 3P plugin docs (#6090)
- Fix config to load into 2021.3 (#6088)
- Move third-party binaries into third_party (#6087)
- This will allow us to assess the security posture of this repository. (#6047)
- Update CONTRIBUTING.md (#6074)
