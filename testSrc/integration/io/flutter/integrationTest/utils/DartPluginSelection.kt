/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.plugins.PluginLatestForIde
import com.intellij.ide.starter.plugins.PluginSourceDescriptor
import com.intellij.ide.starter.plugins.PluginWithExactVersion
import com.intellij.ide.starter.utils.HttpClient
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Chooses the Dart plugin to install into the IDE under test, and refuses one that the IDE build can't load.
 *
 * A Dart plugin outside the IDE build's range is silently skipped by the platform, and then so is Flutter, which depends
 * on it; the test only finds out once the IDE is up. That happened repeatedly with `DART_PLUGIN_VERSION=506.1.0` (right
 * for IU 252) left set in a shell while the tests ran on IU 262. So the choice is checked against the plugin's own
 * `<idea-version since-build until-build>` before the IDE starts:
 *
 * - `DART_PLUGIN_ZIP=<zip>`: exactly that zip (explicit). Incompatible: fail.
 * - `DART_PLUGIN_VERSION=<version>`: exactly that Marketplace version (explicit). Incompatible: fail.
 * - `DART_PLUGIN_VERSION=latest`: the Marketplace's newest version compatible with the IDE build (explicit).
 * - Otherwise `dartPluginVersion` from `gradle.properties` (the version the plugin is built against; default). If the
 *   IDE build can't load it, for example with `-Pide.version` pinned to an older build, use the Marketplace's newest
 *   compatible version instead and say so. An explicit choice is never replaced, so certification runs install exactly
 *   what they asked for.
 */
object DartPluginSelection {

  /** What will be installed, and why. [descriptor] is null for a local zip. */
  data class Choice(
    val version: String,
    val source: String,
    val sinceBuild: String?,
    val untilBuild: String?,
    val descriptor: PluginSourceDescriptor?,
    val localZip: Path?,
  ) {
    val range: String get() = "${sinceBuild ?: "any"} to ${untilBuild ?: "any"}"
  }

  /** Metadata read from a plugin zip's main jar. */
  data class Descriptor(val version: String?, val sinceBuild: String?, val untilBuild: String?)

  /** The choice made for the most recent IDE context, for `run-result.json`. */
  @Volatile
  var last: Choice? = null
    private set

  /**
   * Resolves the Dart plugin for [ide]. [gradleDefault] is `dartPluginVersion` (from the `dart.plugin.version` system
   * property or `gradle.properties`) with a description of where it came from.
   */
  fun choose(ide: InstalledIde, gradleDefault: Pair<String, String>): Choice {
    last = null
    val build = ide.build
    val localZip = System.getenv("DART_PLUGIN_ZIP")?.trim()?.takeIf { it.isNotEmpty() }
    val fromEnv = System.getenv("DART_PLUGIN_VERSION")?.trim()?.takeIf { it.isNotEmpty() }

    val choice = when {
      localZip != null -> {
        val zip = Path.of(localZip)
        val d = read(zip)
        Choice(d.version ?: "local", "DART_PLUGIN_ZIP=$localZip", d.sinceBuild, d.untilBuild, null, zip)
      }
      fromEnv.equals("latest", ignoreCase = true) -> latestFor(ide, "DART_PLUGIN_VERSION=latest")
      fromEnv != null -> exact(fromEnv, "the DART_PLUGIN_VERSION environment variable")
      else -> {
        val (version, source) = gradleDefault
        val preferred = exact(version, source)
        if (isCompatible(build, preferred.sinceBuild, preferred.untilBuild)) {
          preferred
        }
        else {
          println("Dart plugin $version (from $source) supports builds ${preferred.range}, not $build; " +
                  "using the Marketplace's newest Dart plugin compatible with $build instead. " +
                  "Set DART_PLUGIN_VERSION to pin a version.")
          latestFor(ide, "the newest Marketplace version for $build ($version from $source does not support it)")
        }
      }
    }
    // Recorded before the check, so run-result.json names the rejected plugin too.
    last = choice
    println("Dart plugin ${choice.version} (supports builds ${choice.range}), from ${choice.source}")
    requireCompatible(choice, build)
    return choice
  }

  private fun exact(version: String, source: String): Choice {
    val descriptor = PluginWithExactVersion("Dart", version)
    val d = read(download(descriptor, version))
    return Choice(version, source, d.sinceBuild, d.untilBuild, descriptor, null)
  }

