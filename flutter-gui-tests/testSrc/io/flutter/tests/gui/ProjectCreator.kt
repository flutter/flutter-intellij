package io.flutter.tests.gui

import com.intellij.ide.fileTemplates.impl.UrlUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.platform.templates.github.ZipUtil
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import org.fest.swing.exception.WaitTimedOutError
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL

val GuiTestCase.ProjectCreator by ProjectCreator

// Inspired by CommunityProjectCreator
class ProjectCreator(guiTestCase: GuiTestCase) : TestUtilsClass(guiTestCase) {

  companion object : TestUtilsClassCompanion<ProjectCreator>({ ProjectCreator(it) })

  private val defaultProjectName = "untitled"
  private val sampleProjectName = "simple_app"
  private val log = Logger.getInstance(this.javaClass)

  fun importProject(projectName: String = sampleProjectName): File {
    val projectDirFile = extractProject(projectName)
    val projectPath: File = guiTestCase.guiTestRule.importProject(projectDirFile)
    with(guiTestCase) {
      openMainInProject()
      waitForFirstIndexing()
      ideFrame {
        editor {
          try {
            val note = notificationPanel()
            // Best practice is to avoid reusing strings defined in bundles.
            if (note?.getLabelText() == "Pubspec has been edited") {
              log.info("Get dependencies")
              note.clickLink("Get dependencies")
              robot().waitForIdle() // TODO(messick) Check Messages view for completion of 'flutter packages get'
            }
          }
          catch (ex: WaitTimedOutError) {
            // TODO(messick) Check that pubspec is valid.
            // Sometimes the notification panel does not show up. Open pubspec.yaml and use its link.
            // This is a work-around for Mac menus currently being broken in GUI tests.
          }
        }
      }
    }
    return projectPath
  }

  private fun extractProject(projectName: String): File {
    val projectDirUrl = this.javaClass.classLoader.getResource("flutter_projects/$projectName")
    val children = UrlUtil.getChildrenRelativePaths(projectDirUrl)
    val tempDir = java.nio.file.Files.createTempDirectory("test").toFile()
    val projectDir = File(tempDir, projectName)
    for (child in children) {
      val url = childUrl(projectDirUrl, child)
      val inputStream = URLUtil.openResourceStream(url)
      val outputFile = File(projectDir, child)
      File(outputFile.parent).mkdirs()
      val outputStream = BufferedOutputStream(FileOutputStream(outputFile))
      try {
        StreamUtil.copyStreamContent(inputStream, outputStream)
      }
      finally {
        inputStream.close()
        outputStream.close()
      }
    }
    val srcZip = File(projectDir, "src.zip")
    if (srcZip.exists() && srcZip.isFile) {
      run {
        ZipUtil.unzip(null, projectDir, srcZip, null, null, true)
        srcZip.delete()
      }
    }
    return projectDir
  }

  private fun childUrl(parent: URL, child: String): URL {
    return URL(UriUtil.trimTrailingSlashes(parent.toExternalForm()) + "/" + child)
  }

  fun createProject(projectName: String = defaultProjectName, needToOpenMain: Boolean = true) {
    with(guiTestCase) {
      welcomeFrame {
        actionLink(name = "Create New Project").click()
        GuiTestUtilKt.waitProgressDialogUntilGone(robot = robot(), progressTitle = "Loading Templates",
            timeoutToAppear = Timeouts.seconds02)
        dialog("New Project") {
          jList("Flutter").clickItem("Flutter")
          button("Next").click()
          typeText(projectName)
          button("Finish").click()
          GuiTestUtilKt.waitProgressDialogUntilGone(robot = robot(), progressTitle = "Creating Flutter Project",
              timeoutToAppear = Timeouts.seconds03)
        }
      }
      waitForFirstIndexing()
      if (needToOpenMain) openMainInProject(wait = true)
    }
  }

  private fun GuiTestCase.waitForFirstIndexing() {
    ideFrame {
      val secondsToWait = 10
      try {
        waitForStartingIndexing(secondsToWait)
      }
      catch (timedOutError: WaitTimedOutError) {
        log.warn("Wait for indexing exceeded $secondsToWait seconds")
      }
      waitForBackgroundTasksToFinish()
    }
  }

  private fun GuiTestCase.openMainInProject(wait: Boolean = false) {
    ideFrame {
      projectView {
        path(project.name, "lib", "main.dart").doubleClick()
        if (wait) waitForBackgroundTasksToFinish()
      }
    }
  }
}
