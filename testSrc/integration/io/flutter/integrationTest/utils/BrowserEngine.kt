/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

/**
 * Chooses which embedded browser the Flutter plugin uses for the Inspector, DevTools, and other web-based tool windows.
 *
 * The plugin uses JxBrowser by default, which needs a license key in `resources/jxbrowser/jxbrowser.properties` at
 * build time. Only release builds have one, so a locally built plugin normally can't use JxBrowser. The other engine is
 * the IDE's own JCEF browser (Settings > Languages & Frameworks > Flutter > "Enable JCEF browser").
 *
 * `FLUTTER_TEST_BROWSER` selects the engine:
 * - `auto` (default): JxBrowser if the plugin under test contains a license key, otherwise JCEF.
 * - `jxbrowser`: JxBrowser, even without a license. The plugin then shows its "license could not be found" fallback.
 * - `jcef`: JCEF.
 */
enum class BrowserEngine(val displayName: String) {
  JXBROWSER("JxBrowser"),
  JCEF("JCEF");

  companion object {
    /** The engine requested by `FLUTTER_TEST_BROWSER`, resolving `auto` against the plugin under test. */
    fun fromEnvironment(): BrowserEngine =
      when (val choice = System.getenv("FLUTTER_TEST_BROWSER")?.lowercase()?.ifBlank { null } ?: "auto") {
        "jxbrowser" -> JXBROWSER
        "jcef" -> JCEF
        "auto" -> if (pluginHasJxBrowserLicense()) JXBROWSER else JCEF
        else -> error("FLUTTER_TEST_BROWSER must be auto, jxbrowser, or jcef, not '$choice'")
      }

    /**
     * True if the plugin archive under test (`path.to.build.plugin`) contains a JxBrowser license key, the same file
     * `JxBrowserUtils.getJxBrowserKey()` reads at runtime.
     */
    fun pluginHasJxBrowserLicense(): Boolean {
      val archive = System.getProperty("path.to.build.plugin")?.let(::File) ?: return false
      if (!archive.isFile) return false
      ZipFile(archive).use { zip ->
        val pluginJars = zip.entries().asSequence()
          .filter { it.name.matches(Regex("[^/]+/lib/flutter-intellij[^/]*\\.jar")) }
          .toList()
        for (entry in pluginJars) {
          val jar = File.createTempFile("flutter-plugin", ".jar")
          try {
            zip.getInputStream(entry).use { input -> jar.outputStream().use { input.copyTo(it) } }
            ZipFile(jar).use { pluginJar ->
              val properties = pluginJar.getEntry("jxbrowser/jxbrowser.properties") ?: return@use
              val key = Properties().apply { pluginJar.getInputStream(properties).use { load(it) } }
                .getProperty("jxbrowser.license.key")
              if (!key.isNullOrBlank()) return true
            }
          }
          finally {
            jar.delete()
          }
        }
      }
      return false
    }
  }

  /**
   * Makes the running IDE use this engine. The plugin reads the setting each time a Flutter app starts, so call this
   * after the IDE is up and before the app is launched.
   */
  fun applyTo(driver: Driver) {
    driver.service(PropertiesComponentRef::class).updateValue(ENABLE_JCEF_BROWSER_KEY, this == JCEF)
    println("Embedded browser: $displayName")
  }
}

/** The key `FlutterSettings.isEnableJcefBrowser()` reads. */
private const val ENABLE_JCEF_BROWSER_KEY = "io.flutter.enableJcefBrowser"

@Remote("com.intellij.ide.util.PropertiesComponent")
interface PropertiesComponentRef {
  fun updateValue(name: String, newValue: Boolean): Boolean
}
