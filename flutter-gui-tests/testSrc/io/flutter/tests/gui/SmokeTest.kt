package io.flutter.tests.gui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.MultiLineLabelUI
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import org.junit.Assert
import org.junit.Test

// Inspired by CommandLineProjectGuiTest
@RunWithIde(CommunityIde::class)
class SmokeTest : GuiTestCase() {
  private val LOG = Logger.getInstance(this.javaClass)

  @Test
  fun testStartup() {
    ProjectCreator.createProject(projectName = "guitest")
    ideFrame {
      editor {
        // Wait until current file has appeared in current editor and set focus to editor.
        moveTo(1)
      }
      val editorCode = editor.getCurrentFileContents(false)
      Assert.assertTrue(editorCode!!.isNotEmpty())
      closeProjectAndWaitWelcomeFrame()
    }
  }
}
