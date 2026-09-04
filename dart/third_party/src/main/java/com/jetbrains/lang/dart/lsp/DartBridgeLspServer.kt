/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.dart.server.ResponseListener
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.logging.PluginLogger
import org.dartlang.analysis.server.protocol.AnalysisError
import org.dartlang.analysis.server.protocol.DiagnosticMessage
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * DartBridgeLspServer acts as a lightweight translation bridge between the JetBrains LSP client
 * (which expects a standard LSP server) and the legacy Dart Analysis Server (DAS) running in legacy mode.
 *
 * ## Request Flow
 * When the JetBrains client sends an LSP request (e.g., [hover]), the bridge intercepts it,
 * wraps it in a legacy DAS `lsp.handle` request, and forwards it to the existing DAS instance.
 * When DAS responds, the bridge extracts the nested LSP response and completes the future returned to the client.
 *
 * ## Migration Strategy: Shared vs LSP-Only Handlers
 * DAS contains two sets of LSP handlers:
 * 1. **Shared Handlers** (e.g., Hover, Formatting): These are registered in DAS even when running in legacy mode.
 *    We can forward these requests directly to DAS via `lsp.handle`.
 * 2. **LSP-Only Handlers** (e.g., Go to Definition, Completion): These are NOT registered in legacy DAS.
 *    If we forward them, DAS will return `MethodNotFound`. To support these, the bridge must manually
 *    intercept the LSP request, translate it to a legacy DAS request (e.g., `analysis.getNavigation` for Definition),
 *    and translate the legacy response back to LSP classes.
 *
 * ## Document Synchronization
 * Standard LSP document sync notifications ([didOpen], [didChange], [didClose]) are ignored in this bridge.
 * The legacy Dart plugin already handles robust file content synchronization via `analysis.updateContent`.
 * Since DAS shares the same in-memory overlay filesystem between legacy and LSP handlers, the shared LSP handlers
 * (like Hover) will automatically see the up-to-date content synced by the legacy plugin.
 * To ensure the server is fully up to date before processing a request, we also explicitly call
 * `das.updateFilesContent()` before forwarding any client request.
 */
