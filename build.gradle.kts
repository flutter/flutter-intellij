/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

buildscript {
  repositories {
    mavenCentral()
    maven {
      url=uri("https://www.jetbrains.com/intellij-repository/snapshots/")
    }
    maven {
      url=uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
      url=uri("https://www.jetbrains.com/intellij-repository/releases")
    }
    gradlePluginPortal()
  }
}

plugins {
  id("org.jetbrains.intellij") version "1.17.2"
  id("org.jetbrains.kotlin.jvm") version "2.0.0-Beta4"
}

repositories {
//  mavenLocal()
  mavenCentral()
  maven {
    url=uri("https://www.jetbrains.com/intellij-repository/snapshots/")
  }
  maven {
    url=uri("https://oss.sonatype.org/content/repositories/snapshots/")
  }
  maven {
    url=uri("https://www.jetbrains.com/intellij-repository/releases")
  }
}

// Specify UTF-8 for all compilations so we avoid Windows-1252.
allprojects {
  tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
  }
  tasks.withType<Test>() {
    systemProperty("file.encoding", "UTF-8")
  }
}

val ide: String by project
val flutterPluginVersion: String by project
val javaVersion: String by project
val androidVersion: String by project
val dartVersion: String by project
val baseVersion: String by project
val name: String by project
val buildSpec: String by project
val smaliPlugin: String by project
val langPlugin: String by project
val ideVersion: String by project

group = "io.flutter"
version = flutterPluginVersion

java {
  sourceCompatibility = JavaVersion.toVersion(javaVersion)
  targetCompatibility = JavaVersion.toVersion(javaVersion)
}

intellij {
  pluginName.set(name)
  // This adds nullability assertions, but also compiles forms.
  instrumentCode.set(true)
  updateSinceUntilBuild.set(false)
  version.set(ideVersion)
  downloadSources.set(false)
  val pluginList = mutableListOf(
    project(":flutter-idea"), "java", "properties",
    "junit", "Git4Idea", "Kotlin", "gradle",
    "Groovy", "Dart:$dartVersion")
  // If 2023.3+, then "org.jetbrains.android:$androidVersion", otherwise "org.jetbrains.android",
  // see https://github.com/flutter/flutter-intellij/issues/7145
  if(ideVersion == "2023.3" || ideVersion == "2024.1" || ideVersion == "2024.2"|| ideVersion == "2024.3" || ideVersion == "2023.3.4") {
    pluginList.add("org.jetbrains.android:$androidVersion");
  } else {
    pluginList.add("org.jetbrains.android");
  }
  if (ide == "android-studio") {
    pluginList.add(smaliPlugin)
  }
  pluginList.add(langPlugin)
  if (ide == "android-studio") {
    type.set("AI")
    pluginList += listOf(project(":flutter-studio"))
  }
  plugins.set(pluginList)
}

tasks {
  buildSearchableOptions {
    enabled = false
  }
  patchPluginXml {
    version.set("233.13135.103")
    sinceBuild.set("233.13135.103")
  }
  prepareSandbox {
    dependsOn(":flutter-idea:prepareSandbox")
    if (ide == "android-studio") {
      dependsOn(":flutter-studio:prepareSandbox")
    }
  }
}

dependencies {
  implementation(project("flutter-idea", "instrumentedJar")) // Second arg is required to use forms
  if (ide == "android-studio") {
    implementation(project("flutter-studio"))
  }
}

tasks {
  instrumentCode {
    compilerVersion.set("233.13135.103")
  }
  instrumentTestCode {
    compilerVersion.set("233.13135.103")
  }
}
