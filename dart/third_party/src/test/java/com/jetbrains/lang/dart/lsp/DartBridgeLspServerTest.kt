/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.Consumer
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.ResponseListener
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.google.gson.JsonObject
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class DartBridgeLspServerTest : DartCodeInsightFixtureTestCase() {

    private lateinit var bridgeServer: DartBridgeLspServer
    private lateinit var capturedListener: ResponseListener
    private lateinit var mockServer: RemoteAnalysisServerImpl
    private val mockClient = MockLanguageClient()
    private val capturedRequests = CopyOnWriteArrayList<JsonObject>()

    override fun setUp() {
        super.setUp()

        val das = DartAnalysisServerService.getInstance(project)

        val sdk = requireNotNull(com.jetbrains.lang.dart.sdk.DartSdk.getDartSdk(project)) { "Dart SDK not found" }

        // Align mySdkHome and mySdkVersion in DartAnalysisServerService via reflection to bypass re-start check
        val serviceClass = DartAnalysisServerService::class.java
        
        val sdkHomeField = serviceClass.getDeclaredField("mySdkHome").apply { isAccessible = true }
        sdkHomeField.set(das, sdk.homePath)
        
        val dasSdkVersionField = serviceClass.getDeclaredField("mySdkVersion").apply { isAccessible = true }
        dasSdkVersionField.set(das, sdk.version)

        val stubSocket = createStubSocket()
        mockServer = object : RemoteAnalysisServerImpl(stubSocket) {
            override fun addResponseListener(listener: ResponseListener) {
                capturedListener = listener
                super.addResponseListener(listener)
            }
            
            override fun generateUniqueId(): String = "123"

            override fun isSocketOpen(): Boolean = true

            override fun sendRequestToServer(id: String, request: JsonObject) {
                capturedRequests.add(request)
            }

            override fun sendRequestToServer(id: String, request: JsonObject, consumer: Consumer) {
                capturedRequests.add(request)
            }

            override fun server_openUrlRequest(url: String?) {}

            override fun server_showMessageRequest(
                messageType: String?,
                message: String?,
                messageActions: MutableList<MessageAction>?,
                consumer: ShowMessageRequestConsumer?
            ) {}

            override fun lsp_workspaceApplyEdit(
                params: DartLspApplyWorkspaceEditParams?,
                consumer: DartLspWorkspaceApplyEditRequestConsumer?
            ) {}
        }

        das.setServer(mockServer)

        bridgeServer = DartBridgeLspServer(project)
        bridgeServer.connect(mockClient)
    }

    override fun tearDown() {
        try {
            if (::bridgeServer.isInitialized) {
                bridgeServer.stop()
            }
            val das = DartAnalysisServerService.getInstance(project)
            das.setServer(null)
            
            val serviceClass = DartAnalysisServerService::class.java
            val sdkHomeField = serviceClass.getDeclaredField("mySdkHome").apply { isAccessible = true }
            sdkHomeField.set(das, null)
            val dasSdkVersionField = serviceClass.getDeclaredField("mySdkVersion").apply { isAccessible = true }
            dasSdkVersionField.set(das, null)
            
            capturedRequests.clear()
        } finally {
            super.tearDown()
        }
    }

    private fun createStubSocket(): AnalysisServerSocket {
        return object : AnalysisServerSocket {
            override fun getErrorStream(): ByteLineReaderStream? = null
            override fun getRequestSink(): RequestSink? = null
            override fun getResponseStream(): ResponseStream? = null
            override fun isOpen(): Boolean = true
            override fun start() {}
            override fun stop() {}
        }
    }

    fun testForwardRequest() {
        val params = HoverParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        bridgeServer.hover(params)

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        
        assertEquals("123", jsonObject.get("id")?.asString)

        val outerParams = jsonObject.getAsJsonObject("params")
        val lspMessage = outerParams.getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/hover", lspMessage.get("method").asString)
    }

    fun testHandleDasResponse() {
        val params = HoverParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.hover(params)

        // Simulate successful DAS response containing wrapped LSP response
        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "contents": {
                      "kind": "markdown",
                      "value": "Hover Content"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertTrue("Response contents should contain Hover Content", result.contents.toString().contains("Hover Content"))
    }

    fun testDiagnosticServerRequest() {
        val future = bridgeServer.diagnosticServer()

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val outerParams = jsonObject.getAsJsonObject("params")
        val lspMessage = outerParams.getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("dart/diagnosticServer", lspMessage.get("method").asString)
        assertFalse("lspMessage should not have params when null is passed", lspMessage.has("params"))

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "port": 9123
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(9123, result.port)
    }

    fun testForwardNotification() {
        // Simulate a diagnostics notification from DAS
        val notificationJson = """
            {
              "params": {
                "lspMessage": {
                  "jsonrpc": "2.0",
                  "method": "textDocument/publishDiagnostics",
                  "params": {
                    "uri": "file://test.dart",
                    "diagnostics": []
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(notificationJson)

        assertNotNull(mockClient.publishedDiagnostics)
        assertEquals("file://test.dart", mockClient.publishedDiagnostics?.uri)
        assertTrue(mockClient.publishedDiagnostics?.diagnostics?.isEmpty() == true)
    }

    fun testGetFileUriFormatting() {
        val descriptor = DartLspServerDescriptor(project)
        val file = myFixture.configureByText("foo.dart", "void main() {}").virtualFile
        val uri = descriptor.getFileUri(file)
        assertTrue(uri.startsWith("file:///"))
        val pathAfterPrefix = uri.substring("file:///".length)
        if (pathAfterPrefix.length >= 2 && (pathAfterPrefix[1] == ':' || pathAfterPrefix.substring(1).startsWith("%3A"))) {
            assertTrue("Drive letter must be uppercase in: $uri", pathAfterPrefix[0].isUpperCase())
        }
    }

    fun testDocumentHighlightRequest() {
        val params = DocumentHighlightParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.documentHighlight(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/documentHighlight", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": [
                    {"range": {"start": {"line": 0, "character": 4}, "end": {"line": 0, "character": 5}}, "kind": 3},
                    {"range": {"start": {"line": 2, "character": 2}, "end": {"line": 2, "character": 3}}, "kind": 2}
                  ]
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertEquals(2, result.size)
        assertEquals(DocumentHighlightKind.Write, result[0].kind)
        assertEquals(DocumentHighlightKind.Read, result[1].kind)
    }

    fun testClientCapabilities() {
        val lspCaps = JsonObject().apply {
            addProperty("testCap", true)
        }
        mockServer.server_setClientCapabilities(listOf("openUrlRequest"), true, lspCaps)

        val req = requireNotNull(capturedRequests.find { it.get("method")?.asString == "server.setClientCapabilities" }) {
            "A server.setClientCapabilities request should be generated"
        }
        val params = req.getAsJsonObject("params")
        assertEquals(true, params.get("supportsUris").asBoolean)
        val lspCapabilities = params.getAsJsonObject("lspCapabilities")
        assertEquals(true, lspCapabilities.get("testCap").asBoolean)
    }

    fun testPublishDiagnosticsNotification() {
        val testFile = myFixture.addFileToProject(
            "lib/test.dart",
            """
            void main() {
              int x = "string";
            }
            """.trimIndent()
        )
        val fileUri = "file://${testFile.virtualFile.path}"

        val notificationJson = """
            {
              "params": {
                "lspNotification": {
                  "jsonrpc": "2.0",
                  "method": "textDocument/publishDiagnostics",
                  "params": {
                    "uri": "$fileUri",
                    "diagnostics": [
                      {
                        "range": {
                          "start": {"line": 1, "character": 10},
                          "end": {"line": 1, "character": 18}
                        },
                        "severity": 1,
                        "code": "invalid_assignment",
                        "message": "A value of type 'String' can't be assigned to a variable of type 'int'.",
                        "source": "dart"
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(notificationJson)

        assertNotNull(mockClient.publishedDiagnostics)
        assertEquals(fileUri, mockClient.publishedDiagnostics?.uri)
        assertEquals(1, mockClient.publishedDiagnostics?.diagnostics?.size)

        // Also check that DartAnalysisServerService processed the diagnostic
        val errorsHash = DartAnalysisServerService.getInstance(project).getFilePathsWithErrorsHash()
        assertNotSame(0, errorsHash)
    }

    fun testTypeDefinitionRequest() {
        val params = TypeDefinitionParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.typeDefinition(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/typeDefinition", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": [
                    {
                      "targetUri": "file://target.dart",
                      "targetRange": {"start": {"line": 0, "character": 0}, "end": {"line": 10, "character": 0}},
                      "targetSelectionRange": {"start": {"line": 0, "character": 6}, "end": {"line": 0, "character": 9}}
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertTrue(result.isRight)
        assertEquals(1, result.right.size)
        assertEquals("file://target.dart", result.right[0].targetUri)
    }

    // --- Hierarchy ---

    fun testPrepareTypeHierarchyRequest() {
        val params = TypeHierarchyPrepareParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.prepareTypeHierarchy(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/prepareTypeHierarchy", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "Dog",
                          "kind": 5,
                          "uri": "file://test.dart",
                          "range": {"start": {"line": 1, "character": 0}, "end": {"line": 5, "character": 1}},
                          "selectionRange": {"start": {"line": 1, "character": 6}, "end": {"line": 1, "character": 9}}
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Dog", result[0].name)
        assertEquals(SymbolKind.Class, result[0].kind)
        assertEquals("file://test.dart", result[0].uri)
    }

    fun testTypeHierarchySupertypesRequest() {
        val item = TypeHierarchyItem(
            "Dog",
            SymbolKind.Class,
            "file://test.dart",
            Range(Position(1, 0), Position(5, 1)),
            Range(Position(1, 6), Position(1, 9))
        )
        val params = TypeHierarchySupertypesParams().apply {
            this.item = item
        }

        val future = bridgeServer.typeHierarchySupertypes(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("typeHierarchy/supertypes", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "Animal",
                          "kind": 5,
                          "uri": "file://test.dart",
                          "range": {"start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 24}},
                          "selectionRange": {"start": {"line": 0, "character": 15}, "end": {"line": 0, "character": 21}}
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Animal", result[0].name)
    }

    fun testTypeHierarchySubtypesRequest() {
        val item = TypeHierarchyItem(
            "Dog",
            SymbolKind.Class,
            "file://test.dart",
            Range(Position(1, 0), Position(5, 1)),
            Range(Position(1, 6), Position(1, 9))
        )
        val params = TypeHierarchySubtypesParams().apply {
            this.item = item
        }

        val future = bridgeServer.typeHierarchySubtypes(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("typeHierarchy/subtypes", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "Labrador",
                          "kind": 5,
                          "uri": "file://test.dart",
                          "range": {"start": {"line": 7, "character": 0}, "end": {"line": 7, "character": 27}},
                          "selectionRange": {"start": {"line": 7, "character": 6}, "end": {"line": 7, "character": 14}}
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Labrador", result[0].name)
    }

    fun testPrepareCallHierarchyRequest() {
        val params = CallHierarchyPrepareParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.prepareCallHierarchy(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/prepareCallHierarchy", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "bark",
                          "kind": 6,
                          "uri": "file://test.dart",
                          "range": {"start": {"line": 2, "character": 2}, "end": {"line": 4, "character": 3}},
                          "selectionRange": {"start": {"line": 2, "character": 7}, "end": {"line": 2, "character": 11}}
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("bark", result[0].name)
        assertEquals(SymbolKind.Method, result[0].kind)
    }

    fun testCallHierarchyIncomingCallsRequest() {
        val item = CallHierarchyItem().apply {
            name = "bark"
            kind = SymbolKind.Method
            uri = "file://test.dart"
            range = Range(Position(2, 2), Position(4, 3))
            selectionRange = Range(Position(2, 7), Position(2, 11))
        }
        val params = CallHierarchyIncomingCallsParams().apply {
            this.item = item
        }

        val future = bridgeServer.callHierarchyIncomingCalls(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("callHierarchy/incomingCalls", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "from": {
                            "name": "speak",
                            "kind": 6,
                            "uri": "file://test.dart",
                            "range": {"start": {"line": 0, "character": 2}, "end": {"line": 1, "character": 3}},
                            "selectionRange": {"start": {"line": 0, "character": 7}, "end": {"line": 0, "character": 12}}
                          },
                          "fromRanges": [
                            {"start": {"line": 1, "character": 4}, "end": {"line": 1, "character": 10}}
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("speak", result[0].from.name)
        assertEquals(1, result[0].fromRanges.size)
    }

    fun testCallHierarchyOutgoingCallsRequest() {
        val item = CallHierarchyItem().apply {
            name = "bark"
            kind = SymbolKind.Method
            uri = "file://test.dart"
            range = Range(Position(2, 2), Position(4, 3))
            selectionRange = Range(Position(2, 7), Position(2, 11))
        }
        val params = CallHierarchyOutgoingCallsParams().apply {
            this.item = item
        }

        val future = bridgeServer.callHierarchyOutgoingCalls(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("callHierarchy/outgoingCalls", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "to": {
                            "name": "print",
                            "kind": 12,
                            "uri": "file://core.dart",
                            "range": {"start": {"line": 10, "character": 0}, "end": {"line": 12, "character": 1}},
                            "selectionRange": {"start": {"line": 10, "character": 5}, "end": {"line": 10, "character": 10}}
                          },
                          "fromRanges": [
                            {"start": {"line": 3, "character": 4}, "end": {"line": 3, "character": 17}}
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("print", result[0].to.name)
        assertEquals(1, result[0].fromRanges.size)
    }

    fun testReferencesRequest() {
        val params = ReferenceParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
            context = ReferenceContext(true)
        }
        val future = bridgeServer.references(params)
        val jsonObject = requireNotNull(capturedRequests.find {
            it.get("method")?.asString == "lsp.handle"
        }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/references", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": [
                    {
                      "uri": "file:///path/to/file.dart",
                      "range": {
                        "start": {"line": 0, "character": 4},
                        "end": {"line": 0, "character": 10}
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        capturedListener.onResponse(responseJson)
        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("file:///path/to/file.dart", result[0].uri)
        assertEquals(0, result[0].range.start.line)
        assertEquals(4, result[0].range.start.character)
        assertEquals(0, result[0].range.end.line)
        assertEquals(10, result[0].range.end.character)
    }

    fun testIsDartSdkVersionSufficientForLspReferences() {
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("3.14.0-65.0.dev"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("3.15.0"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("4.0.0"))

        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("3.13.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("3.0.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("2.19.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("2.14.0"))
    }

    private class MockLanguageClient : LanguageClient {
        var publishedDiagnostics: PublishDiagnosticsParams? = null

        override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
            publishedDiagnostics = diagnostics
        }

        override fun telemetryEvent(`object`: Any?) {}
        override fun showMessage(messageParams: MessageParams?) {}
        override fun showMessageRequest(requestMessageParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
            return CompletableFuture.completedFuture(null)
        }
        override fun logMessage(messageParams: MessageParams?) {}
    }
}
