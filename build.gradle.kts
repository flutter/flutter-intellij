/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import okhttp3.internal.immutableListOf
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("org.jetbrains:markdown:0.7.1")
  }
}

// Specify UTF-8 for all compilations so we avoid Windows-1252.
allprojects {
  tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
  }
  tasks.withType<Test> {
    systemProperty("file.encoding", "UTF-8")
  }
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

plugins {
  // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
  // https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform
  // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
  id("java") // Java support
  id("org.jetbrains.intellij.platform") version "2.7.0" // IntelliJ Platform Gradle Plugin
  id("org.jetbrains.kotlin.jvm") version "2.2.0" // Kotlin support
}

var flutterPluginVersion = providers.gradleProperty("flutterPluginVersion").get()
if (project.hasProperty("dev-version")) {
  val datestamp = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())
  flutterPluginVersion = project.property("dev-version").toString() + "-dev." + datestamp
}
val ideaVersion = providers.gradleProperty("ideaVersion").get()
val dartPluginVersion = providers.gradleProperty("dartPluginVersion").get()
val sinceBuildInput = providers.gradleProperty("sinceBuild").get()
val untilBuildInput = providers.gradleProperty("untilBuild").get()
val javaVersion = providers.gradleProperty("javaVersion").get()
group = "io.flutter"

// For debugging purposes:
println("flutterPluginVersion: $flutterPluginVersion")
println("ideaVersion: $ideaVersion")
println("dartPluginVersion: $dartPluginVersion")
println("sinceBuild: $sinceBuildInput")
println("untilBuild: $untilBuildInput")
println("javaVersion: $javaVersion")
println("group: $group")

var jvmVersion: JvmTarget
jvmVersion = when (javaVersion) {
  "17" -> {
    JvmTarget.JVM_17
  }

  "21" -> {
    JvmTarget.JVM_21
  }

  else -> {
    throw IllegalArgumentException("javaVersion must be defined in the product matrix as either \"17\" or \"21\", but is not for $ideaVersion")
  }
}
kotlin {
  compilerOptions {
    apiVersion.set(KotlinVersion.KOTLIN_2_1)
    jvmTarget = jvmVersion
  }
}

var javaCompatibilityVersion: JavaVersion
javaCompatibilityVersion = when (javaVersion) {
  "17" -> {
    JavaVersion.VERSION_17
  }

  "21" -> {
    JavaVersion.VERSION_21 // all later versions of java can build against the earlier versions
  }

  else -> {
    throw IllegalArgumentException("javaVersion must be defined in the product matrix as either \"17\" or \"21\", but is not for $ideaVersion")
  }
}
java {
  sourceCompatibility = javaCompatibilityVersion
  targetCompatibility = javaCompatibilityVersion
}

dependencies {
  intellijPlatform {
    // Documentation on the default target platform methods:
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#default-target-platforms
    androidStudio(ideaVersion)
    testFramework(TestFrameworkType.Platform)

    // Plugin dependency documentation:
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#plugins
    // https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#project-setup
    bundledPlugins(
      immutableListOf(
        "com.google.tools.ij.aiplugin",
        "com.intellij.java",
        "com.intellij.properties",
        "JUnit",
        "Git4Idea",
        "org.jetbrains.kotlin",
        "org.jetbrains.plugins.gradle",
        "org.jetbrains.plugins.yaml",
        "org.intellij.intelliLang",
        "org.jetbrains.android",
        "com.android.tools.idea.smali"
      )
    )
    plugin("Dart:$dartPluginVersion")

    if (sinceBuildInput == "243" || sinceBuildInput == "251") {
      bundledModule("intellij.platform.coverage")
      bundledModule("intellij.platform.coverage.agent")
    }
    pluginVerifier()
  }

  compileOnly("org.jetbrains:annotations:24.0.0")
  testImplementation("org.jetbrains:annotations:24.0.0")
  compileOnly("com.google.guava:guava:32.0.1-android")
  compileOnly("com.google.code.gson:gson:2.10.1")
  testImplementation("com.google.guava:guava:32.0.1-jre")
  testImplementation("com.google.code.gson:gson:2.10.1")
  testImplementation("junit:junit:4.13.2")
  implementation(
    fileTree(
      mapOf(
        "dir" to "${project.rootDir}/third_party/lib/jxbrowser",
        "include" to listOf("*.jar")
      )
    )
  )
}


