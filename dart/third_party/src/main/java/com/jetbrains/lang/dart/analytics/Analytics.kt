/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.analytics

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.CommonBundle
import com.intellij.execution.Executor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.extensions.PluginId
import com.jetbrains.lang.dart.dtd.DTDProcess
import com.jetbrains.lang.dart.dtd.DTDProcessListener
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.util.PrintingLogger
import com.jetbrains.lang.dart.websocket.WebSocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/// Sends logging to the console.
private const val DEBUGGING_LOCALLY = false
private const val DAS_NOTIFICATION_GROUP = "Dart Analysis Server"

private val DEFAULT_RESPONSE_TIMEOUT = 1.seconds

private const val DART_PLUGIN_ID = "Dart"

private object UnifiedAnalytics {
  object Property {
    const val EVENT = "event"
    const val EVENT_DATA = "eventData"
    const val EVENT_NAME = "eventName"
    const val RESULT = "result"
    const val TOOL = "tool"
    const val VALUE = "value"
  }

  /** Environment variables used by the unified analytics package. */
  object Env {
    // For more context on these environment variables, see https://github.com/Dart-Code/Dart-Code/issues/5561.
    // Environment variable used to specify the IDE name, e.g. IntelliJ IDEA.
    const val IDE_NAME = "DASH__IDE_NAME"
    // Environment variable used to specify the IDE platform version.
    const val IDE_VERSION = "DASH__IDE_VERSION"
    // Environment variable used to specify the plugin name, e.g. Dart.
    const val PLUGIN_NAME = "DASH__PLUGIN_NAME"
    // Environment variable used to specify the plugin version.
    const val PLUGIN_VERSION = "DASH__PLUGIN_VERSION"
    // Environment variable used to suppress analytics.
    const val SUPPRESS_ANALYTICS = "DASH__SUPPRESS_ANALYTICS"
    // Environment variable used to specify the top-level tool.
    const val TOOL = "DASH__TOOL"
  }

  private val logger: Logger =
    if (DEBUGGING_LOCALLY) PrintingLogger.SYSTEM_OUT else PluginLogger.createLogger(UnifiedAnalytics::class.java)

  /// Service name for the DTD-hosted unified analytics service.
  const val SERVICE_NAME = "UnifiedAnalytics"

  /// Service method name for the method that determines whether the unified
  /// analytics client should display the consent message.
  const val SHOULD_SHOW_MESSAGE = "shouldShowMessage"

  /// Service method name for the method that returns the unified analytics
  /// consent message to prompt users with.
  const val GET_CONSENT_MESSAGE = "getConsentMessage"

  /// Service method name for the method that confirms that a unified analytics
  /// client showed the required consent message.
  const val CLIENT_SHOWED_MESSAGE = "clientShowedMessage"

  /// Service method name for the method that sends an event to unified
  /// analytics.
  const val SEND = "send"

  /// Service method name for the method that returns whether unified analytics
  /// telemetry is enabled.
  const val TELEMETRY_ENABLED = "telemetryEnabled"

  fun callServiceWithJsonResponse(
    dtdProcess: DTDProcess,
    name: String,
    timeout: Duration = DEFAULT_RESPONSE_TIMEOUT
  ): JsonElement? {
    val params = JsonObject()
    params.addProperty(Property.TOOL, getToolName())
    var value: JsonElement? = null
    try {
      val latch = CountDownLatch(1)
      dtdProcess.sendRequest(
        "$SERVICE_NAME.$name",
        params,
        true
      ) { response ->
        logger.debug("$SERVICE_NAME.$name.received: ")
        val result = response[Property.RESULT]
        if (result is JsonObject) {
          value = result[Property.VALUE]
        }
        logger.debug("\t$response")
        latch.countDown()
      }

      val completed = latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      if (!completed) {
        throw TimeoutException("Call to $SERVICE_NAME.$name timed out after $DEFAULT_RESPONSE_TIMEOUT.")
      }

    } catch (e: WebSocketException) {
      logger.error(e)
    }
    return value
  }

  fun callServiceWithNoResponse(dtdProcess: DTDProcess, name: String) {
    callServiceWithJsonResponse(dtdProcess, name)
  }

  fun callServiceWithStringResponse(dtdProcess: DTDProcess, name: String): String? =
    callServiceWithJsonResponse(dtdProcess, name)?.asString

