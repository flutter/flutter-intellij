---
name: patch-copied-lsp-sources
description: Copy JetBrains LSP sources into the Dart plugin and patch them to prevent conflicts and ensure proper configuration.
---

# Patch Copied LSP Sources

Use this skill whenever JetBrains LSP source files are copied or refreshed from the `intellij-community` repository into the `dart-intellij-third-party` plugin. The skill automates the file copying, packaging them under the isolated `com.intellij.platform.dartlsp` package namespace directly, and applies all necessary adjustments to prevent conflicts with the built-in LSP client of IntelliJ IDEA.

## How to Refresh and Apply

To copy the sources from `intellij-community` and apply the adjustments automatically:
1. Run the Python patch script located in the `scripts` directory from the repository root, passing the path to the local `intellij-community` repository as a positional argument:
   ```bash
   python3 .agents/skills/patch-copied-lsp-sources/scripts/patch.py /path/to/intellij-community
   ```
2. Verify the changes using `git diff`.
3. Verify that the project compiles:
   ```bash
   ./gradlew clean compileKotlin --no-build-cache
   ```

> [!NOTE]
> If you only need to re-apply the patches to the existing files in the workspace (without copying fresh files from `intellij-community`), you can run the script without any arguments:
> ```bash
> python3 .agents/skills/patch-copied-lsp-sources/scripts/patch.py
> ```

## Record of Adjustments Applied

Here is the details of the changes made by the patch script:

1. **Direct Package Copy & Rename**:
   - Copies files directly from `platform/lsp/src/com/intellij/platform/lsp` into `third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp`.
   - Rename package namespace references from `com.intellij.platform.lsp` to `com.intellij.platform.dartlsp` across all `.kt`, `.java`, and `.xml` source files to avoid split-package classloader collisions and wrong-namespace imports.

2. **Registry Key & Timeout Key**:
   - Rename key `lsp.server.connect.timeout` to `dart.lsp.server.connect.timeout` in `dart-lsp-impl.xml` and `Lsp4jServerConnector.kt` to avoid conflicting with IntelliJ IDEA's default LSP settings.

3. **Notification Groups**:
   - Prefix notification group IDs with `Dart: ` in `dart-lsp-impl.xml` and `LspServerNotificationsHandlerImpl.kt` to avoid conflicts with IntelliJ's platform groups.
     - `LSP window/showMessage` -> `Dart: LSP window/showMessage`
     - `LSP window/logMessage: errors, warnings` -> `Dart: LSP window/logMessage: errors, warnings`
     - `LSP window/logMessage: info, log; $/logTrace` -> `Dart: LSP window/logMessage: info, log; $/logTrace`

4. **Extension Element IDs**:
   - Remove optional `id="..."` attributes from all extension definitions in `dart-lsp-impl.xml` (except `<notificationGroup>`), as they are optional and avoid ID collision warnings/conflicts.

5. **Resource Bundle**:
   - Change bundle reference from `messages.LspBundle` to `messages.DartLspBundle` in `dart-lsp-impl.xml` and `LspBundle.kt`.
   - Provide local `DartLspBundle.properties` resource file under `third_party/src/main/resources/messages/` containing the required localization keys.

6. **Log Registry Defaults**:
   - Set `defaultValue="false"` for registry key `lsp.communication.standard.log.file` in `dart-lsp-impl.xml` to avoid spamming the main `idea.log` with LSP logs by default, storing them in session-specific files instead.

7. **Intention Directory Names**:
   - Rename the intention descriptions folder `LspIntention` to `DartLspIntention` and update `descriptionDirectoryName` to `DartLspIntention` in `dart-lsp-impl.xml` to prevent collision.

8. **Support External Library Files**:
   - Remove `if (!ProjectFileIndex.getInstance(project).isInContent(file)) return false` from `LspServerImpl.isSupportedFile(file)` so that the Dart LSP bridge can serve external library files (such as pub-cache packages and Dart SDK libraries like `dart:io`).