intellijPlatform {
  pluginConfiguration {
    version = flutterPluginVersion
    ideaVersion {
      sinceBuild = sinceBuildInput
      untilBuild = untilBuildInput
    }
    changeNotes = provider {
      // This is gemini-provided code to turn the changelog into HTML. Without it, the changelog will display properly on the jetbrains
      // site, but not in the Plugins settings view.
      val flavour = GFMFlavourDescriptor()
      val parser = MarkdownParser(flavour)
      val markdownText = file("CHANGELOG.md").readText(Charsets.UTF_8)
      val parsedTree = parser.buildMarkdownTreeFromString(markdownText)
      HtmlGenerator(markdownText, parsedTree, flavour).generateHtml()
    }
  }

  // Verifier documentation
  // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification
  // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification-ides
  pluginVerification {
    // https://github.com/JetBrains/intellij-plugin-verifier/?tab=readme-ov-file#specific-options
    // https://github.com/JetBrains/intellij-plugin-verifier
    cliPath = file("./third_party/lib/verifier-cli-1.388-all.jar")
    failureLevel = listOf(
      // TODO(team) Ideally all of the following FailureLevels should be enabled:
      // https://github.com/flutter/flutter-intellij/issues/8361
      VerifyPluginTask.FailureLevel.COMPATIBILITY_WARNINGS,
      VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
//      VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES, // https://github.com/flutter/flutter-intellij/issues/7718
//      VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
      VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
//      VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
//      VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
      VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
      VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
      VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
      VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
//      VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
    )
    verificationReportsFormats = VerifyPluginTask.VerificationReportsFormats.ALL
    subsystemsToCheck = VerifyPluginTask.Subsystems.ALL
    
    ides {
      recommended()
    }
  }
}

sourceSets {
  main {
    java.srcDirs(
      listOf(
        "src",
        "third_party/vmServiceDrivers"
      )
    )
    // Add kotlin.srcDirs if we start using Kotlin in the main plugin.
    resources.srcDirs(
      listOf(
        "src",
        "resources"
      )
    )
  }
  test {
    java.srcDirs(
      listOf(
        "src",
        "testSrc/unit",
        "third_party/vmServiceDrivers"
      )
    )
    resources.srcDirs(
      listOf(
        "resources",
        "testData",
        "testSrc/unit"
      )
    )
  }
}

// Documentation for printProductsReleases:
// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#how-to-check-the-latest-available-eap-release
tasks {
  printProductsReleases {
    channels = listOf(ProductRelease.Channel.EAP)
    types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
    untilBuild = provider { null }

    doLast {
      productsReleases.get().max()
    }
  }
  prepareJarSearchableOptions {
    enabled = false
  }
  buildSearchableOptions {
    enabled = false
  }
  test {
    useJUnit()
    testLogging {
      showCauses = true
      showStackTraces = true
      showStandardStreams = true
      exceptionFormat = TestExceptionFormat.FULL
      events("skipped", "failed")
    }
  }
}

// A task to print the classpath used for compiling an IntelliJ plugin
// Run with `./gradlew printCompileClasspath --no-configuration-cache `
tasks.register("printCompileClasspath") {
  doLast {
    println("--- Begin Compile Classpath ---")
    configurations.getByName("compileClasspath").forEach { file ->
      println(file.absolutePath)
    }
    println("--- End Compile Classpath ---")
  }
}

// This finds the JxBrowser license key from the environment and writes it to a file.
// This is only used by the dev build on kokoro for now.
val writeLicenseKey = tasks.register("writeLicenseKey") {
  group = "build"
  description = "Writes the license key from an environment variable to a file."

  // Find the output file
  val outputDir = rootProject.file("resources/jxbrowser")
  val licenseFile = outputDir.resolve("jxbrowser.properties")
  outputs.file(licenseFile)

  doLast {
    // Read the license key from the environment variable
    val base = System.getenv("KOKORO_KEYSTORE_DIR")
    val id = System.getenv("FLUTTER_KEYSTORE_ID")
    val name = System.getenv("FLUTTER_KEYSTORE_JXBROWSER_KEY_NAME")

    val readFile = File(base + "/" + id + "_" + name)
    if (readFile.isFile) {
      val licenseKey = readFile.readText(Charsets.UTF_8)
      licenseFile.writeText("jxbrowser.license.key=$licenseKey")
    }
  }
}

tasks.named("buildPlugin") {
  dependsOn(writeLicenseKey)
}

tasks.named("processResources") {
  dependsOn(writeLicenseKey)
}

// TODO(helin24): Find a better way to skip checking this file for tests.
tasks.withType<ProcessResources>().configureEach {
  if (name == "processTestResources") {
    // This block will only execute for the 'processTestResources' task.
    // The context here is unambiguously the task itself.
    exclude("jxbrowser/jxbrowser.properties")
  }
}