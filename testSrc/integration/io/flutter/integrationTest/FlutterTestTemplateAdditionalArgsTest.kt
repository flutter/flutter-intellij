/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 *
 * Integration test for https://github.com/flutter/flutter-intellij/issues/8842
 * Reproduces the bug: Flutter Test template 'Additional args' are not applied
 * to run configs created from the gutter play icon.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import com.intellij.ide.starter.project.LocalProjectInfo
import io.flutter.integrationTest.utils.createFlutterProjectWithCli
import io.flutter.integrationTest.utils.deleteFlutterProject
import io.flutter.integrationTest.utils.runConfigurationsDialog
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class FlutterTestTemplateAdditionalArgsTest {

  private val testProjectName = "flutter_test_template_args_${System.currentTimeMillis()}"
  private lateinit var run: BackgroundRun

  companion object {
    const val TEMPLATE_ADDITIONAL_ARGS = "--dart-define=INTEGRATION_TEST_DEBUG=true"
  }

  @BeforeAll
  fun setup() {
    createFlutterProjectWithCli(testProjectName, projectName = "flutter_test_template")
  }

  @AfterEach
  fun closeIde() {
    if (::run.isInitialized) {
      println("Closing IDE")
      run.closeIdeAndWait()
    } else {
      println("IDE was not started, skipping close")
    }
  }

  @AfterAll
  fun teardown() {
    deleteFlutterProject(testProjectName)
  }

  @Test
  fun gutterCreatedFlutterTestConfigInheritsTemplateAdditionalArgs() {
    println("Initializing IDE test context")
    val projectPath = Paths.get(System.getProperty("java.io.tmpdir"), testProjectName)
    run = Setup.setupTestContextIC(javaClass.simpleName, LocalProjectInfo(projectPath)).runIdeWithDriver()

    run.driver.withContext {
      ideFrame {
        waitFound()
        // See if this next line is required. There are errors that always appear but are innocuous
        //driver.waitForIndicators(1.minutes)
        println("IDE is ready.")

        // 1. Verify project structure
        projectView {
          projectViewTree.pathExists("flutter_test_template", "pubspec.yaml")
          projectViewTree.pathExists("flutter_test_template", "test", "widget_test.dart")
        }
        wait(30.seconds)

        // 2. Open Run/Debug Configurations and edit the Flutter Test template.
        // Use now = false so ActionManager.tryToExecute queues the action; the default (now = true)
        // can block the remote call until the modal dialog is dismissed (see intellij-driver-docs).
        println("before editRunConfigurations")
        driver.invokeAction("editRunConfigurations", now = false)
        println("waiting after editRunConfigurations")
        wait(30.seconds)

        // 3. Click "Edit configuration templates..." to open templates
        // The link is typically at the bottom-left of the dialog
        runConfigurationsDialog {
          editTemplatesLink.click()
          println("waiting after editTemplatesLink")
          wait(15.seconds)
        }

        // 4. Add Flutter Test template via + (it may not appear in the tree until added), then set Additional args
        runConfigurationsDialog {
          selectFlutterTestTemplateViaAddMenu()
          println("waiting after Flutter Test template selected via Add")
          wait(15.seconds)

          // Debug: dump Swing UI as XPath DOM (same model driver uses for x()/xx()) while Flutter Test template UI is visible
          dumpXPathTreeToConsole("Run/Debug Configurations — Flutter Test template visible")

          // Find and fill the Additional args field (ExpandableTextField or JTextField)
          additionalArgsField.click()
          keyboard {
            typeText(TEMPLATE_ADDITIONAL_ARGS, delayBetweenCharsInMs = 30)
          }
          println("waiting after keyboard type")
          wait(5.seconds)
        }

        // 5. Apply and close the dialog (OK or Apply)
        runConfigurationsDialog {
          applyButton.click()
          println("waiting after apply click")
          wait(5.seconds)
          okButton.click()
        }
        println("waiting after ???")
        wait(5.seconds)

        // 6. Open the test file and place caret on a test
        projectView {
          projectViewTree.doubleClickPath("flutter_test_template", "test", "widget_test.dart", fullMatch = false)
        }
        println("waiting after project click")
        wait(2.seconds)

        // 7. Run the test from the gutter (triggers config creation)
        // Put caret on the testWidgets line and invoke Run
        driver.invokeAction("RunContext")
        wait(3.seconds)

        // 8. Open Run Configurations and verify the created config has Additional args
        println("before editRunConfigurations (verify step)")
        driver.invokeAction("editRunConfigurations", now = false)
        println("waiting after editRunConfigurations (verify step)")
        wait(2.seconds)

        runConfigurationsDialog {
          // The newly created config should be selected. Verify Additional args (actual Swing text).
          val additionalArgsValue = additionalArgsField.waitFound().text
          org.junit.jupiter.api.Assertions.assertTrue(
            additionalArgsValue.contains(TEMPLATE_ADDITIONAL_ARGS),
            "Expected Flutter Test config created from gutter to inherit template's Additional args '$TEMPLATE_ADDITIONAL_ARGS', " +
              "but component state was: '$additionalArgsValue'"
          )
          println("closing Run Configurations dialog after assertion")
          okButton.click()
        }
      }
    }
  }
}
