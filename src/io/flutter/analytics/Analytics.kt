/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.analytics

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.flutter.actions.FlutterAppAction

object Analytics {
  private val reporter = NoOpReporter

  @JvmStatic
  fun report(data: AnalyticsData) = data.reportTo(reporter)
}

abstract class AnalyticsReporter {
  internal abstract fun process(data: AnalyticsData)
}

internal object PrintingReporter : AnalyticsReporter() {
  override fun process(data: AnalyticsData) = println(data.data)
}

internal object NoOpReporter : AnalyticsReporter() {
  override fun process(data: AnalyticsData) = Unit
}

abstract class AnalyticsData(type: String) {
  val data = mutableMapOf<String, Any>()

  init {
    add(AnalyticsConstants.TYPE, type)
  }

  companion object {
    @JvmStatic
    fun forAction(action: AnAction, event: AnActionEvent): ActionData = ActionData(
      event.actionManager.getId(action)
      // `FlutterAppAction`s aren't registered so ask them directly.
        ?: (action as? FlutterAppAction)?.id,
      event.place
    )
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

  open fun reportTo(reporter: AnalyticsReporter) = reporter.process(this)
}

/**
 * Data describing an IntelliJ [com.intellij.openapi.actionSystem.AnAction] for analytics reporting.
 *
 * @param id The unique identifier of the action, typically defined in `plugin.xml`.
 * @param place The UI location where the action was invoked (e.g., "MainMenu", "Toolbar").
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/basic-action-system.html">IntelliJ Action System</a>
 */
class ActionData(private val id: String?, private val place: String) : AnalyticsData("action") {

  init {
    id?.let { add(AnalyticsConstants.ID, it) }
    add(AnalyticsConstants.PLACE, place)
  }

  override fun reportTo(reporter: AnalyticsReporter) {
    // We only report if we have an id for the event.
    if (id == null) return
    super.reportTo(reporter)
  }
}

/**
 * Defines standard keys for analytics data properties.
 *
 * The properties are exposed as `@JvmField`s to be easily accessible as static
 * fields from Java.
 */
object AnalyticsConstants {
  /**
   * Indicates if the project is a Google3 project.
   */
  @JvmField
  val GOOGLE3 = BooleanValue("google3")

  /**
   * The unique identifier for an action or event.
   */
  @JvmField
  val ID = StringValue("id")

  /**
   * Indicates if the project is in a Bazel context.
   */
  @JvmField
  val IN_BAZEL_CONTEXT = BooleanValue("inBazelContext")

  /**
   * Indicates if the Flutter SDK is missing.
   */
  @JvmField
  val MISSING_SDK = BooleanValue("missingSdk")

  /**
   * The UI location where an action was invoked, as provided by
   * [com.intellij.openapi.actionSystem.PlaceProvider.getPlace] (for example, "MainMenu",
   * "MainToolbar", "EditorPopup", "GoToAction", etc).
   */
  @JvmField
  val PLACE = StringValue("place")

  /**
   * Indicates if a restart is required for a hot reload request.
   */
  @JvmField
  val REQUIRES_RESTART = BooleanValue("requiresRestart")

  /**
   * The type of the analytics event (e.g., "action", ...).
   */
  @JvmField
  val TYPE = StringValue("type")
}


sealed class DataValue<T>(val name: String) {
  abstract fun addTo(data: AnalyticsData, value: T);
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