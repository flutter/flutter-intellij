/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

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
  // https://github.com/JetBrains/intellij-platform-gradle-plugin/releases
  id("org.jetbrains.intellij.platform") version "2.1.0"
  id("org.jetbrains.kotlin.jvm") version "2.0.0"
}

val flutterPluginVersion = providers.gradleProperty("flutterPluginVersion").get()
val ideaProduct = providers.gradleProperty("ideaProduct").get()
val ideaVersion = providers.gradleProperty("ideaVersion").get()
val dartPluginVersion = providers.gradleProperty("dartPluginVersion").get()
// The Android Plugin version is only used if the ideaProduct is not "android-studio"
val androidPluginVersion = providers.gradleProperty("androidPluginVersion").get()
val sinceBuildInput = providers.gradleProperty("sinceBuild").get()
val untilBuildInput = providers.gradleProperty("untilBuild").get()
group = "io.flutter"

// For debugging purposes:
println("flutterPluginVersion: $flutterPluginVersion")
println("ideaProduct: $ideaProduct")
println("ideaVersion: $ideaVersion")
println("dartPluginVersion: $dartPluginVersion")
println("androidPluginVersion: $androidPluginVersion")
println("sinceBuild: $sinceBuildInput")
println("untilBuild: $untilBuildInput")
println("group: $group")

kotlin {
  compilerOptions {
    apiVersion.set(KotlinVersion.KOTLIN_1_9)
    jvmTarget = JvmTarget.JVM_17
  }
}
val javaCompatibilityVersion = JavaVersion.VERSION_17
java {
  sourceCompatibility = javaCompatibilityVersion
  targetCompatibility = javaCompatibilityVersion
}

dependencies {
  intellijPlatform {
    if (ideaProduct == "android-studio") {
      create(IntelliJPlatformType.AndroidStudio, ideaVersion)
    } else {//if (ide == "ideaIC") {
      create(IntelliJPlatformType.IntellijIdeaCommunity, ideaVersion)
    }
    testFramework(TestFrameworkType.Platform)
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

    // The warning that "instrumentationTools()" is deprecated might be valid, however, this error is produced by Gradle IJ plugin version
    // 2.1.0 if this isn't included:
    //  Caused by: org.gradle.api.GradleException: No Java Compiler dependency found.
    //  Please ensure the `instrumentationTools()` entry is present in the project dependencies section along with the `intellijDependencies()` entry in the repositories section.
    //  See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    instrumentationTools()
//    pluginVerifier()
  }
}
//intellijPlatform {
//  pluginConfiguration {
//    version = flutterPluginVersion
//    ideaVersion {
//      sinceBuild = sinceBuildInput
//      untilBuild = untilBuildInput
//    }
//  }
//  // TODO (jwren) get the verifier to work, and enable in the github presubmit,
//  //  the com.teamdev dep is having the verifier fail
//  // Verifier documentation: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification-ides
//  pluginVerification {
//    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#mutePluginVerifierProblems
//    freeArgs = listOf(
//      "-mute",
//      "TemplateWordInPluginId"
//    )
//    ides {
//      if (ideaProduct == "android-studio") {
//        ide(IntelliJPlatformType.AndroidStudio, ideaVersion)
//      } else {
//          ide(IntelliJPlatformType.IntellijIdeaCommunity, ideaVersion)
//        }
//      recommended()
////      select {
////        types = listOf(IntelliJPlatformType.AndroidStudio)
////        channels = listOf(ProductRelease.Channel.RELEASE)
////        sinceBuild = sinceBuildInput
////        untilBuild = untilBuildInput
////      }
//    }
//  }
//}

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
}

dependencies {
  implementation(project("flutter-idea"))
  if (ideaProduct == "android-studio") {
    implementation(project("flutter-studio"))
  }
}

tasks {
  prepareJarSearchableOptions {
    enabled = false
  }
  buildSearchableOptions {
    enabled = false
  }
  prepareSandbox {
    dependsOn(":flutter-idea:prepareSandbox")
    if (ideaProduct == "android-studio") {
      dependsOn(":flutter-studio:prepareSandbox")
    }
  }
}