  private fun latestFor(ide: InstalledIde, source: String): Choice {
    val descriptor = PluginLatestForIde("Dart", ide)
    val d = read(download(descriptor, ide.build))
    return Choice(d.version ?: "latest-for-${ide.build}", source, d.sinceBuild, d.untilBuild, descriptor, null)
  }

  /** Downloads into the same cache file Starter's `installPluginFromPluginManager` uses, so the install reuses it. */
  private fun download(descriptor: PluginSourceDescriptor, cacheKey: String): Path {
    val target = GlobalPaths.instance.getCacheDirectoryFor("plugins").resolve(cacheKey).resolve("Dart.zip")
    target.parent.toFile().mkdirs()
    HttpClient.downloadIfMissing(descriptor.downloadUrl(), target, retries = 1)
    return target
  }

  private fun requireCompatible(choice: Choice, build: String) {
    if (isCompatible(build, choice.sinceBuild, choice.untilBuild)) return
    error(
      "Dart plugin ${choice.version} (from ${choice.source}) supports IDE builds ${choice.range}, but this run uses " +
      "$build, so the IDE would skip both Dart and Flutter. Unset the override, set DART_PLUGIN_VERSION to a " +
      "compatible version (or 'latest'), or pick the IDE build with -Pide.version."
    )
  }

  /** Reads version and build range from the plugin's main jar (`<Name>/lib/<Name>-*.jar`) inside [zip]. */
  fun read(zip: Path): Descriptor {
    ZipFile(zip.toFile()).use { outer ->
      val jars = outer.entries().asSequence()
        .filter { Regex("""^[^/]+/lib/[^/]+\.jar$""").matches(it.name) && !it.name.contains("searchableOptions") }
        .sortedByDescending { it.name.substringAfterLast('/').startsWith("Dart") }
        .toList()
      for (jarEntry in jars) {
        ZipInputStream(outer.getInputStream(jarEntry)).use { jar ->
          generateSequence { jar.nextEntry }.forEach { e ->
            if (e.name == "META-INF/plugin.xml") {
              val xml = jar.readBytes().toString(Charsets.UTF_8)
              val ideaVersion = Regex("""<idea-version\b[^>]*>""").find(xml)?.value.orEmpty()
              return Descriptor(
                version = Regex("""<version>\s*([^<\s]+)\s*</version>""").find(xml)?.groupValues?.get(1),
                sinceBuild = Regex("""since-build\s*=\s*"([^"]*)"""").find(ideaVersion)?.groupValues?.get(1)?.ifBlank { null },
                untilBuild = Regex("""until-build\s*=\s*"([^"]*)"""").find(ideaVersion)?.groupValues?.get(1)?.ifBlank { null },
              )
            }
          }
        }
      }
    }
    error("No META-INF/plugin.xml found in the plugin jars of $zip")
  }

  /**
   * True when IDE [build] (e.g. `262.10968.63`) is within [since]..[until]. Bounds are build numbers that may end in
   * `*` or be shorter than [build] (`261.*` and `261` both admit every 261 build); null means unbounded.
   */
  fun isCompatible(build: String, since: String?, until: String?): Boolean {
    val b = parts(build)
    if (since != null && compare(b, parts(since)) < 0) return false
    if (until != null && compare(b, parts(until)) > 0) return false
    return true
  }

  /** Numeric components of a build number; `*` becomes null. A product prefix (`IU-`) is ignored. */
  private fun parts(build: String): List<Int?> =
    build.substringAfter('-').split('.').map { if (it == "*") null else it.toIntOrNull() ?: 0 }

  /**
   * Compares [build] with [bound] component by component. Once [bound] has a `*` or runs out with every earlier
   * component equal, the build counts as equal: `261.*` and `261` admit every 261 build as an until-bound, and `252`
   * admits every 252 build as a since-bound.
   */
  private fun compare(build: List<Int?>, bound: List<Int?>): Int {
    for (i in build.indices) {
      if (i >= bound.size) return 0
      val bv = bound[i] ?: return 0
      val v = build[i] ?: 0
      if (v != bv) return v.compareTo(bv)
    }
    return if (bound.drop(build.size).any { it != null && it > 0 }) -1 else 0
  }
}
