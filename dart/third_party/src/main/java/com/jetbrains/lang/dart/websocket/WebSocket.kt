/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.websocket

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket as JdkWebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A thin wrapper around the JDK [java.net.http.WebSocket] that originally mirrored the API of the
 * Weberknecht library's WebSocket to ease replacement.
 */
class WebSocket(private val uri: URI) {

    private val jdkWebSocket = AtomicReference<JdkWebSocket?>()
    private val sendLock = Any()

    @Volatile
    var eventHandler: WebSocketEventHandler? = null

    @Throws(WebSocketException::class)
    fun connect() {
        val listener = JdkListener()
        val future = try {
            httpClient.newWebSocketBuilder().buildAsync(uri, listener).toCompletableFuture()
        } catch (e: Exception) {
            throw WebSocketException("Failed to start WebSocket handshake to $uri", e)
        }
        try {
            future.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            throw WebSocketException("WebSocket handshake to $uri failed", e)
        }
    }

    @Throws(WebSocketException::class)
    fun send(text: String) {
        synchronized(sendLock)
        {
            val webSocket = jdkWebSocket.get() ?: throw WebSocketException("WebSocket is not connected")
            val future = webSocket.sendText(text, true).toCompletableFuture()
            try {
                future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                future.cancel(true)
                if (e is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                throw WebSocketException("Failed to send WebSocket message", e)
            }
        }
    }

    @Throws(WebSocketException::class)
    fun close() {
        val webSocket = jdkWebSocket.getAndSet(null) ?: return
        val future = webSocket.sendClose(JdkWebSocket.NORMAL_CLOSURE, "Normal Closure").toCompletableFuture()
        try {
            future.get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            throw WebSocketException("Failed to close WebSocket gracefully", e)
        }
    }

    private inner class JdkListener : JdkWebSocket.Listener {
        private val pendingText = StringBuilder()

        override fun onOpen(webSocket: JdkWebSocket) {
            jdkWebSocket.set(webSocket)
            webSocket.request(Long.MAX_VALUE)
            eventHandler?.onOpen()
        }

        override fun onText(webSocket: JdkWebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            pendingText.append(data)
            if (last) {
                val complete = pendingText.toString()
                pendingText.setLength(0)
                eventHandler?.onMessage(WebSocketMessage(complete))
            }
            return null
        }

        // We could use the statusCode and the reason for logging purposes
        override fun onClose(webSocket: JdkWebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            pendingText.clear()
            jdkWebSocket.compareAndSet(webSocket, null)
            eventHandler?.onClose()
            return null
        }

        override fun onError(webSocket: JdkWebSocket, error: Throwable) {
            pendingText.clear()
            jdkWebSocket.compareAndSet(webSocket, null)
            eventHandler?.onClose()
        }

        override fun onPing(webSocket: JdkWebSocket, message: ByteBuffer): CompletionStage<*>? {
            eventHandler?.onPing()
            return null
        }

        override fun onPong(webSocket: JdkWebSocket, message: ByteBuffer): CompletionStage<*>? {
            eventHandler?.onPong()
            return null
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val SEND_TIMEOUT_SECONDS = 10L
        const val CLOSE_TIMEOUT_SECONDS = 10L
        private val httpClient: HttpClient = HttpClient.newHttpClient()
    }
}