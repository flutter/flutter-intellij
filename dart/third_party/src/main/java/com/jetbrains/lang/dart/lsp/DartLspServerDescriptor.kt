/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspCommunicationChannel
import com.intellij.platform.dartlsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.dartlsp.api.customization.LspCallHierarchyCustomizer
import com.intellij.platform.dartlsp.api.customization.LspCallHierarchyDisabled
import com.intellij.platform.dartlsp.api.customization.LspCallHierarchySupport
import com.intellij.platform.dartlsp.api.customization.LspCodeActionsDisabled
import com.intellij.platform.dartlsp.api.customization.LspCodeLensDisabled
import com.intellij.platform.dartlsp.api.customization.LspCommandsDisabled
import com.intellij.platform.dartlsp.api.customization.LspCompletionDisabled
import com.intellij.platform.dartlsp.api.customization.LspCustomization
import com.intellij.platform.dartlsp.api.customization.LspDiagnosticsDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentColorDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentHighlightsCustomizer
import com.intellij.platform.dartlsp.api.customization.LspDocumentHighlightsDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentHighlightsSupport
import com.intellij.platform.dartlsp.api.customization.LspDocumentLinkDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentSymbolDisabled
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesCustomizer
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesDisabled
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesSupport
import com.intellij.platform.dartlsp.api.customization.LspFoldingRangeDisabled
import com.intellij.platform.dartlsp.api.customization.LspFormattingDisabled
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionCustomizer
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionSupport
import com.intellij.platform.dartlsp.api.customization.LspGoToTypeDefinitionSupport
import com.intellij.platform.dartlsp.api.customization.LspHoverSupport
import com.intellij.platform.dartlsp.api.customization.LspInlayHintDisabled
import com.intellij.platform.dartlsp.api.customization.LspOptimizeImportsDisabled
import com.intellij.platform.dartlsp.api.customization.LspRenameDisabled
import com.intellij.platform.dartlsp.api.customization.LspSelectionRangeDisabled
import com.intellij.platform.dartlsp.api.customization.LspSemanticTokensDisabled
import com.intellij.platform.dartlsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.dartlsp.api.customization.LspTypeHierarchyCustomizer
import com.intellij.platform.dartlsp.api.customization.LspTypeHierarchyDisabled
import com.intellij.platform.dartlsp.api.customization.LspTypeHierarchySupport
import com.intellij.platform.dartlsp.api.customization.LspWorkspaceSymbolDisabled
import com.intellij.psi.PsiFile
import com.intellij.util.io.URLUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartConfigurable

/**
 * Configuration descriptor that defines how the JetBrains LSP client communicates with the Dart Bridge server.
 *
 * This descriptor specifies:
 * 1. Which files are supported (only `.dart` files).
 * 2. The communication channel to use (a TCP Socket channel using the dynamically allocated port of [DartBridgeLspServerManager]).
 *    `startProcess = false` tells the platform that the server is already running internally, so it only needs to connect.
 * 3. The LSP feature customizations (e.g. enabling/disabling hover support dynamically based on settings).
 */
class DartLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Dart (Bridge)") {

    override val lsp4jServerClass: Class<out Lsp4jServer> = DartLanguageServer::class.java

    override fun isSupportedFile(file: VirtualFile): Boolean {
        return DartAnalysisServerService.isFileNameRespectedByAnalysisServer(file.name)
    }

    /**
     * Re-implements [LspServerDescriptor.getFileUri] to preserve uppercase Windows drive letters (`C:`).
     *
     * By default, the underlying JetBrains [LspServerDescriptor.getFileUri] lowercases drive letters (`c%3A`)
     * to match VS Code conventions. However, legacy server messages sent by the Dart plugin (such as file
     * synchronizations via `analysis.updateContent`) use uppercase drive letters derived from IntelliJ VFS paths.
     * Because the Analysis Server evaluates path strings case-sensitively, a casing discrepancy between legacy
     * and LSP-over-legacy requests causes the server to treat the same file as two distinct contexts, leading
     * to duplicate analysis errors and sticky markers (see dart-lang/sdk#63819).
     */
    override fun getFileUri(file: VirtualFile): String {
        val escapedPath = URLUtil.encodePath(getFilePath(file))
        val url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, escapedPath)
        val uri = VfsUtil.toUri(url)?.toString() ?: url
        val prefix = "file:///"
        if (uri.startsWith(prefix) && OSAgnosticPathUtil.startsWithWindowsDrive(uri.substring(prefix.length))) {
            return prefix + uri[prefix.length].uppercase() + uri.substring(prefix.length + 1)
        }
        return uri
    }

    override val lspCommunicationChannel: LspCommunicationChannel
        get() {
            val manager = project.getService(DartBridgeLspServerManager::class.java)
            val port = manager.port
            // The JetBrains LSP framework calls this getter to determine how to connect to the server.
            // We return a Socket channel pointing to the random port allocated by our Bridge Manager.
            // 'startProcess = false' tells the platform that the server process is already running 
            // (managed by DartBridgeLspServerManager) and it should only establish a socket connection.
            if (port == -1) {
                return LspCommunicationChannel.Socket(0, startProcess = false)
            }
            return LspCommunicationChannel.Socket(port, startProcess = false)
        }

    override val lspCustomization: LspCustomization = object : LspCustomization() {
        override val hoverCustomizer = LspHoverSupport()
        
        override val goToDefinitionCustomizer: LspGoToDefinitionCustomizer
            get() = if (DartAnalysisServerService.isLspNavigationEnabled(project)) {
                LspGoToDefinitionSupport()
            } else {
                LspGoToDefinitionDisabled
            }
        override val goToTypeDefinitionCustomizer = LspGoToTypeDefinitionSupport()
        override val completionCustomizer = LspCompletionDisabled
        override val semanticTokensCustomizer = LspSemanticTokensDisabled
        override val diagnosticsCustomizer = LspDiagnosticsDisabled
        override val codeActionsCustomizer = LspCodeActionsDisabled
        override val commandsCustomizer = LspCommandsDisabled
        override val formattingCustomizer = LspFormattingDisabled
        override val findReferencesCustomizer: LspFindReferencesCustomizer
            get() = if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
                LspFindReferencesSupport()
            } else {
                LspFindReferencesDisabled
            }
        override val optimizeImportsCustomizer = LspOptimizeImportsDisabled
        override val documentColorCustomizer = LspDocumentColorDisabled
        override val documentLinkCustomizer = LspDocumentLinkDisabled
        override val foldingRangeCustomizer = LspFoldingRangeDisabled
        override val inlayHintCustomizer = LspInlayHintDisabled
        override val documentHighlightsCustomizer: LspDocumentHighlightsCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                object : LspDocumentHighlightsSupport() {
                    // The default implementation only serves plain-text/TextMate files.
                    override fun shouldAskServerForDocumentHighlights(psiFile: PsiFile): Boolean = true
                }
            } else {
                LspDocumentHighlightsDisabled
            }
        override val signatureHelpCustomizer = LspSignatureHelpDisabled
        override val documentSymbolCustomizer = LspDocumentSymbolDisabled
        override val workspaceSymbolCustomizer = LspWorkspaceSymbolDisabled
        override val callHierarchyCustomizer: LspCallHierarchyCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                LspCallHierarchySupport()
            } else {
                LspCallHierarchyDisabled
            }
        override val typeHierarchyCustomizer: LspTypeHierarchyCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                LspTypeHierarchySupport()
            } else {
                LspTypeHierarchyDisabled
            }

        override val selectionRangeCustomizer = LspSelectionRangeDisabled
        override val codeLensCustomizer = LspCodeLensDisabled
        override val renameCustomizer = LspRenameDisabled
    }
}
