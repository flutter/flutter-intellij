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
 * **Shows:** generating a `StatelessWidget` with the Flutter plugin's `stless` live template.
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`) that has an almost empty
 *    `lib/greeting.dart`.
 * 2. Open `lib/greeting.dart`, move to the end of the file, and type `stless`.
 * 3. Press Tab to expand the live template.
 * 4. Type `Greeting` as the widget name and press Enter.
 *
 * Expected result: the file contains `class Greeting extends StatelessWidget` with a `const Greeting({super.key})`
 * constructor and a `build` method that returns a `Placeholder`.
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
