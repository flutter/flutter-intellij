/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.client.ProjectRef
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.FileChooserDialogUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.ui.ui
import io.flutter.integrationTest.utils.Platform
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Open an existing Flutter project that has no `.idea/` directory: the project must open in a project window, be
 * indexed, and be set up as a Flutter project (module type, Flutter toolbar), instead of silently not opening.
 *
 * Spec: `testSrc/integration/specs/open-project-without-idea-dir.md` (MVP test T4, issue #8845).
 */
@Tag("agent-generated")
class OpenProjectWithoutIdeaDirUITest : ExistingProjectUITest("no_idea_dir_app") {

  /** The spec's fixture: a `flutter create` project with its `.idea/` removed, as a fresh clone would be. */
  override fun prepareProject() {
    val idea = projectDir.resolve(".idea").toFile()
    if (idea.exists()) {
      assertTrue(Platform.deleteRecursively(idea), "could not delete ${idea.path}")
    }
    println("Removed .idea/ from $projectDir")
  }

  @Test
  fun opensAsFlutterProject() {
    assertOracle("O1", "the folder is a Flutter project on disk and has no .idea/ directory") {
      assertTrue(projectDir.resolve("pubspec.yaml").exists(), "pubspec.yaml missing")
      assertTrue(projectDir.resolve("lib/main.dart").exists(), "lib/main.dart missing")
      assertFalse(projectDir.resolve(".idea").exists(), ".idea/ still exists; the fixture was not applied")
    }

    startIdeOnWelcomeScreen()

    step("1 On the welcome screen, click Open") {
      driver.ui.welcomeScreen { openProjectButton.click() }
    }

    step("2 In the Open dialog, enter the project folder's path and click OK", trigger = true) {
      val chooser = driver.ui.x("//div[@class='MyDialog' and contains(@title, 'Open')]", FileChooserDialogUi::class.java)
      chooser.waitFound(30.seconds)
      if (System.getenv("AGENT_TESTING_PROBE").toBoolean()) probe("open-dialog")
      chooser.selectFolder(projectDir)
      chooser.okButton.click()
      waitUntil("the Open dialog to close", timeout = 30.seconds) { chooser.notPresent() }
    }

    assertOracle("O2", "a project opens within 3 minutes and our plugin set it up as a Flutter project (module type, toolbar)") {
      try {
        waitUntil("a project to open", timeout = 3.minutes) { driver.getOpenProjects().isNotEmpty() }
      }
      catch (e: Exception) {
        throw AssertionError(
          "No project opened within 3 minutes after confirming the Open dialog" +
          (if (welcomeScreenShowing()) " (the welcome screen is still showing: the project silently did not open)" else ""), e
        )
      }
      // The plugin configures the module from a startup activity, after the project opens; give it the spec's 3 minutes
      // from step 1 rather than sampling once.
      try {
        waitUntil("the plugin to set the Flutter module up (FlutterModuleUtils.hasFlutterModule)", timeout = 3.minutes) {
          runCatching { driver.utility(FlutterModuleUtilsRef::class).hasFlutterModule(driver.singleProject()) }.getOrDefault(false)
        }
      }
      catch (e: Exception) {
        throw AssertionError("FlutterModuleUtils.hasFlutterModule(project) stayed false for 3 minutes: the plugin did not configure the module", e)
      }
      inFrame {
        waitUntil("the Flutter device selector to appear on the toolbar", timeout = 2.minutes) {
          xx("//div[contains(@javaclass, 'DeviceSelectorAction')]").list().isNotEmpty()
        }
      }
    }

    step("3 Wait for the project window and indexing") {
      waitForProjectReady(attempts = 1, frameTimeout = 3.minutes)
    }

    step("4 Open lib/main.dart from the Project view") {
      openProjectFile("lib/main.dart")
    }

    assertOracle("O3", "the project window is indexed; lib/main.dart shows the Flutter template; the Project view lists pubspec.yaml") {
      inFrame {
        assertTrue(editorFor("main.dart").text.contains("MaterialApp"), "main.dart editor does not show the Flutter template")
        projectView { projectViewTree.waitFound().pathExists("no_idea_dir_app", "pubspec.yaml") }
      }
    }

    assertOracle("O4", "no welcome screen; no 'No Flutter SDK configured.' banner on main.dart") {
      assertFalse(welcomeScreenShowing(), "the welcome screen is showing alongside the project")
      inFrame {
        assertTrue(
          xx("//div[contains(@visible_text, 'No Flutter SDK configured')]").list().isEmpty(),
          "the editor shows the 'No Flutter SDK configured.' banner"
        )
      }
    }
  }

  @Remote("io.flutter.utils.FlutterModuleUtils", plugin = "io.flutter")
  interface FlutterModuleUtilsRef {
    fun hasFlutterModule(project: ProjectRef): Boolean
  }
}
