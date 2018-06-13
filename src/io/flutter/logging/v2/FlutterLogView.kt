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
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.JComponent

class FlutterLogView(
    environment: ExecutionEnvironment
) : ConsoleView {
  private val myConsoleView: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project).console

  override fun hasDeferredOutput(): Boolean {
    return myConsoleView.hasDeferredOutput()
  }

  override fun clear() {
    myConsoleView.clear()
  }

  override fun setHelpId(helpId: String) {
    myConsoleView.setHelpId(helpId)
  }

  override fun print(text: String, contentType: ConsoleViewContentType) {
    myConsoleView.print(text, contentType)
  }

  override fun getContentSize(): Int {
    return myConsoleView.contentSize
  }

  override fun setOutputPaused(value: Boolean) {
    myConsoleView.isOutputPaused = value
  }

  override fun createConsoleActions(): Array<AnAction> {
    return myConsoleView.createConsoleActions()
  }

  override fun getComponent(): JComponent {
    return myConsoleView.component
  }

  override fun performWhenNoDeferredOutput(runnable: Runnable) {
    myConsoleView.performWhenNoDeferredOutput(runnable)
  }

  override fun attachToProcess(processHandler: ProcessHandler?) {
    myConsoleView.attachToProcess(processHandler)
  }

  override fun getPreferredFocusableComponent(): JComponent {
    return myConsoleView.preferredFocusableComponent
  }

  override fun isOutputPaused(): Boolean {
    return myConsoleView.isOutputPaused
  }

  override fun addMessageFilter(filter: Filter) {
    myConsoleView.addMessageFilter(filter)
  }

  override fun printHyperlink(hyperlinkText: String, info: HyperlinkInfo?) {
    myConsoleView.printHyperlink(hyperlinkText, info)
  }

  override fun canPause(): Boolean {
    return myConsoleView.canPause()
  }

  override fun allowHeavyFilters() {
    myConsoleView.allowHeavyFilters()
  }

  override fun dispose() {
    myConsoleView.dispose()
  }

  override fun scrollTo(offset: Int) {
    myConsoleView.scrollTo(offset)
  }
}
