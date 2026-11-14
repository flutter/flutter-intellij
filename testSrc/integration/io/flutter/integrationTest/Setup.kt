/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.ide.starter.buildTool.GradleBuildTool
import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.utils.Git
import com.intellij.openapi.util.SystemInfo
import io.flutter.integrationTest.utils.Platform
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

/**
 * Setup class for configuring IntelliJ IDE UI tests using the IntelliJ IDE Starter framework.
 *
 * This class provides the necessary configuration to:
 * - Initialize the test environment with proper paths and dependencies
 * - Install the plugin under test into the IDE instance
 * - Configure VM options for optimal test execution
 * - Apply OS-specific settings for compatibility
 *
 * @see [IntelliJ IDE Starter Documentation](https://github.com/JetBrains/intellij-ide-starter)
 */
class Setup {

  /**
   * Custom GlobalPaths implementation that points to the project's build directory.
   * This ensures all test artifacts are stored within the project structure.
   */
  class TemplatePaths : GlobalPaths(Git.getRepoRoot().resolve("build"))


  companion object {

    init {
      // Configure dependency injection to use our custom paths
      di = DI.Companion {
        extend(di)
        bindSingleton<GlobalPaths>(overrides = true) { TemplatePaths() }
      }
    }

    /**
     * True when the IDE will be shown on the user's Wayland desktop (through XWayland), rather than on Xvfb, Xephyr,
     * or IDE Starter's own `xvfb-run` (used when `DISPLAY` is unset).
     */
    fun isWaylandDesktop(): Boolean = Platform.displayMode == Platform.DisplayMode.LINUX_WAYLAND_DESKTOP

    fun setupTestContextIC(hyphenateWithClass: String, projectInfoSpec: ProjectInfoSpec = NoProject): IDETestContext {
      return setupTestContext(
        "", IdeProductProvider.IC.copy(
          // TODO(team) should the version be fetched from some setting, i.e. System.getProperty("uiPlatformBuildVersion")
          buildNumber = "252.23892.409",
          buildType = BuildType.RELEASE.type
        ), projectInfoSpec
      )
    }

    fun setupTestContextIU(hyphenateWithClass: String, projectInfoSpec: ProjectInfoSpec = NoProject): IDETestContext {
      return setupTestContext(
        "", IdeProductProvider.IU.copy(
          // TODO(team) should the version be fetched from some setting, i.e. System.getProperty("uiPlatformBuildVersion")
          buildNumber = "252.23892.409",
          buildType = BuildType.RELEASE.type
        ), projectInfoSpec
      )
    }

    /**
     * Reads the Dart plugin version from the `dart.plugin.version` system property (set from `gradle.properties` by the
     * `integration` Gradle task), falling back to `gradle.properties` when running tests directly from the IDE.
     */
    private val dartPluginVersion: String by lazy {
      // DART_PLUGIN_VERSION overrides the version for the IDE under test only (the plugin is still built against
      // gradle.properties' dartPluginVersion). Needed when certifying on an older IDE build: Dart 510+ requires 253.
      System.getenv("DART_PLUGIN_VERSION")?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getProperty("dart.plugin.version")?.trim()?.takeIf { it.isNotEmpty() }
        ?: Properties().apply {
          Files.newBufferedReader(Git.getRepoRoot().resolve("gradle.properties")).use(::load)
        }.getProperty("dartPluginVersion")?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("dartPluginVersion is not configured in gradle.properties")
    }

    /** The IntelliJ IDEA build the UI tests use unless `ide.version` says otherwise. */
    const val DEFAULT_IU_BUILD = "262.10968.63"

    /**
     * The IDE build number to test on: the `ide.version` system property (forwarded from `-Pide.version` by the
     * `integration` Gradle task) when it is a build number such as `262.10968.63`, otherwise [DEFAULT_IU_BUILD].
     * The runner pins this for every run of a certification so red and green use the same IDE.
     */
    val ideBuildNumber: String by lazy {
      val requested = System.getProperty("ide.version")?.trim().orEmpty()
      when {
        requested.isEmpty() || requested.equals("latest", ignoreCase = true) -> DEFAULT_IU_BUILD
        Regex("""\d{3}\.\d+\.\d+""").matches(requested) -> requested
        else -> error("ide.version must be a build number like 262.10968.63 (got '$requested'); version-to-build resolution is not implemented yet")
      }
    }

    /**
     * IntelliJ IDEA 2026.2 (unified distribution), build [ideBuildNumber].
     */
    fun setupTestContextIU262(
      hyphenateWithClass: String,
      projectInfoSpec: ProjectInfoSpec = NoProject,
    ): IDETestContext {
      return setupTestContext(
        hyphenateWithClass, IdeProductProvider.IU.copy(
          buildNumber = ideBuildNumber,
          buildType = BuildType.RELEASE.type
        ), projectInfoSpec
      )
    }

    fun setupTestContextWS(hyphenateWithClass: String, projectInfoSpec: ProjectInfoSpec = NoProject): IDETestContext {
      return setupTestContext(
        "", IdeProductProvider.WS.copy(
          // TODO(team) should the version be fetched from some setting, i.e. System.getProperty("uiPlatformBuildVersion")
          buildNumber = "252.23892.411",
          buildType = BuildType.RELEASE.type
        ), projectInfoSpec
      )
    }

    /**
     * Sets up the test context for UI testing with IntelliJ IDE Starter.
     *
     * This method:
     * 1. Creates a test case with the specified IDE version
     * 2. Configures the test context with the built plugin
     * 3. Applies VM options for proper IDE behavior during tests
     * 4. Applies OS-specific configurations
     *
     * @param hyphenateWithClass The name of the test class (used for test identification)
     * @return IDETestContext configured for running UI tests
     *
     * @throws IllegalStateException if required system properties are not set
     */
    fun setupTestContext(
      hyphenateWithClass: String,
      ideInfo: IdeInfo,
      projectInfoSpec: ProjectInfoSpec = NoProject,
    ): IDETestContext {

      val testCase = TestCase(ideInfo, projectInfoSpec)

      return Starter.newContext(testName = hyphenateWithClass, testCase = testCase).apply {
        // Install the Flutter plugin: `flutter.plugin.zip` (from `-Pflutter.plugin.zip`, used by the certification
        // runner to load a deliberately broken build) wins over the zip that `buildPlugin` just made. With
        // FLUTTER_UI_TEST_WITHOUT_PLUGIN=true no Flutter plugin is installed at all: the control run in which every
        // UI test must fail on its own checks.
        val withoutPlugin = System.getenv("FLUTTER_UI_TEST_WITHOUT_PLUGIN").toBoolean()
        val pluginPath = System.getProperty("flutter.plugin.zip")?.takeIf { it.isNotBlank() }
                         ?: System.getProperty("path.to.build.plugin")
                         ?: error("Neither flutter.plugin.zip nor path.to.build.plugin is set")
        // DART_PLUGIN_ZIP installs a locally built Dart plugin (for example from a dart-intellij-third-party pull
        // request) instead of the Marketplace version.
        val localDartPlugin = System.getenv("DART_PLUGIN_ZIP")?.takeIf { it.isNotBlank() }
        val plugins = PluginConfigurator(this)
        if (withoutPlugin) {
          println("FLUTTER_UI_TEST_WITHOUT_PLUGIN is set: NOT installing the Flutter plugin")
        }
        else {
          println("Installing the Flutter plugin from $pluginPath")
          plugins.installPluginFromPath(Paths.get(pluginPath))
        }
        // Dart depends on LSP4IJ (the Dart plugin's LSP client); it isn't bundled with the IDE, so install it from
        // the Marketplace at the version the plugin builds against (lsp4ij in libs.versions.toml, forwarded as
        // lsp4ij.plugin.version), unless LSP4IJ_PLUGIN_VERSION overrides it.
        val lsp4ijVersion = System.getenv("LSP4IJ_PLUGIN_VERSION")?.trim()?.takeIf { it.isNotEmpty() }
                            ?: System.getProperty("lsp4ij.plugin.version")?.trim()?.takeIf { it.isNotEmpty() }
        if (lsp4ijVersion != null) {
          println("Installing LSP4IJ $lsp4ijVersion")
          plugins.installPluginFromPluginManager("com.redhat.devtools.lsp4ij", lsp4ijVersion)
        }
        if (localDartPlugin != null) {
          println("Installing the Dart plugin from $localDartPlugin")
          plugins.installPluginFromPath(Paths.get(localDartPlugin))
        }
        else {
          println("Installing the Dart plugin $dartPluginVersion from the Marketplace")
          plugins.installPluginFromPluginManager("Dart", dartPluginVersion)
        }
        withBuildTool<GradleBuildTool>()

        // Don't let the platform auto-open README.md when a project is opened for the first time. The Markdown
        // preview it brings up is a JCEF browser, and under Xvfb that browser has frozen the EDT for minutes on IU
        // 2025.2 (both out-of-process and in-process JCEF), which looks exactly like a plugin failure: nothing
        // renders, no toolbar actions appear. The setting is the user-visible advanced setting "Open README.md
        // file on project startup"; nothing under test depends on it. See testSrc/integration/specs/FINDINGS.md.
        val advancedSettings = paths.configDir.resolve("options").resolve("advancedSettings.xml")
        Files.createDirectories(advancedSettings.parent)
        Files.writeString(advancedSettings, """
          <application>
            <component name="AdvancedSettings">
              <option name="settings">
                <map>
                  <entry key="ide.open.readme.md.on.startup" value="false" />
                </map>
              </option>
            </component>
          </application>
        """.trimIndent())
      }.applyVMOptionsPatch {

        // === Common system properties for all operating systems ===

        // Required JVM arguments for module access
        addSystemProperty("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        addSystemProperty("--add-opens", "java.desktop/javax.swing=ALL-UNNAMED")

        // Core IDE configuration
        addSystemProperty("idea.trust.all.projects", true) // Trust all projects automatically
        addSystemProperty("jb.consents.confirmation.enabled", false) // Disable consent dialogs
        addSystemProperty("jb.privacy.policy.text", "<!--999.999-->") // Skip privacy policy
        addSystemProperty("ide.show.tips.on.startup.default.value", false) // No tips on startup

        // No JetBrains feedback surveys ("Tell us about your experience" and similar). These are registry keys of the
        // platform's feedback module; registry keys can be set as system properties.
        addSystemProperty("platform.feedback", false)
        addSystemProperty("csat.survey.enabled", false)
        addSystemProperty("editor.ux.survey.enabled", false)

        // Test framework configuration
        addSystemProperty("junit.jupiter.extensions.autodetection.enabled", true)
        addSystemProperty("shared.indexes.download.auto.consent", true)

        // UI testing specific
        addSystemProperty("expose.ui.hierarchy.url", true) // Enable UI hierarchy inspection
        addSystemProperty("ide.experimental.ui", true) // Use new UI for testing

        // ensure it does not open any project on startup
        addSystemProperty("ide.open.project.at.startup", false)

        // === OS-specific system properties ===

        when {
          SystemInfo.isMac -> {
            // macOS specific settings
            addSystemProperty("ide.mac.file.chooser.native", false) // Use Java file chooser
            addSystemProperty("ide.mac.message.dialogs.as.sheets", false) // Use regular dialogs
            addSystemProperty("jbScreenMenuBar.enabled", false) // Disable native menu bar
            addSystemProperty("ide.native.launcher", true) // Use native launcher
          }

          SystemInfo.isWindows -> {
            // Windows specific settings

          }

          SystemInfo.isLinux -> {
            // Linux specific settings
            addSystemProperty("ide.browser.jcef.enabled", true)
            // Keep JCEF in-process. IU 2025.2 defaults to out-of-process JCEF (registry
            // ide.browser.jcef.out-of-process.enabled=true); under Xvfb the cef_server handshake that the Markdown
            // preview of an auto-opened README.md triggers blocked the EDT for minutes (T4 green #1, 2026-11-13), so
            // nothing rendered and the device selector never appeared. On a desktop the same IDE came up in-process.
            // See testSrc/integration/specs/FINDINGS.md.
            addSystemProperty("jcef.remote.enabled", false)
            addSystemProperty("ide.native.launcher", false) // Avoid launcher issues on Linux

            // Force X11 (XWayland on Wayland sessions): the Driver's java.awt.Robot input does not work with JBR's native WLToolkit.
            addSystemProperty("awt.toolkit.name", "XToolkit")

            // On a Wayland desktop the IDE runs on XWayland, which only accepts synthetic input (XTest) through the
            // xdg-desktop-portal RemoteDesktop interface. Have java.awt.Robot use that portal directly: GNOME asks once
            // to allow remote interaction, and the JDK keeps a restore token (~/.java/robot/) for later runs.
            // On Xvfb or Xephyr (no WAYLAND_DISPLAY), plain XTest works and nothing changes.
            if (isWaylandDesktop()) {
              addSystemProperty("awt.robot.screenshotMethod", "dbusRemoteDesktop")
            }

            // X11/Wayland compatibility
            addSystemProperty("sun.java2d.uiScale.enabled", false)
            addSystemProperty("sun.java2d.xrender", false)
          }
        }

      }.addProjectToTrustedLocations()
    }
  }
}
