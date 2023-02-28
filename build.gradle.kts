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
  }
}

plugins {
  id("org.jetbrains.intellij") version "1.13.1-SNAPSHOT"
  id("org.jetbrains.kotlin.jvm") version "1.8.20-Beta"
}

repositories {
  mavenLocal()
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
val dartVersion: String by project
val baseVersion: String by project
val name: String by project
val buildSpec: String by project
val smaliPlugin: String by project
val langPlugin: String by project

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
  localPath.set("${project.rootDir.absolutePath}/artifacts/$ide")
  downloadSources.set(false)
  val pluginList = mutableListOf(
    project(":flutter-idea"), "java", "properties",
    "junit", "Git4Idea", "Kotlin", "gradle", "org.jetbrains.android",
    "Groovy", "Dart:$dartVersion")
  pluginList.add(smaliPlugin)
  pluginList.add(langPlugin)
  if (ide == "android-studio") {
    pluginList += listOf(project(":flutter-studio"))
  } else if ("$buildSpec" == "2020.3") {
    pluginList += listOf("gradle-dsl-impl")
  }
  plugins.set(pluginList)
}

tasks {
  buildSearchableOptions {
    enabled = false
  }
}

dependencies {
  implementation(project("flutter-idea"))
  if (ide == "android-studio") {
    implementation(project("flutter-studio"))
  }
}

tasks {
  instrumentCode {
    compilerVersion.set("$baseVersion")
  }
  instrumentTestCode {
    compilerVersion.set("$baseVersion")
  }
}
