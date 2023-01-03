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
