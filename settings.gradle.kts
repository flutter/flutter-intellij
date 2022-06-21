/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

pluginManagement {
  repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    gradlePluginPortal()
  }
}

val ide: String by settings

include("flutter-idea")
if ("$ide" == "android-studio") {
  include("flutter-studio")
}
