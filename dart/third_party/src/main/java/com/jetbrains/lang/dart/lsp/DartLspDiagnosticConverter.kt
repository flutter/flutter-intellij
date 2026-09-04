/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.util.getOffsetInDocument
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.DartLocalFileInfo
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import org.dartlang.analysis.server.protocol.AnalysisError
import org.dartlang.analysis.server.protocol.DiagnosticMessage
import org.dartlang.analysis.server.protocol.Location
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range

object DartLspDiagnosticConverter {

    /**
     * Converts an LSP [Diagnostic] object received from `textDocument/publishDiagnostics`
     * into a legacy DAS [AnalysisError] protocol object so it can be displayed in the
     * Dart Analysis tool window (`DartProblemsView`) and used by Project View decorators.
     */
    fun convertDiagnosticToAnalysisError(
        project: Project,
        das: DartAnalysisServerService,
        uri: String,
        diagnostic: Diagnostic
    ): AnalysisError {
        val severity = when (diagnostic.severity) {
            DiagnosticSeverity.Error -> "ERROR"
            DiagnosticSeverity.Warning -> "WARNING"
            DiagnosticSeverity.Information, DiagnosticSeverity.Hint -> "INFO"
            else -> "ERROR"
        }
        val codeStr = diagnostic.code?.let { if (it.isLeft) it.left else it.right.toString() }
        val type = when {
            codeStr?.equals("todo", ignoreCase = true) == true -> "TODO"
            severity == "ERROR" -> "COMPILE_TIME_ERROR"
            severity == "WARNING" -> "STATIC_WARNING"
            else -> "HINT"
        }
        val location = resolveLocation(project, das, uri, diagnostic.range)
        val contextMessages = diagnostic.relatedInformation?.mapNotNull { info ->
            val infoLocation = resolveLocation(project, das, info.location.uri, info.location.range)
            DiagnosticMessage(info.message, infoLocation)
        }
        val url = diagnostic.codeDescription?.href

        return AnalysisError(
            severity,
            type,
            location,
            diagnostic.message,
            null,
            codeStr,
            url,
            contextMessages,
            false
        )
    }

    private fun resolveLocation(
        project: Project,
        das: DartAnalysisServerService,
        uri: String,
        range: Range
    ): Location {
        val fileInfo = getDartFileInfo(project, uri)
        val filePath = if (fileInfo is DartLocalFileInfo) fileInfo.filePath else uri
        val (vFile, docOffset, docEnd) = runReadAction {
            val f = fileInfo.findFile()
            val doc = f?.let { FileDocumentManager.getInstance().getDocument(it) }
            if (doc != null) {
                Triple(
                    f,
                    getOffsetInDocument(doc, range.start) ?: 0,
                    getOffsetInDocument(doc, range.end) ?: 0
                )
            } else {
                Triple(f, 0, 0)
            }
        }
        val offset = das.getOriginalOffset(vFile, docOffset)
        val length = (das.getOriginalOffset(vFile, docEnd) - offset).coerceAtLeast(0)
        return Location(
            filePath,
            offset,
            length,
            (range.start.line + 1).coerceAtLeast(1),
            (range.start.character + 1).coerceAtLeast(1),
            (range.end.line + 1).coerceAtLeast(1),
            (range.end.character + 1).coerceAtLeast(1)
        )
    }
}
