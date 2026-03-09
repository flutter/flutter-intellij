/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import io.flutter.integrationTest.utils.FlutterTestSdk
import io.flutter.integrationTest.utils.newProjectWelcomeScreen
import org.gradle.internal.impldep.org.junit.Ignore
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class FlutterSdkSettingsUITest {

  companion object {
    var testProjectName = ""

    @JvmStatic
    @AfterAll
    fun cleanUpTestFolder() {
      if (testProjectName.isEmpty()) return
      val projectPath = Paths.get(System.getProperty("user.home"), "IdeaProjects", testProjectName)
      val projectFile = projectPath.toFile()
      if (projectFile.exists()) {
        projectFile.deleteRecursively()
        println("Deleted test project: $projectPath")
      }
    }
  }

  private lateinit var run: BackgroundRun
  private lateinit var alternateSdkPath: String

  @BeforeEach
  fun initContext() {
    alternateSdkPath = FlutterTestSdk.ensureInstalled()
    println("Alternate Flutter SDK path: $alternateSdkPath")
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

  @Test
  @Ignore
  fun openSettingsAndQuit() {
    println("Initializing IDE test context")
    run = Setup.setupTestContextIC("FlutterSdkSettingsUITest").runIdeWithDriver()

    run.driver.withContext {
      welcomeScreen {
        waitFound()
        println("Welcome screen is visible.")

        openSettingsDialog()

        settingsDialog {
          waitFound(20.seconds)
          println("Settings dialog is open.")
          x("//div[@text='Cancel']").waitFound().click()
          println("Settings dialog closed.")
        }
      }
    }
  }

  /**
   * Test plan:
   *   1.  Create a new Flutter project from the welcome screen and open it in the IDE.
   *   2.  Wait for the IDE frame and all background indexing to finish.
   *   2b. Lower pubspec.yaml sdk constraint to >=3.10.9 so the project is compatible with
   *       the alternate SDK (Dart 3.10.9). The project is created with Flutter 3.41.4 (Dart
   *       3.11.1) which sets >=3.11.0, which the alternate SDK does not satisfy.
   *   3.  Open Settings → Languages & Frameworks → Flutter.
   *   4.  Open Settings dialog and navigate to Languages & Frameworks → Flutter.
   *   5.  Navigate to the Flutter settings section.
   *   6.  Capture `initialVersion` — the SDK version already displayed below the path field.
   *       This is the version currently configured for the project.
   *   7.  Locate the Flutter SDK field (a ComboBox with an ExtendableTextField editor).
   *   8.  Set the field to `alternateSdkPath` via JTextComponent.setText(). This triggers the
   *       document listener which starts the async flutter --version call. BasicComboBoxEditor
   *       .getItem() reads directly from editor.getText(), so apply() will see the new path.
   *   9.  Assert the field value equals `alternateSdkPath`.
   *   10. Wait for the version label to update asynchronously (flutter --version runs in
   *       background). Capture `newVersion`.
   *   11. Assert `newVersion != initialVersion` (the version actually changed).
   *   12. Assert `newVersion` contains "3.38.10" (the alternate SDK version).
   *   13. Click OK to save the settings.
   *   14. Reopen Settings → Flutter and verify the path and version label persisted.
   *   15. Click Cancel to close.
   *
   * Key implementation notes:
   *   - The version label (myVersionLabel / JBLabel) shows "Flutter X.Y.Z • channel <name> • <url>".
   *     The "channel" substring distinguishes it from the static "Flutter SDK path:" label.
   *   - sdkTextField.text = value uses JTextComponent.setText() which updates the field
   *     directly. BasicComboBoxEditor.getItem() returns editor.getText(), so apply() reads
   *     the correct value without needing a separate focusLost event.
   */
  @Test
  fun setFlutterSdkPath() {
    testProjectName = "sdk_settings_test_${System.currentTimeMillis()}"
    println("Initializing IDE test context")
    run = Setup.setupTestContextIC("FlutterSdkSettingsUITest").runIdeWithDriver()

    // Step 1: Create a new Flutter project from the welcome screen and open it in the IDE.
    newProjectWelcomeScreen(run, testProjectName)

    run.driver.withContext {
      ideFrame {

        // Step 2: Wait for the IDE frame and all background indexing tasks to finish.
        waitFound()
        driver.waitForIndicators(2.minutes)
        println("IDE frame is ready.")

        // Step 2b: Lower the pubspec.yaml SDK constraint to >=3.10.9 so it is compatible with
        // the alternate Flutter SDK (Dart 3.10.9). The project was created with the current
        // Flutter SDK (Dart 3.11.1), which sets a higher minimum that the alternate SDK fails.
        val pubspecFile = Paths.get(
          System.getProperty("user.home"), "IdeaProjects", testProjectName, "pubspec.yaml"
        ).toFile()
        if (pubspecFile.exists()) {
          val original = pubspecFile.readText()
          // Match any sdk version constraint format: ^3.x, >=3.x <4.x, with or without quotes.
          // The pattern anchors on the leading quote/caret/> to avoid matching "sdk: flutter".
          val updated = original.replace(
            Regex("""sdk:\s*['"]?[\^>=][^'"\n#]*['"]?"""),
            "sdk: '>=3.10.9 <4.0.0'"
          )
          pubspecFile.writeText(updated)
          println("Updated pubspec.yaml SDK constraint to >=3.10.9 <4.0.0")
        } else {
          println("WARNING: pubspec.yaml not found at ${pubspecFile.absolutePath}")
        }

        // Steps 3-5: Open Settings (⌘,) and navigate to Languages & Frameworks → Flutter.
        click()  // give the frame focus so ShowSettings is accepted
        driver.invokeAction("ShowSettings", now = false)

        settingsDialog {
          waitFound(20.seconds)
          println("Settings dialog is open.")
          openTreeSettingsSection("Languages & Frameworks", "Flutter")
          println("Navigated to Flutter settings.")

          // The version label shown below the SDK path field. Its text takes the form
          // "Flutter X.Y.Z • channel <name> • <url>" once flutter --version completes.
          val versionLabel = x("//div[@class='JBLabel' and contains(@text, 'channel')]")

          // Step 6: Capture the initial SDK version shown before making any changes.
          // reset() triggers an async flutter --version call when the page opens; wait for it.
          println("Waiting for initial version label...")
          versionLabel.waitFound(60.seconds)
          val initialVersion = versionLabel.getAllTexts().joinToString { it.text }
          println("Initial version: $initialVersion")

          // Steps 7-8: Set the SDK path to the alternate SDK.
          // JTextComponent.setText() updates the visual text and triggers the document listener
          // (which starts the async flutter --version call). BasicComboBoxEditor.getItem() reads
          // directly from editor.getText(), so apply() will read the correct value without needing
          // focusLost to commit it into the ComboBox model first.
          val sdkTextField = x("//div[@class='ComboBox']//div[@class='ExtendableTextField']", JTextFieldUI::class.java)
          sdkTextField.waitFound()
          sdkTextField.text = alternateSdkPath
          println("Set SDK path to: $alternateSdkPath")

          // Step 9: Assert the field value equals alternateSdkPath.
          assertEquals(alternateSdkPath, sdkTextField.text, "SDK path field should show the alternate path.")

          // Steps 10-12: Wait for the version label to update to the new SDK version and verify.
          // Poll for "3.38.10" specifically — the label already exists (showing the old version)
          // so waitFound() alone would return immediately with stale data.
          println("Waiting for version label to update to 3.38.10...")
          waitFor(timeout = 60.seconds) {
            versionLabel.getAllTexts().any { it.text.contains("3.38.10") }
          }
          val newVersion = versionLabel.getAllTexts().joinToString { it.text }
          println("New version: $newVersion")
          assertNotEquals(initialVersion, newVersion, "Version label should have changed after setting new SDK.")
          assertTrue(newVersion.contains("3.38.10"), "Version label should show 3.38.10, got: $newVersion")

          // Step 13: Click OK to save the settings.
          println("SDK text before OK click: ${sdkTextField.text}")
          x("//div[@text='OK']").waitFound().click()
          println("Settings saved and dialog closed.")
        }

        // Step 14: Reopen Settings and verify the path and version persisted.
        driver.waitForIndicators(30.seconds)
        click()
        driver.invokeAction("ShowSettings", now = false)

        settingsDialog {
          waitFound(20.seconds)
          openTreeSettingsSection("Languages & Frameworks", "Flutter")
          println("Reopened Flutter settings.")

          val sdkTextField = x("//div[@class='ComboBox']//div[@class='ExtendableTextField']", JTextFieldUI::class.java)
          sdkTextField.waitFound()
          assertEquals(alternateSdkPath, sdkTextField.text,
            "SDK path should persist after closing and reopening settings.")
          println("SDK path persisted: ${sdkTextField.text}")

          val versionLabel = x("//div[@class='JBLabel' and contains(@text, 'channel')]")
          println("Waiting for version label to show 3.38.10 after reopen...")
          waitFor(timeout = 60.seconds) {
            versionLabel.getAllTexts().any { it.text.contains("3.38.10") }
          }
          val persistedVersion = versionLabel.getAllTexts().joinToString { it.text }
          println("Persisted version: $persistedVersion")
          assertTrue(persistedVersion.contains("3.38.10"),
            "Version label should still show 3.38.10 after reopen, got: $persistedVersion")

          // Step 15: Click Cancel to close.
          x("//div[@text='Cancel']").waitFound().click()
          println("Settings dialog closed.")
        }
      }
    }
  }
}
