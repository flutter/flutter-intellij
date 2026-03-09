/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.openapi.util.SystemInfo
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Paths

/**
 * Manages a secondary Flutter SDK installation used as an alternate SDK path in tests.
 *
 * The SDK is cached at ~/.flutter_test_sdk and is never deleted between runs, since
 * downloading Flutter is expensive. On subsequent runs, the existing installation is reused.
 */
object FlutterTestSdk {

  private const val FLUTTER_VERSION = "3.38.10"

  /** Root directory that contains the extracted `flutter/` folder. */
  private val cacheRoot: java.io.File =
    Paths.get(System.getProperty("user.home"), ".flutter_test_sdk").toFile()

  /** Path to the `flutter` directory (i.e. the value to set as Flutter SDK path). */
  val sdkPath: String get() = cacheRoot.resolve("flutter").absolutePath

  private val flutterBin: java.io.File get() = cacheRoot.resolve("flutter/bin/flutter")

  private fun isArm64(): Boolean {
    val arch = System.getProperty("os.arch") ?: ""
    return arch == "aarch64" || arch == "arm64"
  }

  private fun downloadUrl(): String = when {
    SystemInfo.isMac && isArm64() ->
      "https://storage.googleapis.com/flutter_infra_release/releases/stable/macos/flutter_macos_arm64_${FLUTTER_VERSION}-stable.zip"
    SystemInfo.isMac ->
      "https://storage.googleapis.com/flutter_infra_release/releases/stable/macos/flutter_macos_${FLUTTER_VERSION}-stable.zip"
    SystemInfo.isLinux ->
      "https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz"
    else -> error("Unsupported OS for Flutter SDK download in tests")
  }

  /**
   * Ensures the alternate Flutter SDK is present on disk.
   *
   * If already installed, logs the location and returns immediately.
   * If missing, downloads and extracts it. The download can take several minutes
   * on the first run.
   *
   * @return the absolute path to the Flutter SDK root (suitable for use as the Flutter SDK path setting).
   */
  fun ensureInstalled(): String {
    if (flutterBin.exists()) {
      println("Alternate Flutter SDK already installed at: $sdkPath")
      return sdkPath
    }

    println("Alternate Flutter SDK not found. Downloading Flutter $FLUTTER_VERSION...")
    cacheRoot.mkdirs()

    val url = downloadUrl()
    println("Download URL: $url")

    if (SystemInfo.isLinux) {
      val tarFile = cacheRoot.resolve("flutter-download.tar.xz")
      downloadFile(url, tarFile)
      extractTarXz(tarFile, cacheRoot)
      tarFile.delete()
    } else {
      val zipFile = cacheRoot.resolve("flutter-download.zip")
      downloadFile(url, zipFile)
      extractZip(zipFile, cacheRoot)
      zipFile.delete()
    }

    check(flutterBin.exists()) { "Flutter binary not found after extraction at: ${flutterBin.absolutePath}" }
    println("Alternate Flutter SDK installed at: $sdkPath")
    return sdkPath
  }

  private fun downloadFile(url: String, destination: java.io.File) {
    println("Downloading ${destination.name}...")
    URI.create(url).toURL().openStream().use { input ->
      FileOutputStream(destination).use { output ->
        input.copyTo(output)
      }
    }
    println("Download complete: ${destination.length() / 1_048_576} MB")
  }

  /**
   * Extracts a zip archive using the system `unzip` command to preserve Unix
   * file permissions (executable bits), which Java's ZipInputStream does not handle.
   */
  private fun extractZip(zipFile: java.io.File, targetDir: java.io.File) {
    println("Extracting ${zipFile.name}...")
    val result = ProcessBuilder("unzip", "-q", zipFile.absolutePath, "-d", targetDir.absolutePath)
      .inheritIO()
      .start()
      .waitFor()
    check(result == 0) { "unzip exited with code $result" }
  }

  private fun extractTarXz(tarFile: java.io.File, targetDir: java.io.File) {
    println("Extracting ${tarFile.name}...")
    val result = ProcessBuilder("tar", "xJf", tarFile.absolutePath, "-C", targetDir.absolutePath)
      .inheritIO()
      .start()
      .waitFor()
    check(result == 0) { "tar exited with code $result" }
  }
}
