/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.SwingHierarchyService

/**
 * Prints the Swing UI as an XML DOM string — the same representation used for XPath queries ([com.intellij.driver.sdk.ui.remote.SearchService]).
 *
 * @param root If `null`, dumps the **entire** hierarchy (can be huge). Pass a dialog or panel [Component] to limit scope.
 */
fun Driver.dumpSwingHierarchyToConsole(
  label: String,
  root: Component? = null,
  onlyFrontend: Boolean = false,
) {
  val xml = service<SwingHierarchyService>().getSwingHierarchyAsDOM(root, onlyFrontend)
  println("===== $label — Swing XPath DOM (${xml.length} chars) =====")
  println(xml)
  println("===== end $label =====")
}
