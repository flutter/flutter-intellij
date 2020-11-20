/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings

import com.intellij.ui.layout.panel
import io.flutter.sdk.FlutterSdk
import io.flutter.sdk.FlutterSdkChannel
import io.flutter.sdk.FlutterSdkChannel.ID
import java.util.function.Supplier

class PlatformsForm(getSdk: Supplier<out FlutterSdk>) {

  val sdkGetter = getSdk
  var channel: FlutterSdkChannel? = null
  var configAndroid: Boolean? = null
  var configIos: Boolean? = null
  var configLinux: Boolean? = null
  var configMacos: Boolean? = null
  var configWeb: Boolean? = null
  var configWindows: Boolean? = null

  fun initChannel() {
    val sdk = sdkGetter.get()
    channel = sdk.queryFlutterChannel(true)
    val platforms = sdk.queryConfiguredPlatforms(true)
    configAndroid = platforms.contains("enable-android");
    configIos = platforms.contains("enable-ios");
    configLinux = platforms.contains("enable-linux-desktop");
    configMacos = platforms.contains("enable-macos-desktop");
    configWeb = platforms.contains("enable-web");
    configWindows = platforms.contains("enable-windows-desktop");
  }

  fun shouldBeVisible(): Boolean {
    // TODO (messick) Keep this up-to-date with Flutter SDK changes.
    // We don't show platforms for the stable channel currently, but that will change.
    val ch = channel
    ch ?: return false
    return ch.id > ID.STABLE
  }

  fun panel() = panel() {
    assert(channel != null)
    val ch = channel!!.id
    row {
      cell(isVerticalFlow = false) {
        checkBox("Android").enabled(ch >= ID.STABLE && configAndroid == true)
        checkBox("iOS").enabled(ch >= ID.STABLE && configIos == true)
        checkBox("Linux").enabled(ch >= ID.DEV && configLinux == true)
        checkBox("MacOS").enabled(ch >= ID.DEV && configMacos == true)
        checkBox("Web").enabled(ch >= ID.BETA && configWeb == true)
        checkBox("Windows").enabled(ch >= ID.DEV && configWindows == true)
      }
    }
    row {
      label("Platform availability depends on the Flutter SDK channel, and on 'flutter config'.")
    }
  }
}
