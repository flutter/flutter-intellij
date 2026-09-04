#!/usr/bin/env python3
import argparse
import os
import re
import shutil
import sys

def main():
    parser = argparse.ArgumentParser(description="Copy and patch JetBrains LSP sources into the Dart plugin.")
    parser.add_argument(
        "intellij_path",
        nargs="?",
        help="Path to the local clone of the 'intellij-community' repository. If provided, sources will be copied first."
    )
    parser.add_argument(
        "--repo-root",
        help="Path to the dart-intellij-third-party repository root (defaults to resolving relative to this script)."
    )
    args = parser.parse_args()

    # Resolve base_dir
    if args.repo_root:
        base_dir = os.path.abspath(args.repo_root)
    else:
        # Default to the repository root relative to this script's location
        script_dir = os.path.dirname(os.path.abspath(__file__))
        repo_root = os.path.abspath(os.path.join(script_dir, "../../../../"))
        if os.path.isdir(os.path.join(repo_root, "third_party")):
            base_dir = repo_root
        else:
            base_dir = os.getcwd()

    if not os.path.isdir(os.path.join(base_dir, "third_party")):
        print(f"Error: {base_dir} does not contain a 'third_party' folder.", file=sys.stderr)
        sys.exit(1)

    # 1. Copy sources if intellij_path is provided
    if args.intellij_path:
        intellij_community_path = os.path.abspath(args.intellij_path)
        print(f"Copying sources from: {intellij_community_path}")

        src_lsp_code = os.path.join(intellij_community_path, "platform/lsp/src/com/intellij/platform/lsp")
        src_lsp_resources = os.path.join(intellij_community_path, "platform/lsp/resources")

        if not os.path.isdir(src_lsp_code) or not os.path.isdir(src_lsp_resources):
            print(f"Error: Could not find LSP sources in {intellij_community_path}.", file=sys.stderr)
            print(f"Expected to find:\n  {src_lsp_code}\n  {src_lsp_resources}", file=sys.stderr)
            sys.exit(1)

        dst_lsp_code = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp")
        dst_lsp_resources = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/resources")

        # Clean existing destination directories
        if os.path.exists(dst_lsp_code):
            shutil.rmtree(dst_lsp_code)
        if os.path.exists(dst_lsp_resources):
            shutil.rmtree(dst_lsp_resources)

        # Copy code directly to com/intellij/platform/dartlsp
        shutil.copytree(src_lsp_code, dst_lsp_code)

        # Copy resources
        shutil.copytree(src_lsp_resources, dst_lsp_resources)

        # Rename intellij.platform.lsp.xml to dart-lsp-impl.xml
        xml_old_path = os.path.join(dst_lsp_resources, "META-INF/intellij.platform.lsp.xml")
        if not os.path.exists(xml_old_path):
            xml_old_path = os.path.join(dst_lsp_resources, "intellij.platform.lsp.xml")

        xml_new_path = os.path.join(dst_lsp_resources, "dart-lsp-impl.xml")
        if os.path.exists(xml_old_path):
            os.rename(xml_old_path, xml_new_path)
            # Clean up META-INF if empty
            meta_inf_dir = os.path.dirname(xml_old_path)
            if os.path.exists(meta_inf_dir) and not os.listdir(meta_inf_dir):
                os.rmdir(meta_inf_dir)

    print(f"Applying JetBrains LSP patches and renames in: {base_dir}")

    # 2. Rename package references in all files under platform-lsp
    walk_dir = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp")
    for root, dirs, files in os.walk(walk_dir):
        for file in files:
            if file.endswith((".kt", ".java", ".xml")):
                file_path = os.path.join(root, file)
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                # Check for the original platform package name
                modified = False
                if "com.intellij.platform.lsp" in content:
                    content = content.replace("com.intellij.platform.lsp", "com.intellij.platform.dartlsp")
                    modified = True
                
                # Also fallback to replacing any residual com.intellij.platform.lsp.dart just in case
                if "com.intellij.platform.lsp.dart" in content:
                    content = content.replace("com.intellij.platform.lsp.dart", "com.intellij.platform.dartlsp")
                    modified = True

                if modified:
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write(content)

    # 3. Modify dart-lsp-impl.xml
    xml_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/resources/dart-lsp-impl.xml")
    if os.path.exists(xml_path):
        with open(xml_path, "r", encoding="utf-8") as f:
            xml_content = f.read()

        xml_content = xml_content.replace(
            'key="lsp.server.connect.timeout"',
            'key="dart.lsp.server.connect.timeout"'
        )
        xml_content = re.sub(
            r'<notificationGroup id="LSP window/',
            r'<notificationGroup id="Dart: LSP window/',
            xml_content
        )
        xml_content = xml_content.replace(
            'bundle="messages.LspBundle"',
            'bundle="messages.DartLspBundle"'
        )
        xml_content = xml_content.replace(
            '<bundleName>messages.LspBundle</bundleName>',
            '<bundleName>messages.DartLspBundle</bundleName>'
        )
        xml_content = xml_content.replace(
            'key="lsp.communication.standard.log.file" defaultValue="true"',
            'key="lsp.communication.standard.log.file" defaultValue="false"'
        )
        xml_content = xml_content.replace(
            '<descriptionDirectoryName>LspIntention</descriptionDirectoryName>',
            '<descriptionDirectoryName>DartLspIntention</descriptionDirectoryName>'
        )

        def remove_ids(match):
            tag_content = match.group(0)
            if tag_content.startswith("<notificationGroup"):
                return tag_content
            return re.sub(r'\s+id="[^"]*"', '', tag_content)

        xml_content = re.sub(r'<[a-zA-Z0-9_\.]+(?:\s+[^>]*?)?\s+id="[^"]*"[^>]*>', remove_ids, xml_content)

        with open(xml_path, "w", encoding="utf-8") as f:
            f.write(xml_content)

    # 4. Modify Lsp4jServerConnector.kt
    connector_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/connector/Lsp4jServerConnector.kt")
    if os.path.exists(connector_path):
        with open(connector_path, "r", encoding="utf-8") as f:
            connector_content = f.read()
        connector_content = connector_content.replace(
            '"lsp.server.connect.timeout"',
            '"dart.lsp.server.connect.timeout"'
        )
        with open(connector_path, "w", encoding="utf-8") as f:
            f.write(connector_content)

    # 5. Modify LspServerNotificationsHandlerImpl.kt
    handler_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/LspServerNotificationsHandlerImpl.kt")
    if os.path.exists(handler_path):
        with open(handler_path, "r", encoding="utf-8") as f:
            handler_content = f.read()
        handler_content = handler_content.replace(
            '"LSP window/showMessage"',
            '"Dart: LSP window/showMessage"'
        )
        handler_content = handler_content.replace(
            '"LSP window/logMessage: errors, warnings"',
            '"Dart: LSP window/logMessage: errors, warnings"'
        )
        handler_content = handler_content.replace(
            '"LSP window/logMessage: info, log; $/logTrace"',
            '"Dart: LSP window/logMessage: info, log; $/logTrace"'
        )
        with open(handler_path, "w", encoding="utf-8") as f:
            f.write(handler_content)

    # 6. Modify LspBundle.kt
    bundle_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/api/LspBundle.kt")
    if os.path.exists(bundle_path):
        with open(bundle_path, "r", encoding="utf-8") as f:
            bundle_content = f.read()
        bundle_content = bundle_content.replace(
            '"messages.LspBundle"',
            '"messages.DartLspBundle"'
        )
        with open(bundle_path, "w", encoding="utf-8") as f:
            f.write(bundle_content)

    # 7. Rename intentionDescriptions directory
    src_intention_dir = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/resources/intentionDescriptions/LspIntention")
    dst_intention_dir = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/resources/intentionDescriptions/DartLspIntention")
    if os.path.exists(src_intention_dir):
        if os.path.exists(dst_intention_dir):
            shutil.rmtree(dst_intention_dir)
        os.rename(src_intention_dir, dst_intention_dir)

    # 8. Write messages/DartLspBundle.properties
    properties_dir = os.path.join(base_dir, "third_party/src/main/resources/messages")
    os.makedirs(properties_dir, exist_ok=True)
    properties_path = os.path.join(properties_dir, "DartLspBundle.properties")

    properties_content = '''notification.group.lsp.message=LSP messages
notification.group.lsp.log.errors.warnings=LSP log: errors, warnings
notification.group.lsp.log.info.trace=LSP log: info, trace

lsp.based.formatter=LSP-based formatter
command.name.lsp.on.type.formatting=On-Type Formatting

intention.group.name=LSP
intention.family.name=Apply code action

code.change.from.server={0}: code change from the LSP server

action.OpenLspErrorOutputAction.text=Open Server Error Output
server.error.output.editor.tab.name={0} Server Error Output
action.StopLspServerAction.text=Stop and Autorun Server as Needed
action.RestartLspServerAction.text=Restart Server

# Example: "Dart: Find References (foo.dart:12:3)"
0.find.references.1={0}: Find References ({1})
follow.link.tooltip=Follow link
progress.title.progress={0}: progress
codeLens.LspCodeVisionProvider.name=LSP Code Lens
codeLens.LspCodeVisionProvider.description=Displays code lens information from LSP servers

lsp.rename.prepare.progress.title=Preparing Rename\\u2026
lsp.rename.progress.title=Renaming\\u2026
lsp.rename.action.text=LSP-Based Rename
'''

    with open(properties_path, "w", encoding="utf-8") as f:
        f.write(properties_content)

    # 9. Modify LspCodeVisionProvider.kt to explicitly override handleExtraAction
    code_vision_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/codeLens/LspCodeVisionProvider.kt")
    if os.path.exists(code_vision_path):
        with open(code_vision_path, "r", encoding="utf-8") as f:
            code_vision_content = f.read()
        if "handleExtraAction" not in code_vision_content:
            code_vision_content = code_vision_content.replace(
                "override val singleEntryPerLine: Boolean = false\n}",
                "override val singleEntryPerLine: Boolean = false\n\n  override fun handleExtraAction(editor: Editor, textRange: TextRange, entry: CodeVisionEntry, actionId: String) {}\n}"
            )
            with open(code_vision_path, "w", encoding="utf-8") as f:
                f.write(code_vision_content)

    # 10. Modify LspServerImpl.kt to remove ProjectFileIndex.isInContent check so external library files (pub-cache, SDK) are supported
    lsp_server_impl_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/LspServerImpl.kt")
    if os.path.exists(lsp_server_impl_path):
        with open(lsp_server_impl_path, "r", encoding="utf-8") as f:
            lsp_server_impl_content = f.read()
        target_line = "    if (!ProjectFileIndex.getInstance(project).isInContent(file)) return false\n"
        import_line = "import com.intellij.openapi.roots.ProjectFileIndex\n"
        modified = False
        if target_line in lsp_server_impl_content:
            lsp_server_impl_content = lsp_server_impl_content.replace(target_line, "")
            modified = True
        if import_line in lsp_server_impl_content:
            lsp_server_impl_content = lsp_server_impl_content.replace(import_line, "")
            modified = True
        if modified:
            with open(lsp_server_impl_path, "w", encoding="utf-8") as f:
                f.write(lsp_server_impl_content)

    print("Patch applied successfully!")

if __name__ == "__main__":
    main()
