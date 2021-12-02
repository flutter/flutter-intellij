/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

repositories {
  mavenCentral()
  maven {
    url=uri("https://oss.sonatype.org/content/repositories/snapshots/")
  }
}

plugins {
  id("java")
  id("kotlin")
  id("org.jetbrains.intellij")
}

val ide: String by project
val flutterPluginVersion: String by project
val javaVersion: String by project
val dartVersion: String by project
val baseVersion: String by project

group = "io.flutter"
version = flutterPluginVersion

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
  kotlinOptions {
    jvmTarget = javaVersion
  }
}

java {
  sourceCompatibility = JavaVersion.toVersion(javaVersion)
  targetCompatibility = JavaVersion.toVersion(javaVersion)
}

intellij {
  // This adds nullability assertions, but also compiles forms.
  instrumentCode.set(true)
  updateSinceUntilBuild.set(false)
  downloadSources.set(false)
  localPath.set("${project.rootDir.absolutePath}/artifacts/$ide")
  val pluginList = mutableListOf("java", "Dart:$dartVersion", "properties", "junit",
             "gradle", "Groovy", "smali", "IntelliLang", "org.jetbrains.android")
  plugins.set(pluginList)
}

dependencies {
  compileOnly(project(":flutter-idea"))
  testImplementation(project(":flutter-idea"))
  compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/lib",
                         "include" to listOf("*.jar"))))
  testImplementation(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/lib",
                         "include" to listOf("*.jar"))))
  compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/plugins",
                         "include" to listOf("**/*.jar"),
                         "exclude" to listOf("**/kotlin-compiler.jar", "**/kotlin-plugin.jar"))))
  testImplementation(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/plugins",
                         "include" to listOf("**/*.jar"),
                         "exclude" to listOf("**/kotlin-compiler.jar", "**/kotlin-plugin.jar"))))
}

sourceSets {
  main {
    java.srcDirs(listOf(
      "src",
      "third_party/vmServiceDrivers",
      //"resources"
    ))
    // Add kotlin.srcDirs if we start using Kotlin in the main plugin.
    //resources.srcDirs(listOf(
      //"src",
      //project(":flutter-idea").sourceSets.main.get().resources
      //"resources",
    //))
  }
}

tasks {

  buildSearchableOptions {
    enabled = false
  }

  instrumentCode {
    compilerVersion.set("$baseVersion")
  }

  instrumentTestCode {
    compilerVersion.set("$baseVersion")
  }
}
