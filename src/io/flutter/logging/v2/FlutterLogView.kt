/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.logging.v2

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.SimpleToolWindowPanel
import io.flutter.logging.FlutterLog
import io.flutter.logging.FlutterLogFilterPanel
import io.flutter.run.daemon.FlutterApp
import org.apache.commons.lang.StringUtils
import javax.swing.JComponent

class FlutterLogView(
    environment: ExecutionEnvironment,
    flutterApp: FlutterApp
) : ConsoleView {
  private val flutterLogFormater: FlutterLogFormater = FlutterLogFormater()
  private val myConsoleView: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project).console
  private val filterPanel = FlutterLogFilterPanel { doFilter(it) }
  private val simpleToolWindowPanel: SimpleToolWindowPanel = SimpleToolWindowPanel(true, true).apply {
    setToolbar(filterPanel.root)
    setContent(myConsoleView.component)
  }
  private val flutterLogListener = FlutterLog.Listener {
    // TODO: corect log content type
    myConsoleView.print(flutterLogFormater.format(it), ConsoleViewContentType.NORMAL_OUTPUT)
  }
  private val flutterLog: FlutterLog = flutterApp.flutterLog.apply {
    addListener(flutterLogListener, Disposable { })
  }

  private var lastEntryFilter: EntryFilter? = null
  private fun doFilter(param: FlutterLogFilterPanel.FilterParam) {
    val filter = if (param.expression.isNullOrBlank()) null else EntryFilter(param.expression, param.isMatchCase, param.isRegex)
    if (lastEntryFilter == filter) {
      return
    }
    myConsoleView.clear()
    val filterdEntries = if (filter == null) flutterLog.entries else flutterLog.entries.filter { filter.accept(it) }
    filterdEntries.forEach { myConsoleView.print(flutterLogFormater.format(it), ConsoleViewContentType.NORMAL_OUTPUT) }
  }

  override fun setOutputPaused(value: Boolean) {
    myConsoleView.isOutputPaused = value
  }

  override fun attachToProcess(processHandler: ProcessHandler?) {
    processHandler?.let { flutterLog.listenToProcess(it, this) }
  }

  override fun print(text: String, contentType: ConsoleViewContentType) = flutterLog.addConsoleEntry(text, contentType)

  override fun hasDeferredOutput(): Boolean = myConsoleView.hasDeferredOutput()
  override fun clear() = myConsoleView.clear()
  override fun setHelpId(helpId: String) = myConsoleView.setHelpId(helpId)
  override fun getContentSize(): Int = myConsoleView.contentSize
  override fun createConsoleActions(): Array<AnAction> = myConsoleView.createConsoleActions()
  override fun getComponent(): JComponent = simpleToolWindowPanel
  override fun performWhenNoDeferredOutput(runnable: Runnable) = myConsoleView.performWhenNoDeferredOutput(runnable)
  override fun getPreferredFocusableComponent(): JComponent = myConsoleView.preferredFocusableComponent
  override fun isOutputPaused(): Boolean = myConsoleView.isOutputPaused
  override fun addMessageFilter(filter: Filter) = myConsoleView.addMessageFilter(filter)
  override fun printHyperlink(hyperlinkText: String, info: HyperlinkInfo?) = Unit
  override fun canPause(): Boolean = myConsoleView.canPause()
  override fun allowHeavyFilters() = myConsoleView.allowHeavyFilters()
  override fun dispose() = myConsoleView.dispose()
  override fun scrollTo(offset: Int) = myConsoleView.scrollTo(offset)
}