  fun callServiceWithBoolResponse(dtdProcess: DTDProcess, name: String): Boolean =
    callServiceWithJsonResponse(dtdProcess, name)?.asBoolean ?: false
}

class AnalyticsConfiguration {
  var shouldShowMessage: Boolean = false
    internal set
  var consentMessage: String? = null
    internal set
  var telemetryEnabled: Boolean = false
    internal set

  val suppressAnalytics: Boolean
    get() = shouldShowMessage || !telemetryEnabled
}

private object AnalyticsConfigurationManager {
  private const val INITIALIZATION_TIMEOUT_IN_MS: Long = 700
  lateinit var data: AnalyticsConfiguration
  private val initLatch = CountDownLatch(1)
  
  val safeData: AnalyticsConfiguration?
    get() = if (::data.isInitialized) data else null

  @Volatile
  private var isInitializing = false

  fun getConfiguration(sdk: DartSdk, project: Project, logger: Logger): AnalyticsConfiguration {
    logger.debug("Analytics.getConfiguration")

    if (::data.isInitialized && initLatch.count == 0L) return data

    // TODO (pq): capture timing info and report (if analytics are enabled)
    var shouldInitialize = false
    synchronized(this) {
      if (!isInitializing) {
        isInitializing = true
        shouldInitialize = true
      }
    }

    if (shouldInitialize) {
      data = AnalyticsConfiguration()

      // Return a default configuration (that suppresses analytics) when running tests.
      if (ApplicationManager.getApplication().isUnitTestMode) {
        initLatch.countDown()
        return data
      }

      val dtdProcess = DTDProcess()
      dtdProcess.listener = object : DTDProcessListener {
        override fun onProcessStarted(uri: String?) {
          logger.debug("DartAnalysisServerService.onProcessStarted")

          val params = JsonObject()
          params.addProperty(UnifiedAnalytics.Property.TOOL, getToolName())

          try {
            data.shouldShowMessage =
              UnifiedAnalytics.callServiceWithBoolResponse(dtdProcess, UnifiedAnalytics.SHOULD_SHOW_MESSAGE)
            if (data.shouldShowMessage) {
              data.consentMessage =
                UnifiedAnalytics.callServiceWithStringResponse(dtdProcess, UnifiedAnalytics.GET_CONSENT_MESSAGE)
              data.telemetryEnabled = false // No need to ask
            } else {
              data.telemetryEnabled =
                UnifiedAnalytics.callServiceWithBoolResponse(dtdProcess, UnifiedAnalytics.TELEMETRY_ENABLED)
            }

            // Update global suppression state (note that the default is to suppress).
            Analytics.suppressAnalytics = data.suppressAnalytics

            if (data.shouldShowMessage) {
              // Process termination happens after the prompt.
              scheduleConsentPromptNotification(project, dtdProcess)
            } else {
              dtdProcess.terminate()
            }
          } catch (t: Throwable) {
            logger.error(t)
            dtdProcess.terminate()
          } finally {
            initLatch.countDown()
          }
        }
      }
      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          dtdProcess.start(sdk)
        } catch (t: Throwable) {
          logger.warn("Failed to start DTD process", t)
          initLatch.countDown()
        }
      }
    }

    try {
      initLatch.await(INITIALIZATION_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
      logger.debug("[DTDProcess] analytics data initialization timed out.")
    }

    return data
  }

  private fun scheduleConsentPromptNotification(project: Project, dtdProcess: DTDProcess) {
    ApplicationManager.getApplication().invokeLater {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(DAS_NOTIFICATION_GROUP)
        .createNotification(data.consentMessage!!, NotificationType.INFORMATION).also { notification ->
          notification.addAction(object : AnAction(CommonBundle.getOkButtonText()) {
            override fun actionPerformed(e: AnActionEvent) {
              try {
                notification.expire()
              } finally {
                UnifiedAnalytics.callServiceWithNoResponse(dtdProcess, UnifiedAnalytics.CLIENT_SHOWED_MESSAGE)
                dtdProcess.terminate()
              }
            }
          })
          notification.notify(project)
        }
    }
  }
}

object Analytics {

  private val logger: Logger =
    if (DEBUGGING_LOCALLY) PrintingLogger.SYSTEM_OUT else PluginLogger.createLogger(Analytics::class.java)

  private val reporter: AnalyticsReporter
    get() = if (DEBUGGING_LOCALLY) PrintingReporter else AnalyticsReporter.forConfiguration(
      AnalyticsConfigurationManager.safeData
    )

