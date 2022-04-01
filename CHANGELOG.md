# 66.0
- Use expandable test field for run args (#6065)
- Ignore scratch files during hot reload on save (#6064)
- Stop using some deprecated methods (#5994)
- Allow directories to be recognized as part of a Flutter project (#6057)
- Drop 2020.3, update Dart plugin for latest EAP (#6059)
- Check for bazel workspace when using VM mapping (#6055)
- Add jxbrowser log file for easier debugging (#6051)
- Notify bazel users to try running iOS apps (#6028)
- Use VM service for mapping breakpoint URIs (#6044)
- Send internal errors to analytics for disconnections (#6005)

# 65.0
- Change .packages file use to package config file (#5993)
- Add Android module when opening any project (#5991)
- Update JxBrowser to 7.22 (#5990)
- Use time zone in survey calculation (#5976)
- Fix return type of createState in macros (#5963)
- Log perf dispose NPEs (#5975)
- Build for 2022.1 EAP (#5973)
- Remove old bazel test file mapping code (#5974)
- Use VM for mapping breakpoint files during app run (#5947)
- Update for 64.1 (#5958)
- Try SIGINT first for all processes (#5950)
- Open AS 2021.x (#5951)
- Build for canary (Dolphin) (#5937)

# 64.1
- Try SIGINT before SIGKILL for processes (#5950)

# 64.0
- Fix modules for working with AS sources (#5913)
- Add analytics to survey notifications (#5912)
- Update AS modules for 212 (#5905)
- Build for 212.5712 (#5894)
- Improve icon preview and completion performance (#5887)
- Rename old load classes (#5884)
- Use IJ 213 for unit tests (#5882)
- Share resources with both modules (#5877)

# 63.0
- Build for IntelliJ 2021.3 and Android Studio canary (#5868)
- Enable downloading Mac M1 version of JxBrowser (#5871)
- Convert to Gradle project with Kotlin DSL (#5858)

# 62.0
- Update jxbrowser to 7.19 (#5783)
- Restore ignored tests (#5826)
- Use Java 11 everywhere (#5836)
- Add AS Canary to dev channel (#5830)
- Remove dependencies on built-in Flutter icons (#5824)
- Add instructions for converting to/from Gradle project (#5822)
- Derive icon previews from font files for built-in icons (#5820)
- Enable more compile-time nulllability checking (#5804)

# 61.1
- Add null check for manager (#5799)

# 61.0
- Make console stack traces expandable (#5777)
- Do hot reload on auto-save (#5774)
- Run flutter pub get after changing SDK in preferences (#5773)
- Allow tests in any marked test dir and optionally in sources (#5770)
- Add the new skeleton template to the New Project Wizard (#5765)
- Bug fix in Workspace.java: parsing of contents out of the json file. (#5768)
- Make unit test name matching more strict (#5763)
- Stop running an external tool before launching (#5759)
- Change some deprecated API usage (#5755)
- Try harder to find pub root (#5753)
- Add --project-name arg to flutter create (#5752)
- Delete the project directory if project validation fails (#5751)
- Build menu should be visible even if no file is selected (#5750)
- Fix broken image resorce link (#5745)
- Convert back to iml-based project (#5741)
- Remove old code from NPW (#5739)
- Fix some problems in the set up docs (#5734)
