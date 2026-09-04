/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.dartToolingDaemon

import com.google.gson.JsonObject
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.dtd.DTDProcess
import com.jetbrains.lang.dart.dtd.DTDProcessListener
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.util.DartTestUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DTDProcessTest : BasePlatformTestCase() {

    private companion object {
        const val STARTUP_TIMEOUT_SECONDS = 5L
        const val RESPONSE_TIMEOUT_SECONDS = 2L
        const val CONCURRENT_REQUESTS_TIMEOUT_SECONDS = 5L
        const val CONCURRENT_REQUESTS_COUNT = 10
    }

    private lateinit var dtdProcess: DTDProcess

    override fun setUp() {
        super.setUp()
        DartTestUtils.configureDartSdk(module, myFixture.projectDisposable, true)
    }

    override fun tearDown() {
        try {
            if (::dtdProcess.isInitialized) {
                dtdProcess.terminate()
            }
        } finally {
            super.tearDown()
        }
    }

    fun testRoundTripRequest() {
        startDtdProcessAndAwaitSocketOpen()

        val responseRef = AtomicReference<JsonObject?>() //AtomicReference is not really necessary
        val responseReceived = CountDownLatch(1)

        val params = JsonObject().apply { addProperty("streamId", "Service") }
        dtdProcess.sendRequest("streamListen", params, includeSecret = true) { response ->
            responseRef.set(response)
            responseReceived.countDown()
        }

        assertTrue(
            "No response from streamListen within ${RESPONSE_TIMEOUT_SECONDS}s",
            responseReceived.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )

        val response = requireNotNull(responseRef.get()) { "Response should not be null" }

        assertNotNull("Response should carry an id", response["id"])

        val result = response["result"]
        assertNotNull("Response should carry a result", result)
        assertTrue("Result should be a JSON object", result.isJsonObject)

        val type = result.asJsonObject["type"]?.asString
        assertEquals("streamListen result type", "Success", type)
    }

    fun testConcurrentRequests() {
        startDtdProcessAndAwaitSocketOpen()

        val n = CONCURRENT_REQUESTS_COUNT
        val streamPrefix = "DTDProcessTest_${UUID.randomUUID()}"
        val responses = ConcurrentHashMap<Int, JsonObject>()
        val allReceived = CountDownLatch(n)

        for (i in 0 until n) {
            val params = JsonObject().apply { addProperty("streamId", "${streamPrefix}_$i") }
            dtdProcess.sendRequest("streamListen", params, includeSecret = true) { response ->
                responses[i] = response
                allReceived.countDown()
            }
        }

        assertTrue(
            "Did not receive all $n responses within ${CONCURRENT_REQUESTS_TIMEOUT_SECONDS}s " +
                    "(received ${n - allReceived.count})",
            allReceived.await(CONCURRENT_REQUESTS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )

        assertEquals("Each request should produce exactly one response", n, responses.size)
        for (i in 0 until n) {
            val response = requireNotNull(responses[i]) { "Missing response for request $i" }

            val result = response["result"]
            assertNotNull("Response $i missing result", result)
            assertTrue("Response $i result should be a JSON object", result.isJsonObject)

            val type = result.asJsonObject["type"]?.asString
            assertEquals("Response $i streamListen result type", "Success", type)
        }
    }

    private fun startDtdProcessAndAwaitSocketOpen() {
        val sdk = DartSdk.getDartSdk(project)
            ?: error("Dart SDK is not configured for the test project")

        dtdProcess = DTDProcess()

        val socketReady = CountDownLatch(1)
        dtdProcess.listener = object : DTDProcessListener {
            override fun onWebSocketOpen() {
                socketReady.countDown()
            }
        }

        dtdProcess.start(sdk)
        assertTrue(
            "WebSocket did not open within ${STARTUP_TIMEOUT_SECONDS}s",
            socketReady.await(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )
    }
}