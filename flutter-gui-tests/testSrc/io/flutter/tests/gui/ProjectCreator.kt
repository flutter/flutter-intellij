/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui

import com.intellij.ide.fileTemplates.impl.UrlUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.platform.templates.github.ZipUtil
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.step
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import io.flutter.tests.gui.fixtures.FlutterMessagesToolWindowFixture
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Pause.pause
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
  var flutterMessagesFixture: FlutterMessagesToolWindowFixture.FlutterContentFixture? = null

  fun importProject(projectName: String = sampleProjectName): File {
    return step("Import project $projectName") {
      val projectDirFile = extractProject(projectName)
      val projectPath: File = guiTestCase.guiTestRule.importProject(projectDirFile)
      with(guiTestCase) {
        waitForFirstIndexing()
        step("Get packages") {
          openPubspecInProject()
          ideFrame {
            editor {
              val note = notificationPanel()
              if (note?.getLabelText() == "Flutter commands") {
                note.clickLink("Packages get")
                flutterMessagesFixture = flutterMessagesToolWindowFixture().getFlutterContent(projectName)
                flutterMessagesFixture!!.findMessageContainingText("Process finished")
              }
              // TODO(messick) Close pubspec.yaml once editor tab fixtures are working.
              //closeTab("pubspec.yaml")
            }
          }
        }
        openMainInProject(wait = true)
        pause()
      }
      projectPath
    }
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
      step("Create project $projectName") {
        welcomeFrame {
          this.actionLink(name = "Create New Project").click()
          dialog("New Project") {
            jList("Flutter").clickItem("Flutter")
            button("Next").click()
            typeText(projectName)
            button("Finish").click()
            GuiTestUtilKt.waitProgressDialogUntilGone(
                robot = robot(), progressTitle = "Creating Flutter Project", timeoutToAppear = Timeouts.seconds03)
          }
        }
        waitForFirstIndexing()
      }
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
        step("Open lib/main.dart") {
          path(project.name, "lib", "main.dart").doubleClick()
          if (wait) waitForBackgroundTasksToFinish()
        }
      }
    }
  }

  private fun GuiTestCase.openPubspecInProject() {
    ideFrame {
      projectView {
        step("Open pubspec.yaml") {
          path(project.name, "pubspec.yaml").doubleClick()
        }
      }
    }
  }

  fun IdeFrameFixture.flutterMessagesToolWindowFixture(): FlutterMessagesToolWindowFixture {
    return FlutterMessagesToolWindowFixture(project, robot())
  }
}
