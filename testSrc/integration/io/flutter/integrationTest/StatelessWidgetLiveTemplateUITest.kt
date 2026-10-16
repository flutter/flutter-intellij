/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import kotlin.io.path.writeText

/**
 * Expands the plugin's `stless` live template in a Dart file and fills in the widget name.
 */
class StatelessWidgetLiveTemplateUITest : ExistingProjectUITest("live_template_test") {

  override fun prepareProject() {
    projectDir.resolve("lib/greeting.dart").writeText("import 'package:flutter/material.dart';\n\n")
  }

  @Test
  fun expandStlessTemplate() {
    startIdeOnProject()
    openProjectFile("lib/greeting.dart")

    inFrame {
      val editor = editorFor("greeting.dart")
      editor.click()
      keyboard {
        hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_END)
        typeText("stless", 80)
      }
      Thread.sleep(1_000)
      keyboard { tab() }
      Thread.sleep(1_000)
      keyboard {
        typeText("Greeting", 80)
        enter()
      }

      waitUntil("greeting.dart to contain the generated widget") {
        val text = editor.text
        text.contains("class Greeting extends StatelessWidget") && text.contains("const Greeting({super.key});")
      }
    }
  }
}
