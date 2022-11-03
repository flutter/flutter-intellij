/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

repositories {
  mavenLocal()
  maven {
    url=uri("https://www.jetbrains.com/intellij-repository/snapshots/")
  }

  mavenCentral()
  maven {
    url=uri("https://oss.sonatype.org/content/repositories/snapshots/")
  }
  maven {
    url=uri("https://www.jetbrains.com/intellij-repository/releases")
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
val smaliPlugin: String by project
val langPlugin: String by project

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
  val pluginList = mutableListOf("java", "properties", "junit", "Kotlin", "Git4Idea",
             "gradle", "Groovy", "org.jetbrains.android", "yaml", "Dart:$dartVersion")
  pluginList.add(smaliPlugin)
  pluginList.add(langPlugin)
  plugins.set(pluginList)
}

dependencies {
  testImplementation("org.powermock:powermock-api-mockito2:2.0.0")
  testImplementation("org.powermock:powermock-module-junit4:2.0.0")
  if (ide == "android-studio") {
    testImplementation(project(":flutter-studio"))
    testRuntimeOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/plugins",
                         "include" to listOf("**/*.jar"),
                         "exclude" to listOf("**/kotlin-compiler.jar", "**/kotlin-plugin.jar", "**/kotlin-stdlib-jdk8.jar"))))
    testRuntimeOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/lib",
                         "include" to listOf("*.jar"))))
    compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/plugins/git4idea/lib",
                         "include" to listOf("*.jar"))))
    testImplementation(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/android-studio/plugins/git4idea/lib",
                         "include" to listOf("*.jar"))))
  } else {
    compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/ideaIC/plugins/git4idea/lib",
                         "include" to listOf("*.jar"))))
    testImplementation(fileTree(mapOf("dir" to "${project.rootDir}/artifacts/ideaIC/plugins/git4idea/lib",
                         "include" to listOf("*.jar"))))
  }
  compileOnly("com.google.guava:guava:31.0.1-jre")
  compileOnly("com.google.code.gson:gson:2.9.0")
  testImplementation("com.google.guava:guava:31.0.1-jre")
  testImplementation("com.google.code.gson:gson:2.9.0")
  compileOnly(fileTree(mapOf("dir" to "${project.rootDir}/third_party/lib/jxbrowser",
                       "include" to listOf("*.jar"))))
  testImplementation(fileTree(mapOf("dir" to "${project.rootDir}/third_party/lib/jxbrowser",
                       "include" to listOf("*.jar"))))
  testImplementation("junit:junit:4.13.2")
  testImplementation(mapOf("group" to "org.mockito", "name" to "mockito-core", "version" to "2.2.2")) // 3.11.2 is latest
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

  buildSearchableOptions {
    enabled = false
  }

  instrumentCode {
    compilerVersion.set("$baseVersion")
  }

  instrumentTestCode {
    compilerVersion.set("$baseVersion")
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

