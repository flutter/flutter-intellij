/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

plugins {
  // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
  // https://github.com/JetBrains/intellij-platform-gradle-plugin/releases
  // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
  id("java")
  id("org.jetbrains.intellij.platform") version "2.5.0"
  id("org.jetbrains.kotlin.jvm") version "2.1.20"
}

val flutterPluginVersion = providers.gradleProperty("flutterPluginVersion").get()
val ideaProduct = providers.gradleProperty("ideaProduct").get()
val ideaVersion = providers.gradleProperty("ideaVersion").get()
val dartPluginVersion = providers.gradleProperty("dartPluginVersion").get()
// The Android Plugin version is only used if the ideaProduct is not "android-studio"
val androidPluginVersion = providers.gradleProperty("androidPluginVersion").get()
val sinceBuildInput = providers.gradleProperty("sinceBuild").get()
val untilBuildInput = providers.gradleProperty("untilBuild").get()
val javaVersion = providers.gradleProperty("javaVersion").get()
group = "io.flutter"

var jvmVersion: JvmTarget
if (javaVersion == "17") {
  jvmVersion = JvmTarget.JVM_17
} else if (javaVersion == "21") {
  jvmVersion = JvmTarget.JVM_21
} else {
  throw IllegalArgumentException("javaVersion must be defined in the product matrix as either \"17\" or \"21\", but is not for $ideaVersion")
}
kotlin {
  compilerOptions {
    apiVersion.set(KotlinVersion.KOTLIN_1_9)
    jvmTarget = jvmVersion
  }
}

var javaCompatibilityVersion: JavaVersion
if (javaVersion == "17") {
  javaCompatibilityVersion = JavaVersion.VERSION_17
} else if (javaVersion == "21") {
  javaCompatibilityVersion = JavaVersion.VERSION_21
} else {
  throw IllegalArgumentException("javaVersion must be defined in the product matrix as either \"17\" or \"21\", but is not for $ideaVersion")
}
java {
  sourceCompatibility = javaCompatibilityVersion
  targetCompatibility = javaCompatibilityVersion
}


dependencies {
  intellijPlatform {
    // Documentation on the default target platform methods:
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#default-target-platforms
    if (ideaProduct == "android-studio") {
      androidStudio(ideaVersion)
    } else { // if (ideaProduct == "IC") {
      intellijIdeaCommunity(ideaVersion)
    }
    testFramework(TestFrameworkType.Platform)

    // Plugin dependency documentation:
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#plugins
    val bundledPluginList = mutableListOf(
      "com.intellij.java",
      "com.intellij.properties",
      "JUnit",
      "Git4Idea",
      "org.jetbrains.kotlin",
      "org.jetbrains.plugins.gradle",
      "org.intellij.intelliLang",
    )
    if (ideaProduct == "android-studio") {
      bundledPluginList.add("org.jetbrains.android")
      bundledPluginList.add("com.android.tools.idea.smali")
    }
    val pluginList = mutableListOf("Dart:$dartPluginVersion")
    if (ideaProduct == "IC") {
      pluginList.add("org.jetbrains.android:$androidPluginVersion")
    }

    // Finally, add the plugins into their respective lists:
    // https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#project-setup
    bundledPlugins(bundledPluginList)
    plugins(pluginList)

    if (sinceBuildInput == "243" || sinceBuildInput == "251") {
      bundledModule("intellij.platform.coverage")
      bundledModule("intellij.platform.coverage.agent")
    }
    pluginVerifier()
  }
}

