/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

/**
 * Custom Language Server interface for Dart to support custom LSP requests.
 */
interface DartLanguageServer : Lsp4jServer {
    /**
     * Returns the port of the diagnostic server.
     */
    @JsonRequest("dart/diagnosticServer")
    fun diagnosticServer(): CompletableFuture<DiagnosticServerResult>
}

data class DiagnosticServerResult(
    val port: Int
)

object DartLspService {
    @JvmStatic
    fun getDiagnosticServerPort(project: Project): CompletableFuture<Int?> {
        return CompletableFuture.supplyAsync({
            if (project.isDisposed) return@supplyAsync null
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(DartLspServerSupportProvider::class.java)
                .firstOrNull() ?: return@supplyAsync null
            val result = server.sendRequestSync { (it as DartLanguageServer).diagnosticServer() }
            result?.port
        }, AppExecutorUtil.getAppExecutorService())
    }
}
