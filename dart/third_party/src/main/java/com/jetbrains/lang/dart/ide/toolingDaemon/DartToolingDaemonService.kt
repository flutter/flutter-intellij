// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.toolingDaemon

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.jetbrains.lang.dart.logging.PluginLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.util.EventDispatcher
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.io.BaseOutputReader
import com.intellij.util.io.URLUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.devtools.DartDevToolsService
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil
import com.jetbrains.lang.dart.websocket.WebSocket
import com.jetbrains.lang.dart.websocket.WebSocketEventHandler
import com.jetbrains.lang.dart.websocket.WebSocketException
import com.jetbrains.lang.dart.websocket.WebSocketMessage
import kotlinx.coroutines.CoroutineScope
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

private data class PendingRequest(
  val method: String,
  val params: JsonObject,
  val includeSecret: Boolean,
  val consumer: DartToolingDaemonConsumer
)

@Service(Service.Level.PROJECT)
class DartToolingDaemonService private constructor(val project: Project, cs: CoroutineScope) : Disposable {

  private lateinit var dtdProcessHandler: KillableProcessHandler
  private var serviceRunning = false

  @Volatile
  var webSocketListener: DartToolingDaemonWebSocketListener? = null

  private lateinit var webSocket: WebSocket
  @Volatile
  var webSocketReady: Boolean = false
    private set

  var uri: String? = null
    private set
  private var secret: String? = null

  private var lastSentRootUris: List<String> = emptyList()

  private val pendingRequests = ConcurrentLinkedQueue<PendingRequest>()

  private val nextRequestId = AtomicInteger()
  private val consumerMap: MutableMap<Int, DartToolingDaemonConsumer> = mutableMapOf()
  private val servicesMap: MutableMap<String, DartToolingDaemonRequestHandler> = mutableMapOf()

  private val eventDispatcher: EventDispatcher<DartToolingDaemonListener> = EventDispatcher.create(DartToolingDaemonListener::class.java)

  private var activeLocationChangeEventSupported: Boolean = false

  init {
    DartActiveLocationChangeHandler(this, cs)
  }

  @Throws(ExecutionException::class)
  fun startService() {
    if (serviceRunning) {
      logger.error("Ignoring startService() call. Dart Tooling Daemon is already running.")
      return
    }

    val sdk = DartSdk.getDartSdk(project)?.takeIf { isDartSdkVersionSufficient(it) } ?: return

    activeLocationChangeEventSupported = DartAnalysisServerService.isDartSdkVersionSufficientForWorkspaceApplyEdits(sdk.version)

    val commandLine = createDtdCommandLine(sdk)

    logger.info("Starting Dart Tooling Daemon, sdk ${sdk.version}")
    dtdProcessHandler = object : KillableProcessHandler(commandLine) {
      override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.forMostlySilentProcess()
    }
    dtdProcessHandler.addProcessListener(DtdProcessListener())
    dtdProcessHandler.startNotify()
  }

  private fun onServiceStarted(uri: String?, secret: String?) {
    serviceRunning = true
    this.uri = uri
    this.secret = secret
    DartDevToolsService.getInstance(project).startService(uri)
    uri?.let {
      connectToDtdWebSocket(it)
      DartAnalysisServerService.getInstance(project).connectToDtd(uri)
    }
    DtdEditorService(project, this).setUpService()
  }

  private fun connectToDtdWebSocket(uri: String) {
    try {
      webSocket = WebSocket(URI(uri))
      webSocket.eventHandler = DtdWebSocketEventHandler()
      webSocket.connect()
    }
    catch (e: Exception) {
      logger.error("Failed to connect to Dart Tooling Daemon, uri: $uri", e)
    }
  }

  fun stopService() {
    val pendingShutdownTasks = mutableListOf<Future<*>>()
    if (::dtdProcessHandler.isInitialized && !dtdProcessHandler.isProcessTerminated) {
      pendingShutdownTasks += ApplicationManager.getApplication().executeOnPooledThread {
        if (!dtdProcessHandler.isProcessTerminated) {
          dtdProcessHandler.killProcess()
        }
      }
    }
    if (::webSocket.isInitialized) {
      pendingShutdownTasks += ApplicationManager.getApplication().executeOnPooledThread {
        try {
          webSocket.close()
        } catch (e: Exception) {
          logger.warn("Failed to close DTD web socket", e)
        }
      }
    }
    pendingShutdownTasks.forEach { it.get() }
    webSocketListener = null
    serviceRunning = false
    webSocketReady = false
    uri = null
    secret = null
    lastSentRootUris = emptyList()
    pendingRequests.clear()
    consumerMap.clear()
    servicesMap.clear()
  }