intellijPlatform {
  pluginConfiguration {
    version = flutterPluginVersion
    ideaVersion {
      sinceBuild = sinceBuildInput
      untilBuild = untilBuildInput
    }
  }

  // Verifier documentation
  // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification
  // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification-ides
  pluginVerification {
    // https://github.com/JetBrains/intellij-plugin-verifier/?tab=readme-ov-file#specific-options
    // https://github.com/JetBrains/intellij-plugin-verifier
    cliPath = file("../third_party/lib/verifier-cli-1.379-all.jar")
    failureLevel = listOf(
      // TODO(team) Ideally all of the following FailureLevels should be enabled:
      // TODO(team) Create a tracking issue for each of the following validations
//      VerifyPluginTask.FailureLevel.COMPATIBILITY_WARNINGS,
//      VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
//      VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
//      VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
      VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
//      VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
//      VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
      VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
      VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
//      VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
      VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
//      VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
    )
    verificationReportsFormats = VerifyPluginTask.VerificationReportsFormats.ALL
    subsystemsToCheck = VerifyPluginTask.Subsystems.ALL
    // Mute and freeArgs documentation
    // https://github.com/JetBrains/intellij-plugin-verifier/?tab=readme-ov-file#specific-options
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#mutePluginVerifierProblems
    freeArgs = listOf(
      "-mute",
      "TemplateWordInPluginId,ForbiddenPluginIdPrefix,TemplateWordInPluginName"
    )
    ides {
      if (ideaProduct == "android-studio") {
        ide(IntelliJPlatformType.AndroidStudio, ideaVersion)
      } else {
          ide(IntelliJPlatformType.IntellijIdeaCommunity, ideaVersion)
        }
      recommended()
//      select {
//        types = listOf(IntelliJPlatformType.AndroidStudio)
//        channels = listOf(ProductRelease.Channel.RELEASE)
//        sinceBuild = sinceBuildInput
//        untilBuild = untilBuildInput
//      }
    }
  }
}

dependencies {
  compileOnly("org.jetbrains:annotations:24.0.0")
  testImplementation("org.jetbrains:annotations:24.0.0")
  testImplementation("org.powermock:powermock-api-mockito2:2.0.9")
  testImplementation("org.powermock:powermock-module-junit4:2.0.9")
  testImplementation(mapOf("group" to "org.mockito", "name" to "mockito-core", "version" to "5.2.0"))
  if (ideaProduct == "android-studio") {
    testImplementation(project(":flutter-studio"))
    testRuntimeOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/plugins",
      "include" to listOf("**/*.jar"),
      "exclude" to listOf("**/kotlin-compiler.jar", "**/kotlin-plugin.jar", "**/kotlin-stdlib-jdk8.jar"))))
    compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/lib",
      "include" to listOf("*.jar"),
      "exclude" to listOf("**/annotations.jar"))))
    testRuntimeOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/lib",
      "include" to listOf("*.jar"))))
    compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/plugins/git4idea/lib",
      "include" to listOf("*.jar"))))
    testImplementation(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/plugins/git4idea/lib",
      "include" to listOf("*.jar"))))
  } else {
    compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/ideaIC/plugins/git4idea/lib",
      "include" to listOf("*.jar"))))
    compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/ideaIC/plugins/java/lib",
      "include" to listOf("*.jar"))))
    testImplementation(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/ideaIC/plugins/git4idea/lib",
      "include" to listOf("*.jar"))))
  }
  compileOnly("com.google.guava:guava:32.0.0-android")
  compileOnly("com.google.code.gson:gson:2.10.1")
  testImplementation("com.google.guava:guava:32.0.0-jre")
  testImplementation("com.google.code.gson:gson:2.10.1")
  testImplementation("junit:junit:4.13.2")
  runtimeOnly(fileTree(mapOf("dir" to "${project.rootDir}/third_party/lib/jxbrowser",
    "include" to listOf("*.jar"))))
  compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/third_party/lib/jxbrowser",
    "include" to listOf("*.jar"))))
  testImplementation(fileTree(mapOf("dir" to "${project.rootDir}/third_party/lib/jxbrowser",
    "include" to listOf("*.jar"))))
}

sourceSets {
  main {
    java.srcDirs(listOf(
      "src",
      "third_party/vmServiceDrivers"
    ))
    // Add kotlin.srcDirs if we start using Kotlin in the main plugin.
    resources.srcDirs(listOf(
      "src",
      "resources"
    ))
  }
  test {
    java.srcDirs(listOf(
      "src",
      "testSrc/unit",
      "third_party/vmServiceDrivers"
    ))
    resources.srcDirs(listOf(
      "resources",
      "testData",
      "testSrc/unit"
    ))
  }
}

tasks {
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
