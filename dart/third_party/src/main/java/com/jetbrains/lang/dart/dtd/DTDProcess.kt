/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.dtd

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.util.EventDispatcher
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonConsumer
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonListener
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonRequestHandler
import com.jetbrains.lang.dart.ide.toolingDaemon.createDtdCommandLine
import com.jetbrains.lang.dart.ide.toolingDaemon.isDtdCommandLine
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.websocket.WebSocketEventHandler
import com.jetbrains.lang.dart.websocket.WebSocketException
import com.jetbrains.lang.dart.websocket.WebSocketMessage
import com.jetbrains.lang.dart.websocket.WebSocket
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class DTDProcess {
  companion object {
    private val logger = PluginLogger.createLogger(DTDProcess::class.java)
  }

  private lateinit var osProcessHandler: KillableProcessHandler

  private val consumerMap: MutableMap<String, DartToolingDaemonConsumer> = mutableMapOf()

  private var processRunning = false

  var uri: String? = null
    private set
  private var secret: String? = null

  private val nextRequestId = AtomicInteger()

  private lateinit var webSocket: WebSocket
  var webSocketReady: Boolean = false
    private set
  private val servicesMap: MutableMap<String, DartToolingDaemonRequestHandler> = mutableMapOf()

  private val eventDispatcher: EventDispatcher<DartToolingDaemonListener> =
    EventDispatcher.create(DartToolingDaemonListener::class.java)

  @Volatile var listener: DTDProcessListener? = null

  private fun connectToWebSocket(uri: String) {
    try {
      webSocket = WebSocket(URI(uri))
      webSocket.eventHandler = object : WebSocketEventHandler {
        override fun onClose() {
          listener?.onWebSocketClose()
          listener = null
        }

        override fun onMessage(message: WebSocketMessage) {
          val text = message.text
          handleWebSocketMessage(text)
          listener?.onWebSocketMessage(text)
        }

        override fun onOpen() {
          webSocketReady = true
          listener?.onWebSocketOpen()
        }

        override fun onPing() {}

        override fun onPong() {}
      }
      webSocket.connect()
    } catch (e: Exception) {
      logger.error("Failed to connect to Dart Tooling Daemon, uri: $uri", e)
    }
  }

  // TODO (pq): call from DartToolingDaemonService
  fun addToolingDaemonListener(listener: DartToolingDaemonListener, parentDisposable: Disposable): Unit =
    eventDispatcher.addListener(listener, parentDisposable)

  private fun handleWebSocketMessage(text: String) {
    logger.debug("<-- $text")

    val json: JsonObject = try {
      JsonParser.parseString(text) as JsonObject
    } catch (e: Exception) {
      logger.warn("Failed to parse message, error: ${e.message}, message: $text")
      return
    }

    val method = json["method"]?.asString
    val serviceConsumer = servicesMap[method]
    if (method == "streamNotify") {
      val params = json["params"].asJsonObject
      val streamId = params["streamId"].asString
      eventDispatcher.multicaster.received(streamId, json)
    } else if (serviceConsumer != null) {
      val params = json["params"].asJsonObject
      val id = json["id"].asString
      ApplicationManager.getApplication().executeOnPooledThread {
        val response = serviceConsumer.handleRequest(params)
        sendResponse(id, response.result, response.error)
      }
    } else {
      val id = json["id"].asString
      val consumer = consumerMap.remove(id)
      consumer?.received(json)
    }
  }

  fun start(sdk: DartSdk) {
    val commandLine = createDtdCommandLine(sdk)

    osProcessHandler = object : KillableProcessHandler(commandLine) {
      override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.forMostlySilentProcess()
    }

    osProcessHandler.addProcessListener(OSProcessListener())
    osProcessHandler.startNotify()
  }

  fun terminate() {
    if (::webSocket.isInitialized) {
      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          webSocket.close()
        } catch (e: Exception) {
          logger.warn("Failed to close DTD web socket", e)
        }
      }
    }
    if (::osProcessHandler.isInitialized && !osProcessHandler.isProcessTerminated) {
      ApplicationManager.getApplication().executeOnPooledThread {
        if (!osProcessHandler.isProcessTerminated) {
          osProcessHandler.killProcess()
        }
      }
    }
    listener = null
  }

  private fun onProcessStarted(uri: String?, secret: String?) {
    processRunning = true
    this.uri = uri
    this.secret = secret

    uri?.let {
      connectToWebSocket(it)
    }

    listener?.onProcessStarted(uri)
  }

  private fun sendResponse(id: String, result: JsonObject?, error: JsonObject? = null) {
    if (!webSocketReady) {
      logger.warn("sendResponse(\"$id\", $result) called when the socket is not ready")
      return
    }

    val response = JsonObject()
    response.addProperty("jsonrpc", "2.0")
    response.addProperty("id", id)
    if (error == null) {
      response.add("result", result)
    } else {
      response.add("error", error)
    }

    val responseString = response.toString()
    logger.debug("--> $responseString")
    webSocket.send(responseString)
  }

  @Throws(WebSocketException::class)
  fun sendRequest(method: String, params: JsonObject, includeSecret: Boolean, consumer: DartToolingDaemonConsumer) {
    if (!webSocketReady) {
      logger.warn("sendRequest(\"$method\") called when the socket is not ready")
      return
    }

    val request = JsonObject()
    request.addProperty("jsonrpc", "2.0")
    request.addProperty("method", method)

    val id = nextRequestId.incrementAndGet().toString()
    request.addProperty("id", id)
    secret?.takeIf { includeSecret }?.let { params.addProperty("secret", it) }
    request.add("params", params)

    consumerMap[id] = consumer

    val requestString = request.toString()
    logger.debug("--> $requestString")
    webSocket.send(requestString)
  }

  private inner class OSProcessListener : ProcessListener {

    override fun processTerminated(event: ProcessEvent) {
      processRunning = false
      webSocketReady = false
      uri = null
      logger.info("DTD process terminated, exit code: ${event.exitCode}")
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      logger.debug("DTD output: ${event.text}")

      if (processRunning) return

      // The first line of text is the command issued, which can be ignored.
      val text = event.text.trim().takeUnless { isDtdCommandLine(it) }
        ?: return

      var uri: String? = null
      var secret: String? = null
      try {
        val json = JsonParser.parseString(text) as JsonObject
        val details = json["tooling_daemon_details"].asJsonObject
        uri = details["uri"].asString
        secret = details["trusted_client_secret"].asString
      } catch (e: Exception) {
        logger.warn("Failed to parse DTD init message. Error: ${e.message}. DTD message: $text")
      } finally {
        onProcessStarted(uri, secret)
      }
    }
  }
}

interface DTDProcessListener {
  fun onWebSocketMessage(text: String) {}
  fun onWebSocketClose() {}
  fun onWebSocketOpen() {}
  fun onProcessStarted(uri: String?) {}
}
