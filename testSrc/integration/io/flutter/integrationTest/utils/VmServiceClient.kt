/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A minimal Dart VM service client, so a test can ask the running Flutter app what it is actually showing.
 *
 * Connect to the URI the Flutter plugin prints in the Run/Debug console ("Debug service listening on ws://.../ws";
 * see [vmServiceUriFromConsole]). Works for apps on a device or emulator and for web apps (through DWDS).
 */
class VmServiceClient(wsUri: String) : AutoCloseable {
  private val nextId = AtomicInteger()
  private val pending = ConcurrentHashMap<String, CompletableFuture<JsonObject>>()
  private val webSocket: WebSocket = HttpClient.newHttpClient().newWebSocketBuilder()
    .buildAsync(URI(wsUri), Listener())
    .get(30, TimeUnit.SECONDS)

  /** Calls [method] and returns its `result`; throws if the VM service returns an error. */
  fun call(method: String, params: Map<String, String> = emptyMap()): JsonObject {
    val id = nextId.incrementAndGet().toString()
    val request = JsonObject().apply {
      addProperty("jsonrpc", "2.0")
      addProperty("id", id)
      addProperty("method", method)
      add("params", JsonObject().apply { params.forEach { (k, v) -> addProperty(k, v) } })
    }
    val response = CompletableFuture<JsonObject>()
    pending[id] = response
    webSocket.sendText(request.toString(), true).get(30, TimeUnit.SECONDS)
    val message = response.get(60, TimeUnit.SECONDS)
    message.getAsJsonObject("error")?.let { throw IllegalStateException("VM service $method failed: $it") }
    return message.getAsJsonObject("result")
  }

  /**
   * The app's widget tree as text, from the `ext.flutter.debugDumpApp` service extension. Widgets show their key
   * properties, for example `Text("Flutter Demo Home Page")`, so a test can check what the app displays.
   */
  fun widgetTreeDump(): String {
    val isolates = call("getVM").getAsJsonArray("isolates").map { it.asJsonObject.get("id").asString }
    val errors = mutableListOf<String>()
    for (isolateId in isolates) {
      try {
        val result = call("ext.flutter.debugDumpApp", mapOf("isolateId" to isolateId))
        return result.get("data")?.asString ?: result.toString()
      }
      catch (e: Exception) {
        errors += "$isolateId: ${e.message}" // Not the UI isolate, or the extension isn't registered yet.
      }
    }
    throw IllegalStateException("No isolate answered ext.flutter.debugDumpApp: $errors")
  }

  override fun close() {
    runCatching { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(5, TimeUnit.SECONDS) }
  }

  private inner class Listener : WebSocket.Listener {
    private val buffer = StringBuilder()

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
      buffer.append(data)
      if (last) {
        val message = runCatching { JsonParser.parseString(buffer.toString()).asJsonObject }.getOrNull()
        buffer.setLength(0)
        message?.get("id")?.asString?.let { pending.remove(it)?.complete(message) }
      }
      webSocket.request(1)
      return null
    }
  }
}

/**
 * The VM service URI of the most recently launched app, from the plugin's "Debug service listening on <uri>" console
 * line, or null if no app has printed one yet.
 */
fun vmServiceUriFromConsole(consoleText: String): String? =
  Regex("Debug service listening on (ws://\\S+)").findAll(consoleText).lastOrNull()?.groupValues?.get(1)