  @Suppress("unused") // for the Flutter plugin
  fun registerServiceMethod(service: String, method: String, capabilities: JsonObject, consumer: DartToolingDaemonRequestHandler) {
    val params = JsonObject()
    params.addProperty("service", service)
    params.addProperty("method", method)
    params.add("capabilities", capabilities)
    sendRequest("registerService", params, false) { response ->
      val result = response.getAsJsonObject("result")
      if (result == null) {
        logger.error("No result from attempt to register service $service.$method")
        return@sendRequest
      }

      val type = result.getAsJsonPrimitive("type")
      if (type == null || "Success" != type.asString) {
        logger.error("Failed to register service $service.$method")
        return@sendRequest
      }

      servicesMap["$service.$method"] = consumer
    }
  }

  @Throws(WebSocketException::class)
  fun sendRequest(method: String, params: JsonObject, includeSecret: Boolean, consumer: DartToolingDaemonConsumer) {
    if (!webSocketReady || !pendingRequests.isEmpty()) {
      logger.debug("sendRequest(\"$method\") queued because the socket is not ready or there are pending requests")
      pendingRequests.add(PendingRequest(method, params, includeSecret, consumer))
      if (webSocketReady) {
        drainPendingRequests()
      }
      return
    }

    doSendRequest(method, params, includeSecret, consumer)
  }

  private fun drainPendingRequests() {
    while (webSocketReady) {
      val pending = pendingRequests.poll() ?: break
      try {
        doSendRequest(pending.method, pending.params, pending.includeSecret, pending.consumer)
      }
      catch (e: Exception) {
        logger.warn("Failed to send queued DTD request for ${pending.method}", e)
      }
    }
  }

  private fun doSendRequest(method: String, params: JsonObject, includeSecret: Boolean, consumer: DartToolingDaemonConsumer) {
    val request = JsonObject()
    request.addProperty("jsonrpc", "2.0")
    request.addProperty("method", method)

    val id = nextRequestId.incrementAndGet()
    request.addProperty("id", id)
    secret?.takeIf { includeSecret }?.let { params.addProperty("secret", it) }
    request.add("params", params)

    consumerMap[id] = consumer

    val requestString = request.toString()
    webSocketListener?.onWebSocketRequest(id, method, requestString)
    logger.debug("--> $requestString")
    webSocket.send(requestString)
  }

  private fun sendResponse(id: Int, result: JsonObject?, error: JsonObject? = null) {
    if (!webSocketReady) {
      logger.warn("sendResponse(\"$id\", $result) called when the socket is not ready")
      return
    }

    val response = JsonObject()
    response.addProperty("jsonrpc", "2.0")
    response.addProperty("id", id)
    if (error == null) {
      response.add("result", result)
    }
    else {
      response.add("error", error)
    }

    val responseString = response.toString()
    logger.debug("--> $responseString")
    webSocket.send(responseString)
  }

  @Suppress("unused") // for the Flutter plugin
  fun addToolingDaemonListener(listener: DartToolingDaemonListener, parentDisposable: Disposable): Unit =
    eventDispatcher.addListener(listener, parentDisposable)

  fun ensureRootsUpToDate() {
    if (!webSocketReady) return

    ReadAction
      .nonBlocking(Callable { calcRootUris() })
      .coalesceBy(this)
      .expireWith(this)
      .finishOnUiThread(ModalityState.nonModal()) { rootUris: List<String> ->
        if (!webSocketReady) return@finishOnUiThread
        if (lastSentRootUris == rootUris) return@finishOnUiThread

        // https://pub.dev/documentation/dtd/latest/dtd/DTDConnection/setIDEWorkspaceRoots.html
        val params = JsonObject()
        val rootUrisArray = JsonArray()
        rootUris.forEach { rootUrisArray.add(it) }
        params.add("roots", rootUrisArray)
        sendRequest("FileSystem.setIDEWorkspaceRoots", params, true) {}

        lastSentRootUris = rootUris
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  /**
   * A simplified version of [com.jetbrains.lang.dart.analyzer.DartServerRootsHandler.calcIncludedAndExcludedDartRootPaths].
   */
  private fun calcRootUris(): List<String> {
    DartSdk.getDartSdk(project) ?: return emptyList()

    val rootUris: MutableList<String> = mutableListOf()
    for (module in DartSdkLibUtil.getModulesWithDartSdkEnabled(project)) {
      for (contentEntry in ModuleRootManager.getInstance(module).contentEntries) {
        val contentEntryUrl = contentEntry.url
        if (contentEntryUrl.startsWith(URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR)) {
          val rootPath = VfsUtilCore.urlToPath(contentEntryUrl)
          val rootUri = getLocalFileUri(rootPath)
          rootUris.add(rootUri)
        }
      }
    }

    return rootUris
  }

  override fun dispose() {
    stopService()
  }

  private fun isDartSdkVersionSufficient(sdk: DartSdk): Boolean =
    StringUtil.compareVersionNumbers(sdk.version, MIN_SDK_VERSION) >= 0

  fun getFileUri(file: VirtualFile): String = getLocalFileUri(file.path)

  /**
   * URIs are constructed in the same way as in [DartAnalysisServerService.getLocalFileUri]
   * but without falling back to plain OS-dependent paths for older SDK versions.
   */
  private fun getLocalFileUri(localFilePath: String): String {
    val escapedPath = URLUtil.encodePath(PathUtil.toSystemIndependentName(localFilePath))
    val url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, escapedPath)
    val uri = VfsUtil.toUri(url)
    return uri?.toString() ?: url
  }


  private inner class DtdProcessListener : ProcessListener {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      logger.debug("DTD output: ${event.text}")
      if (serviceRunning) return

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
      }
      catch (e: Exception) {
        logger.warn("Failed to parse DTD init message. Error: ${e.message}. DTD message: $text")
      }
      finally {
        onServiceStarted(uri, secret)
      }
    }

