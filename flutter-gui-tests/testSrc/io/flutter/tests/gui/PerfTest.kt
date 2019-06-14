/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui

import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import io.flutter.tests.gui.fixtures.flutterPerfFixture
import org.fest.swing.timing.Pause.pause
import org.junit.Test

@RunWithIde(CommunityIde::class)
class PerfTest : GuiTestCase() {

  @Test
  fun widgetTree() {
    ProjectCreator.importProject()
    ideFrame {
      launchFlutterApp()
      val inspector = flutterPerfFixture(this)
      inspector.populate()

      pause()

      runner().stop()
    }
  }
}