  var suppressAnalytics: Boolean = true
    internal set

  @JvmStatic
  fun getConfiguration(sdk: DartSdk, project: Project): AnalyticsConfiguration =
    AnalyticsConfigurationManager.getConfiguration(sdk, project, logger)

  @JvmStatic
  fun report(data: AnalyticsData) = data.reportTo(reporter)

  @JvmStatic
  fun recordRunOrDebugSession(mechanism: String, executor: Executor, project: Project?) {
    val type = if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
      AnalyticsConstants.DEBUG_SESSION_TYPE
    } else {
      AnalyticsConstants.RUN_SESSION_TYPE
    }
    report(SessionData(type, mechanism, project))
  }

  @JvmStatic
  fun updateEnvironment(commandLine: GeneralCommandLine) {
    updateEnvironment(commandLine.environment)
  }

  @JvmStatic
  fun updateEnvironment(environment:  MutableMap<String, String>) {
      environment[UnifiedAnalytics.Env.TOOL] = getToolName()
      environment[UnifiedAnalytics.Env.SUPPRESS_ANALYTICS] = suppressAnalytics.toString()
      environment[UnifiedAnalytics.Env.IDE_NAME] = ApplicationInfo.getInstance().versionName
      environment[UnifiedAnalytics.Env.IDE_VERSION] = ApplicationInfo.getInstance().fullVersion

      val plugin = PluginManagerCore.getPlugin(PluginId.getId(DART_PLUGIN_ID))
      if (plugin != null) {
          environment[UnifiedAnalytics.Env.PLUGIN_NAME] = plugin.name
          environment[UnifiedAnalytics.Env.PLUGIN_VERSION] = plugin.version
      }
  }
}


class ActionData(id: String?, place: String, project: Project?) :
  AnalyticsData(AnalyticsConstants.ACTION_TYPE, id, project) {

  init {
    add(AnalyticsConstants.PLACE, place)
  }
}

class AssistData(id: String?, project: Project?) :
  AnalyticsData(AnalyticsConstants.ASSIST_TYPE, id, project)

class FixData(id: String?, project: Project?) :
  AnalyticsData(AnalyticsConstants.FIX_TYPE, id, project)

class LegacyRefactoringData(kind: String?, project: Project?) :
  AnalyticsData(AnalyticsConstants.LEGACY_REFACTORING_TYPE, kind, project)

class SettingsData(project: Project?) :
  AnalyticsData(AnalyticsConstants.SETTINGS_TYPE, "settings", project)

class SessionData(type: String, mechanism: String, project: Project?) :
  AnalyticsData(type, mechanism, project)

abstract class AnalyticsData(type: String, val id: String?, val project: Project? = null) {
  val data = mutableMapOf<String, Any>()

  init {
    id?.let { add(AnalyticsConstants.ID, it) }
    add(AnalyticsConstants.TYPE, type)
  }

  fun <T> add(key: DataValue<T>, value: T) = key.addTo(this, value)

  internal operator fun set(key: String, value: Boolean) {
    data[key] = value
  }

  internal operator fun set(key: String, value: Int) {
    data[key] = value
  }

  internal operator fun set(key: String, value: String) {
    data[key] = value
  }

  open fun reportTo(reporter: AnalyticsReporter) {
    // We only report if we have an id for the event.
    if (id == null) return
    reporter.process(this)
  }

  companion object {
    @JvmStatic
    fun forAssist(id: String?, project: Project?): AssistData = AssistData(id, project)

    @JvmStatic
    fun forFix(id: String?, project: Project?): FixData = FixData(id, project)

    @JvmStatic
    fun forLegacyRefactoring(kind: String?, project: Project?): LegacyRefactoringData =
      LegacyRefactoringData(kind, project)

    @JvmStatic
    fun forAction(action: AnAction, event: AnActionEvent): ActionData = forAction(
      event.actionManager.getId(action), event.place, event.project
    )

    @JvmStatic
    fun forAction(id: String?, event: AnActionEvent): ActionData = forAction(
      id, event.place, event.project
    )

    @JvmStatic
    fun forAction(id: String?, place: String, project: Project?): ActionData = ActionData(
      id, place, project
    )

    @JvmStatic
    fun forAction(id: String?, project: Project?): ActionData = forAction(
      id, ActionPlaces.UNKNOWN, project
    )
  }
}

object AnalyticsConstants {
  /**
   * The unique identifier for an action or event.
   */
  @JvmField
  val ID = StringValue("id")