    override fun processTerminated(event: ProcessEvent) {
      serviceRunning = false
      webSocketReady = false
      uri = null
      pendingRequests.clear()
      logger.info("DTD terminated, exit code: ${event.exitCode}")
    }
  }

  private inner class DtdWebSocketEventHandler : WebSocketEventHandler {
    override fun onOpen() {
      logger.info("Connected to DTD successfully")
      webSocketReady = true
      ensureRootsUpToDate()
      drainPendingRequests()

      // Fake request to make sure the tooling daemon works
      //val params = JsonObject()
      //params.addProperty("streamId", "foo_stream")
      //sendRequest("streamListen", params) { response ->
      //  println("received response from streamListen")
      //  println(response)
      //}

      // Example request to test registering a service method
      /*
      registerServiceMethod("Test", "testMethod", JsonObject()) { requestParams ->
        println(requestParams)
        val params = JsonObject()
        params.addProperty("success", true)
        DartToolingDaemonResponse(params, null)
      }

      // Try using the service method
      val methodParams = JsonObject()
      methodParams.addProperty("param1", "1")
      sendRequest("Test.testMethod", methodParams, false) { response ->
        println(response)
      }

      registerServiceMethod("Test", "testErrorMethod", JsonObject()) { requestParams ->
        println(requestParams)
        val params = JsonObject()
        params.addProperty("code", 144)
        params.addProperty("message", "This is an error")
        DartToolingDaemonResponse(null, params)
      }

      // Try using the service method
      val methodParams2 = JsonObject()
      methodParams2.addProperty("param1", "1")
      sendRequest("Test.testErrorMethod", methodParams2, false) { response ->
        println(response)
      }
      */
    }

    override fun onMessage(message: WebSocketMessage) {
      val text = message.text
      logger.debug("<-- $text")
      webSocketListener?.onWebSocketMessage(text)

      val json: JsonObject = try {
        JsonParser.parseString(text) as JsonObject
      }
      catch (e: Exception) {
        logger.warn("Failed to parse message, error: ${e.message}, message: $text")
        return
      }

      val method = json["method"]?.asString
      val serviceConsumer = servicesMap[method]
      if (method == "streamNotify") {
        val params = json["params"].asJsonObject
        val streamId = params["streamId"].asString
        eventDispatcher.multicaster.received(streamId, json)
      }
      else if (serviceConsumer != null) {
        val params = json["params"]?.asJsonObject ?: JsonObject()
        val id = json["id"].asInt
        ApplicationManager.getApplication().executeOnPooledThread {
          val response = serviceConsumer.handleRequest(params)
          sendResponse(id, response.result, response.error)
        }
      }
      else {
        val id = json["id"].asInt
        val consumer = consumerMap.remove(id)
        consumer?.received(json)
      }
    }

    override fun onPing() {}
    override fun onPong() {}

    override fun onClose() {
      webSocketReady = false
      secret = null
      lastSentRootUris = emptyList()
      pendingRequests.clear()
    }
  }


  companion object {
    @JvmStatic
    fun getInstance(project: Project): DartToolingDaemonService = project.service()

    private const val MIN_SDK_VERSION: String = "3.4"
    private val logger = PluginLogger.createLogger(DartToolingDaemonService::class.java)
  }
}

// This is a  test only listener.
// In case it's needed to be used in production, use publish-subscribe pattern just like the eventDispatcher
interface DartToolingDaemonWebSocketListener {
  fun onWebSocketMessage(text: String) {}
  fun onWebSocketRequest(id: Int, method: String, text: String) {}
}
