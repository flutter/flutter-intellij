/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.dartToolingDaemon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonWebSocketListener
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.lang.dart.util.DartTestUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

class DartToolingDaemonServiceTest : BasePlatformTestCase() {

    private companion object {
        const val INITIALIZATION_TIMEOUT_SECONDS = 5
        const val EXPECTED_REQUEST_COUNT = 4
    }

    override fun setUp() {
        super.setUp()
        DartTestUtils.configureDartSdk(module, myFixture.projectDisposable, true)
        val tempDir = myFixture.tempDirFixture.findOrCreateDir("dtd_test_root")
        ModuleRootModificationUtil.addContentRoot(module, tempDir.path)
    }

    override fun tearDown() {
        try {
            DartToolingDaemonService.getInstance(project).stopService()
        } finally {
            super.tearDown()
        }
    }

    fun testInitializationRequestsAreSentOnWebSocketOpen() {
        val allRequestsAcknowledged = CountDownLatch(EXPECTED_REQUEST_COUNT)
        val sentRequestsById = ConcurrentHashMap<Int, JsonObject>()
        val responsesById = ConcurrentHashMap<Int, JsonObject>()

        val dartToolingDaemonService = DartToolingDaemonService.getInstance(project)
        dartToolingDaemonService.webSocketListener = object : DartToolingDaemonWebSocketListener {
            override fun onWebSocketRequest(id: Int, method: String, text: String) {
                sentRequestsById[id] = JsonParser.parseString(text).asJsonObject
            }

            override fun onWebSocketMessage(text: String) {
                val json = JsonParser.parseString(text).asJsonObject
                val id = json["id"]?.asInt ?: return
                responsesById[id] = json
                allRequestsAcknowledged.countDown()
            }
        }

        // Send an early request before starting the service (while webSocketReady is false)
        val earlyParams = JsonObject().apply { addProperty("streamId", "Service") }
        dartToolingDaemonService.sendRequest("streamListen", earlyParams, includeSecret = true) {}

        dartToolingDaemonService.startService()
        UIUtil.dispatchAllInvocationEvents()
        PlatformTestUtil.waitWithEventsDispatching(
            "Did not receive all $EXPECTED_REQUEST_COUNT acknowledgements within ${INITIALIZATION_TIMEOUT_SECONDS}s",
            { allRequestsAcknowledged.count == 0L },
            INITIALIZATION_TIMEOUT_SECONDS
        )

        val streamListenId = idOfRequest(sentRequestsById, "a streamListen request") {
            it["method"]?.asString == "streamListen"
        }
        val getActiveLocationId =
            idOfRequest(sentRequestsById, "a registerService request for Editor.getActiveLocation") {
                it.isRegisterService("Editor", "getActiveLocation")
            }
        val navigateToCodeId = idOfRequest(sentRequestsById, "a registerService request for Editor.navigateToCode") {
            it.isRegisterService("Editor", "navigateToCode")
        }
        val setWorkspaceRootsId = idOfRequest(sentRequestsById, "a FileSystem.setIDEWorkspaceRoots request") {
            it["method"]?.asString == "FileSystem.setIDEWorkspaceRoots"
        }

        assertSuccessResponse(responsesById, streamListenId)
        assertSuccessResponse(responsesById, getActiveLocationId)
        assertSuccessResponse(responsesById, navigateToCodeId)
        assertSuccessResponse(responsesById, setWorkspaceRootsId)

        assertEquals(
            "Exactly $EXPECTED_REQUEST_COUNT requests should have been sent",
            EXPECTED_REQUEST_COUNT,
            sentRequestsById.size
        )
    }

    private fun idOfRequest(
        sentRequestsById: Map<Int, JsonObject>,
        description: String,
        predicate: (JsonObject) -> Boolean,
    ): Int {
        val entry = sentRequestsById.entries.singleOrNull { (_, request) -> predicate(request) }
        requireNotNull(entry) { "Expected exactly one request matching: $description" }
        return entry.key
    }

    private fun JsonObject.isRegisterService(service: String, method: String): Boolean {
        if (this["method"]?.asString != "registerService") return false
        val params = this["params"]?.asJsonObject ?: return false
        return params["service"]?.asString == service && params["method"]?.asString == method
    }

    private fun assertSuccessResponse(responsesById: Map<Int, JsonObject>, id: Int) {
        val expected = JsonParser.parseString(
            """{"jsonrpc":"2.0","result":{"type":"Success"},"id":$id}"""
        ).asJsonObject
        assertEquals(
            "Unexpected response for request id $id",
            expected,
            responsesById[id]
        )
    }
}