  /**
   * The UI location where an action was invoked, as provided by
   * [com.intellij.ui.PlaceProvider.getPlace] (for example, "MainMenu",
   * "MainToolbar", "EditorPopup", "GoToAction", etc.).
   */
  @JvmField
  val PLACE = StringValue("place")

  /**
   * The type of the analytics event (e.g., "action", "assist", "fix", ...).
   */
  @JvmField
  val TYPE = StringValue("type")

  @JvmField
  val DURATION_MS = IntValue("duration_ms")

  @JvmField
  val EXTRACT_ALL = BooleanValue("extract_all")

  @JvmField
  val CREATE_GETTER = BooleanValue("create_getter")

  @JvmField
  val INLINE_ALL = BooleanValue("inline_all")

  @JvmField
  val CUSTOM_NAME = BooleanValue("custom_name")

  internal const val ACTION_TYPE = "action"
  internal const val ASSIST_TYPE = "assist"
  internal const val FIX_TYPE = "fix"
  internal const val LEGACY_REFACTORING_TYPE = "legacy_refactoring"
  internal const val SETTINGS_TYPE = "settings"
  internal const val DEBUG_SESSION_TYPE = "debug_session"
  internal const val RUN_SESSION_TYPE = "run_session"

  const val MECHANISM_APP = "app"
  const val MECHANISM_TESTS = "tests"
  const val MECHANISM_REMOTE = "remote"
  const val MECHANISM_WEB = "web"
}

sealed class DataValue<T>(val name: String) {
  abstract fun addTo(data: AnalyticsData, value: T)
}

class StringValue(name: String) : DataValue<String>(name) {
  override fun addTo(data: AnalyticsData, value: String) {
    data[name] = value
  }
}

class IntValue(name: String) : DataValue<Int>(name) {
  override fun addTo(data: AnalyticsData, value: Int) {
    data[name] = value
  }
}

class BooleanValue(name: String) : DataValue<Boolean>(name) {
  override fun addTo(data: AnalyticsData, value: Boolean) {
    data[name] = value
  }
}

abstract class AnalyticsReporter {
  internal abstract fun process(data: AnalyticsData)

  companion object {
    fun forConfiguration(config: AnalyticsConfiguration?): AnalyticsReporter = config?.let { c ->
      if (c.suppressAnalytics || !c.telemetryEnabled) {
        NoOpReporter
      } else {
        UnifiedAnalyticsReporter
      }
    } ?: NoOpReporter
  }
}

internal object PrintingReporter : AnalyticsReporter() {
  override fun process(data: AnalyticsData) = println(data.data)
}

internal object NoOpReporter : AnalyticsReporter() {
  override fun process(data: AnalyticsData) = Unit
}

internal object UnifiedAnalyticsReporter : AnalyticsReporter() {
  const val IDE_EVENT = "ide_event"

  override fun process(data: AnalyticsData) {
    val project = data.project ?: return

    ApplicationManager.getApplication().executeOnPooledThread {
      sendAnalyticsEvent(project, data.data)
    }
  }

  private fun sendAnalyticsEvent(project: Project, dataMap: Map<String, Any>) {
    val params = JsonObject()
    params.addProperty(UnifiedAnalytics.Property.TOOL, getToolName())

    val event = JsonObject()
    event.addProperty(UnifiedAnalytics.Property.EVENT_NAME, IDE_EVENT)

    val evenData = JsonObject()
    for (entry in dataMap) {
      when (val value = entry.value) {
        is String -> evenData.addProperty(entry.key, value)
        is Boolean -> evenData.addProperty(entry.key, value)
        is Int -> evenData.addProperty(entry.key, value)
        else -> {
          // TODO (pq): consider logging
        }
      }
    }
    event.add(UnifiedAnalytics.Property.EVENT_DATA, evenData)

    // Note: encoded as a string.
    params.addProperty(UnifiedAnalytics.Property.EVENT, event.toString())

    // TODO (pq): temporary
    // print(params.toString())

    DartToolingDaemonService.getInstance(project)
      .sendRequest("${UnifiedAnalytics.SERVICE_NAME}.${UnifiedAnalytics.SEND}", params, true) { response: JsonObject ->
        // TODO (pq): temporary
        // print(response)
      }
  }
}

private fun getToolName(): String = when (ApplicationInfo.getInstance().build.productCode) {
  "AI" -> "android-studio-plugins"
  else -> "intellij-plugins"
}
