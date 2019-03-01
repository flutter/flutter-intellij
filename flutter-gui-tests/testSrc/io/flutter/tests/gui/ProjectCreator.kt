package io.flutter.tests.gui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError

val GuiTestCase.ProjectCreator by ProjectCreator

// Inspired by CommunityProjectCreator
class ProjectCreator(guiTestCase: GuiTestCase) : TestUtilsClass(guiTestCase) {

  companion object : TestUtilsClassCompanion<ProjectCreator>({ it -> ProjectCreator(it) })

  private val defaultProjectName = "untitled"
  private val LOG = Logger.getInstance(this.javaClass)

  fun createProject(projectName: String = defaultProjectName, needToOpenMain: Boolean = true) {
    with(guiTestCase) {
      welcomeFrame {
        actionLink("Create New Project").click()
        GuiTestUtilKt.waitProgressDialogUntilGone(robot = robot(), progressTitle = "Loading Templates",
                                                  timeoutToAppear = Timeouts.seconds02)
        dialog("New Project") {
          jList("Flutter").clickItem("Flutter")
          button("Next").click()
          typeText(projectName)
          checkFileAlreadyExistsDialog() //confirm overwriting already created project
          button("Finish").click()
          GuiTestUtilKt.waitProgressDialogUntilGone(robot = robot(), progressTitle = "Creating Flutter Project",
                                                    timeoutToAppear = Timeouts.seconds03)
        }
      }
      waitForFirstIndexing()
      if (needToOpenMain) openMainInProject()
    }
  }

  private fun GuiTestCase.checkFileAlreadyExistsDialog() {
    try {
      val dialogFixture = dialog(IdeBundle.message("title.file.already.exists"), false, timeout = Timeouts.seconds01)
      dialogFixture.button("Yes").click()
    }
    catch (cle: ComponentLookupException) { /*do nothing here */
    }
  }

  fun GuiTestCase.waitForFirstIndexing() {
    ideFrame {
      val secondToWaitIndexing = 10
      try {
        waitForStartingIndexing(secondToWaitIndexing)
      }
      catch (timedOutError: WaitTimedOutError) {
        LOG.warn("Wait for indexing exceeded $secondToWaitIndexing seconds")
      }
      waitForBackgroundTasksToFinish()
    }
  }

  fun GuiTestCase.openMainInProject() {
    ideFrame {
      projectView {
        path(project.name, "lib", "main.dart").doubleClick()
        waitForBackgroundTasksToFinish()
      }
    }
  }
}