class DartBridgeLspServer(private val project: Project) : DartLanguageServer, TextDocumentService, WorkspaceService,
    LanguageClientAware {
    companion object {
        private val logger = PluginLogger.createLogger(DartBridgeLspServer::class.java)
        private const val LSP_MESSAGE_KEY = "lspMessage"
        private const val LSP_NOTIFICATION_KEY = "lspNotification"
        private const val LSP_RESPONSE_KEY = "lspResponse"
        private const val JSONRPC_VERSION = "2.0"
        
        // We use a JSON handler with default lsp4j configuration to serialize/deserialize lsp4j objects.
        private val JSON_HANDLER = MessageJsonHandler(mapOf())
        private val GSON: Gson = JSON_HANDLER.gson
    }

    private var client: LanguageClient? = null
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest<*>>()
    private var responseListener: ResponseListener? = null

    private val das: DartAnalysisServerService
        get() = DartAnalysisServerService.getInstance(project)

    init {
        setupDasResponseListener()
    }

    override fun connect(client: LanguageClient) {
        this.client = client
        logger.info("Connected to LanguageClient")
    }

    /**
     * Sets up a listener on the legacy Dart Analysis Server (DAS) to intercept responses 
     * and notifications sent back to requests originating from the client.
     */
    private fun setupDasResponseListener() {
        val listener = ResponseListener { response ->
            // Intercept only those responses that contain LSP payload keys.
            if (!response.contains(LSP_MESSAGE_KEY) && !response.contains(LSP_RESPONSE_KEY) && !response.contains(LSP_NOTIFICATION_KEY)) {
                return@ResponseListener
            }

            try {
                val jsonObject = JsonParser.parseString(response).asJsonObject
                handleDasResponse(jsonObject)
            } catch (e: Exception) {
                logger.error("Error handling DAS response: $response", e)
            }
        }
        this.responseListener = listener
        das.addResponseListener(listener)
    }

    /**
     * Processes raw JSON responses from the Dart Analysis Server.
     * Depending on the payload, it either:
     * 1. Forwards server notifications (e.g. publishDiagnostics) directly to the LSP client.
     * 2. Unwraps successful LSP responses (e.g. hover/completion results) or exceptional 
     *    LSP errors to resolve the matching pending request future.
     */
    private fun handleDasResponse(jsonObject: JsonObject) {
        // Check if it's a notification from DAS.
        if (jsonObject.has("params")) {
            val params = jsonObject.get("params").asJsonObject
            val msgObj = when {
                params.has(LSP_NOTIFICATION_KEY) -> params.get(LSP_NOTIFICATION_KEY).asJsonObject
                params.has(LSP_MESSAGE_KEY) -> params.get(LSP_MESSAGE_KEY).asJsonObject
                else -> null
            }
            if (msgObj != null) {
                val method = msgObj.getAsJsonPrimitive("method")?.asString
                if (method != null) {
                    // Forward notification to client.
                    forwardNotificationToClient(method, msgObj)
                }
                return
            }
        }

        // Check if it's a response to a request.
        val idElement = jsonObject.get("id")
        val topLevelId = if (idElement != null && idElement.isJsonPrimitive) idElement.asString else null
        if (topLevelId != null) {
            val pending = pendingRequests.remove(topLevelId)
            if (pending != null) {
                if (jsonObject.has("result")) {
                    val result = jsonObject.get("result").asJsonObject
                    if (result.has(LSP_RESPONSE_KEY)) {
                        val lspResponseElement = result.get(LSP_RESPONSE_KEY)
                        if (lspResponseElement != null && lspResponseElement.isJsonObject) {
                            val lspResponse = lspResponseElement.asJsonObject
                            if (lspResponse.has("error")) {
                                val error = lspResponse.getAsJsonObject("error")
                                pending.completeExceptionally(error)
                            } else if (lspResponse.has("result")) {
                                val lspResult = lspResponse.get("result")
                                if (lspResult != null && !lspResult.isJsonNull) {
                                    pending.complete(lspResult)
                                } else {
                                    pending.completeWithNull()
                                }
                            } else {
                                pending.completeWithNull()
                            }
                        } else {
                            pending.completeWithNull()
                        }
                    } else {
                        pending.completeWithNull()
                    }
                } else if (jsonObject.has("error")) {
                    val error = jsonObject.get("error").asJsonObject
                    pending.completeExceptionally(error)
                }
            }
        }
    }

    private fun forwardNotificationToClient(method: String, msgObj: JsonObject) {
        val client = this.client ?: return
        try {
            // Parse and forward notifications to the LSP client proxy using lsp4j.
            // Currently, only 'textDocument/publishDiagnostics' is supported and forwarded.
            if (method == "textDocument/publishDiagnostics") {
                val paramsObj = msgObj.get("params")
                val params = GSON.fromJson(paramsObj, PublishDiagnosticsParams::class.java)
                client.publishDiagnostics(params)
                val errors = params.diagnostics?.map {
                    DartLspDiagnosticConverter.convertDiagnosticToAnalysisError(project, das, params.uri, it)
                } ?: emptyList()
                das.onLspDiagnosticsUpdated(params.uri, errors)
            } else {
                logger.info("Ignored notification from DAS: $method")
            }
        } catch (e: Exception) {
            logger.error("Failed to forward notification: $msgObj", e)
        }
    }

    fun stop() {
        responseListener?.let { das.removeResponseListener(it) }
        pendingRequests.forEach { (_, pending) ->
            pending.future.cancel(true)
        }
        pendingRequests.clear()
    }

    // --- LanguageServer Implementation ---

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        logger.info("Initialize called")
        val capabilities = ServerCapabilities().apply {
            setHoverProvider(true)
            setDefinitionProvider(true)
            setTypeDefinitionProvider(true)
            setDocumentHighlightProvider(true)
            setTypeHierarchyProvider(true)
            setCallHierarchyProvider(true)
            setReferencesProvider(true)
            // Add other capabilities as we support them.
        }
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun shutdown(): CompletableFuture<Any> {
        logger.info("Shutdown called")
        stop()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        logger.info("Exit called")
    }

    override fun getTextDocumentService(): TextDocumentService = this
    override fun getWorkspaceService(): WorkspaceService = this

    // --- TextDocumentService Implementation ---

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        return forwardRequest("textDocument/hover", params, Hover::class.java)
    }

    // Note: We advertise linkSupport: true in server.setClientCapabilities (see RequestUtilities.java)
    // so DAS is guaranteed to return List<LocationLink> for textDocument/definition.
    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        val type = object : TypeToken<List<LocationLink>>() {}.type
        return forwardRequest<List<LocationLink>>("textDocument/definition", params, type).thenApply { links ->
            Either.forRight(links ?: emptyList())
        }
    }

    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        val type = object : TypeToken<List<LocationLink>>() {}.type
        return forwardRequest<List<LocationLink>>("textDocument/typeDefinition", params, type).thenApply { links ->
            Either.forRight(links ?: emptyList())
        }
    }

    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
        val type = object : TypeToken<List<DocumentHighlight>>() {}.type
        return forwardRequest<List<DocumentHighlight>>("textDocument/documentHighlight", params, type)
    }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
        val type = object: TypeToken<List<Location>>() {}.type

        return forwardRequest("textDocument/references", params, type)
    }

    override fun diagnosticServer(): CompletableFuture<DiagnosticServerResult> {
        return forwardRequest("dart/diagnosticServer", null, DiagnosticServerResult::class.java)
    }

    // Implement other TextDocumentService methods as needed, returning unsupported or forwarding.
    
    override fun didOpen(params: DidOpenTextDocumentParams) {
        // Ignored. Document synchronization is handled by the legacy DartAnalysisServerService.
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // Ignored. Document synchronization is handled by the legacy DartAnalysisServerService.
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        // Ignored. Document synchronization is handled by the legacy DartAnalysisServerService.
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // Ignored. Document synchronization is handled by the legacy DartAnalysisServerService.
    }

    // --- WorkspaceService Implementation ---

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        // Ignored. Configuration is managed by the legacy plugin settings.
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        // Ignored. File watching is handled by the legacy plugin.
    }

    // --- Type Hierarchy ---

    override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<List<TypeHierarchyItem>> {
        val type = object : TypeToken<List<TypeHierarchyItem>>() {}.type
        return forwardRequest("textDocument/prepareTypeHierarchy", params, type)
    }

    override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        val type = object : TypeToken<List<TypeHierarchyItem>>() {}.type
        return forwardRequest("typeHierarchy/supertypes", params, type)
    }

    override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        val type = object : TypeToken<List<TypeHierarchyItem>>() {}.type
        return forwardRequest("typeHierarchy/subtypes", params, type)
    }

    // --- Call Hierarchy ---

    override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> {
        val type = object : TypeToken<List<CallHierarchyItem>>() {}.type
        return forwardRequest("textDocument/prepareCallHierarchy", params, type)
    }

    override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<List<CallHierarchyIncomingCall>> {
        val type = object : TypeToken<List<CallHierarchyIncomingCall>>() {}.type
        return forwardRequest("callHierarchy/incomingCalls", params, type)
    }

    override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<List<CallHierarchyOutgoingCall>> {
        val type = object : TypeToken<List<CallHierarchyOutgoingCall>>() {}.type
        return forwardRequest("callHierarchy/outgoingCalls", params, type)
    }


    // --- Helper Methods for Forwarding ---

    /**
     * Forwards an LSP request to the legacy DAS.
     *
     * It wraps the LSP request in a legacy DAS `lsp.handle` request.
     * We register a [PendingRequest] associated with the [legacyId] so that when DAS returns
     * the response, we can match it, extract the inner LSP response payload, and complete the future.
     *
     * Note: We use the same [legacyId] for both the outer legacy request and the inner LSP request
     * to simplify tracking and matching.
     */
    private fun <T> forwardRequest(method: String, params: Any?, responseClass: Class<T>): CompletableFuture<T> {
        return forwardRequest(method, params, responseClass as Type)
    }

    private fun <T> forwardRequest(method: String, params: Any?, responseType: Type): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        
        val ready = runReadAction { das.serverReadyForRequest() }
        if (!ready) {
            future.completeExceptionally(ResponseErrorException(ResponseError(-32001, "Dart Analysis Server is not ready", null)))
            return future
        }

        das.updateFilesContent()

        val legacyId = das.generateUniqueId()
        if (legacyId == null) {
            future.completeExceptionally(ResponseErrorException(ResponseError(-32001, "Failed to generate request ID", null)))
            return future
        }
        
        val pending = PendingRequest(future, responseType)
        pendingRequests[legacyId] = pending

        val lspRequest = JsonObject().apply {
            addProperty("jsonrpc", JSONRPC_VERSION)
            addProperty("id", legacyId)
            addProperty("method", method)
            if (params != null && params != Unit) {
                add("params", GSON.toJsonTree(params))
            }
        }

        val legacyRequest = JsonObject().apply {
            addProperty("id", legacyId)
            addProperty("method", "lsp.handle")
            add("params", JsonObject().apply {
                add("lspMessage", lspRequest)
            })
        }

        try {
            das.sendRequest(legacyId, legacyRequest)
        } catch (e: Exception) {
            logger.error("Failed to send request to DAS: $legacyRequest", e)
            pendingRequests.remove(legacyId)
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Forwards an LSP notification to the legacy DAS.
     *
     * It wraps the LSP notification in a legacy DAS `lsp.handle` request.
     * Since LSP notifications do not expect a response, we do not register a future to track it.
     * DAS will still return a dummy legacy response acknowledging the `lsp.handle` request,
     * which we will receive and safely ignore (as no pending request will match its ID).
     */
    private fun forwardNotification(method: String, params: Any) {
        val legacyId = das.generateUniqueId()
        val lspNotification = JsonObject().apply {
            addProperty("jsonrpc", JSONRPC_VERSION)
            addProperty("method", method)
            add("params", GSON.toJsonTree(params))
        }

        val legacyRequest = JsonObject().apply {
            addProperty("id", legacyId)
            addProperty("method", "lsp.handle")
            add("params", JsonObject().apply {
                add("lspMessage", lspNotification)
            })
        }

        try {
            das.sendRequest(legacyId, legacyRequest)
        } catch (e: Exception) {
            logger.error("Failed to send notification to DAS: $legacyRequest", e)
        }
    }

    // Helper class to store pending request info.
    private inner class PendingRequest<T>(val future: CompletableFuture<T>, val responseType: Type) {
        fun complete(resultPayload: JsonElement) {
            try {
                val result = GSON.fromJson<T>(resultPayload, responseType)
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        fun completeWithNull() {
            future.complete(null)
        }

        fun completeExceptionally(error: JsonObject) {
            val code = error.get("code")?.asInt ?: -32603
            val message = error.get("message")?.asString ?: "Unknown error"
            future.completeExceptionally(ResponseErrorException(ResponseError(code, message, null)))
        }
    }
}

