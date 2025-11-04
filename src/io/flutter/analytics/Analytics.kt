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
  fun report(data: AnalyticsData) = reporter.report(data)
}

abstract class AnalyticsReporter {

  fun report(data: AnalyticsData) = data.reportTo(this)

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
    add("type", type)
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

  fun add(key: String, value: Boolean) {
    data[key] = value
  }

  fun add(key: String, value: Int) {
    data[key] = value
  }

  fun add(key: String, value: String) {
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
    id?.let { add("id", it) }
    add("place", place)
  }

  override fun reportTo(reporter: AnalyticsReporter) {
    // We only report if we have an id for the event.
    if (id == null) return
    super.reportTo(reporter)
  }
}
