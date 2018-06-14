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
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.SimpleToolWindowPanel
import io.flutter.logging.FlutterLog
import io.flutter.logging.FlutterLogFilterPanel
import io.flutter.run.daemon.FlutterApp
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class FlutterLogView(
  environment: ExecutionEnvironment,
  private val flutterApp: FlutterApp
) : ConsoleView {
  private val flutterLogFormater: FlutterLogFormater = FlutterLogFormater()
  private val myConsoleView: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project).console
  private val filterPanel = FlutterLogFilterPanel(::doFilter)
  private val simpleToolWindowPanel: SimpleToolWindowPanel = SimpleToolWindowPanel(true, true).apply {
    val windowToolbar = ActionManager.getInstance().createActionToolbar("FlutterLogViewToolbar", createToolbar(), true)
    val toolbar = JPanel()
    toolbar.layout = BorderLayout()
    toolbar.add(filterPanel.root, BorderLayout.WEST)
    toolbar.add(windowToolbar.component, BorderLayout.EAST)
    setToolbar(toolbar)
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

  private fun forceReload() {
    lastEntryFilter = null
    doFilter(filterPanel.currentFilterParam)
  }

  private fun doFilter(param: FlutterLogFilterPanel.FilterParam) {
    val filter = if (param.expression.isNullOrBlank()) null else EntryFilter(param.expression, param.isMatchCase, param.isRegex)
    if (lastEntryFilter != null && lastEntryFilter == filter) {
      return
    }
    lastEntryFilter = filter
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


  private fun createToolbar(): DefaultActionGroup = DefaultActionGroup().apply {
    add(ConfigureAction())
  }

  private inner class ConfigureAction : AnAction("Configure", null, AllIcons.General.Gear), RightAlignedToolbarAction {
    override fun update(event: AnActionEvent) {
      event.presentation.isEnabled = flutterApp.isSessionActive()
    }

    override fun actionPerformed(event: AnActionEvent) {
      val component = (event.presentation.getClientProperty("button") as? JComponent)
          ?: (event.inputEvent.source as? JComponent)
          ?: return

      val popupMenu = ActionManager.getInstance().createActionPopupMenu(
        ActionPlaces.UNKNOWN,
        createPopupActionGroup()
      )
      popupMenu.component.show(component, component.width, 0)
    }

    private fun createPopupActionGroup(): DefaultActionGroup = DefaultActionGroup().apply {
      add(ShowTimeStampsAction())
      add(ShowLevelAction())
    }
  }

  private inner class ShowTimeStampsAction internal constructor() : ToggleAction("Show timestamps") {
    override fun isSelected(e: AnActionEvent): Boolean = flutterLogFormater.isShowTimestamp

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      flutterLogFormater.isShowTimestamp = state
      forceReload()
    }
  }

  private inner class ShowLevelAction internal constructor() : ToggleAction("Show log levels") {

    override fun isSelected(e: AnActionEvent): Boolean = flutterLogFormater.isShowLogLevel

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      flutterLogFormater.isShowLogLevel = state
      forceReload()
    }
  }
}
