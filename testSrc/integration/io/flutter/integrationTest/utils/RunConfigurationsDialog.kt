/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.elements.textField

/**
 * Extension to find the Run/Debug Configurations dialog.
 * The dialog can be in template editing mode or normal mode.
 */
fun Finder.runConfigurationsDialog(action: RunConfigurationsDialogUI.() -> Unit) {
  // The Run/Debug Configurations dialog - title varies by IDE (e.g. "Run/Debug Configurations")
  val dialog = x("//div[contains(@title, 'Configurations') or contains(@title, 'Run')]", RunConfigurationsDialogUI::class.java)
  dialog.waitFound()
  dialog.action()
}

open class RunConfigurationsDialogUI(data: ComponentData) : UiComponent(data) {

  /** Link to open template editing mode: "Edit configuration templates..." */
  val editTemplatesLink = x("//div[contains(@text, 'Edit configuration templates') or contains(@text, 'configuration templates')]")

  /**
   * Add (+). Tooltip is often "Add new configuration" (casing varies); accessible name may differ by IDE/skin.
   * In template mode, Flutter Test may not appear in the tree until you add it via this control.
   */
  val addNewConfigurationButton = x {
    or(
      byTooltip("Add new configuration"),
      byAccessibleName("Add New Configuration"),
      byAccessibleName("Add new configuration"),
    )
  }

  /** Tree/list of configuration types (when in template mode) or configs (normal mode) */
  val templatesList = x("//div[@class='JBList' or contains(@class, 'Tree')]")

  /**
   * Flutter Test template / config "Additional args" ([io.flutter.run.test.TestForm.additionalArgs]).
   * That form has a [javax.swing.JTextField] for the test name and a single
   * [com.intellij.ui.components.fields.ExpandableTextField] for additional args — a generic TextField
   * XPath matches the name field first and breaks typing and assertions.
   */
  val additionalArgsField
    get() = textField { byType("com.intellij.ui.components.fields.ExpandableTextField") }

  /** Apply button */
  val applyButton = x("//div[@text='Apply']")

  /** OK button */
  val okButton = x("//div[@text='OK']")

  /**
   * Adds the Flutter Test template type via the + menu: opens the chooser, filters to "Flutter Test", confirms.
   * Mirrors [com.intellij.driver.sdk.ui.components.common.dialogs.EditRunConfigurationsDialogUiComponent.addNewRunConfiguration].
   */
  fun selectFlutterTestTemplateViaAddMenu() {
    addNewConfigurationButton.click()
    keyboard {
      typeText("Flutter Test", delayBetweenCharsInMs = 100)
      enter()
    }
  }

  /**
   * Dumps the dialog subtree as the driver's XPath DOM (see [io.flutter.integrationTest.utils.dumpSwingHierarchyToConsole]).
   * Call when this dialog is already found and [component] is resolved.
   */
  fun dumpXPathTreeToConsole(label: String = "Run/Debug Configurations dialog") {
    driver.dumpSwingHierarchyToConsole(label, component, onlyFrontend = false)
  }
}
