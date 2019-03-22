/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui.fixtures

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.testGuiFramework.fixtures.MessagesToolWindowFixture
import com.intellij.testGuiFramework.fixtures.ToolWindowFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.ui.content.Content
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.timing.Timeout
import org.junit.Assert

class FlutterMessagesToolWindowFixture(project: Project, robot: Robot) : ToolWindowFixture(ToolWindowId.MESSAGES_WINDOW, project, robot) {

  fun getFlutterContent(appName: String): FlutterContentFixture {
    val content = getContent("[$appName] Flutter")
    Assert.assertNotNull(content)
    return FlutterContentFixture(content!!)
  }

  inner class FlutterContentFixture(val myContent: Content) {

    fun findMessageContainingText(text: String, timeout: Timeout = Timeouts.seconds10): FlutterMessageFixture {
      val element = doFindMessage(text, timeout)
      return createFixture(element)
    }

    private fun robot(): Robot {
      return (this@FlutterMessagesToolWindowFixture).myRobot
    }

    private fun createFixture(found: ConsoleViewImpl): FlutterMessageFixture {
      return FlutterMessageFixture(robot(), found)
    }

    private fun doFindMessage(matcher: String, timeout: Timeout): ConsoleViewImpl {
      return GuiTestUtil.waitUntilFound(robot(), myContent.component, object : GenericTypeMatcher<ConsoleViewImpl>(ConsoleViewImpl::class.java) {
        override fun isMatching(panel: ConsoleViewImpl): Boolean {
          if (panel.javaClass.name.startsWith(ConsoleViewImpl::class.java.name) && panel.isShowing) {
            val doc = panel.editor.document
            return (doc.text.contains(matcher))
          }
          return false
        }
      }, timeout)
    }

  }
}

// Modeled after MessagesToolWindowFixture.MessageFixture but used with consoles rather than error trees.
class FlutterMessageFixture(val robot: Robot, val target: ConsoleViewImpl) {

  fun getText(): String {
    return target.editor.document.text
  }

  fun findHyperlink(p0: String): MessagesToolWindowFixture.HyperlinkFixture {
    TODO("not implemented")
  }

}
