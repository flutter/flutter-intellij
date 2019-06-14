/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui

import com.intellij.testGuiFramework.fixtures.ActionButtonFixture
import com.intellij.testGuiFramework.fixtures.ExecutionToolWindowFixture
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.util.step
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause

fun IdeFrameFixture.launchFlutterApp() {
  step("Launch Flutter app") {
    tryFindRunAppButton().click()
    val runner = runner()
    Pause.pause(object : Condition("Start app") {
      override fun test(): Boolean {
        return runner.isExecutionInProgress
      }
    }, Timeouts.seconds30)
  }
}

fun IdeFrameFixture.tryFindRunAppButton(): ActionButtonFixture {
  while (true) {
    try {
      return findRunApplicationButton()
    }
    catch (ex: ComponentLookupException) {
      Pause.pause()
    }
  }
}

fun IdeFrameFixture.runner(): ExecutionToolWindowFixture.ContentFixture {
  return runToolWindow.findContent("main.dart")
}
