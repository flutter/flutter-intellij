/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.panel
import io.flutter.FlutterBundle
import io.flutter.sdk.FlutterCreateAdditionalSettings
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
  var component: DialogPanel? = null

  fun initChannel() {
    val sdk = sdkGetter.get()
    channel = sdk.queryFlutterChannel(true)
    // Use the settings defined by `flutter config` as the default value for the check boxes.
    val platforms = sdk.queryConfiguredPlatforms(true)
    configAndroid = platforms.contains("enable-android")
    configIos = platforms.contains("enable-ios")
    configLinux = platforms.contains("enable-linux-desktop")
    configMacos = platforms.contains("enable-macos-desktop")
    configWeb = platforms.contains("enable-web")
    configWindows = platforms.contains("enable-windows-desktop")
  }

  fun shouldBeVisible(): Boolean {
    // TODO (messick) Keep this up-to-date with Flutter SDK changes.
    // We don't show platforms for the stable channel currently, but that will change.
    val ch = channel
    ch ?: return false
    return ch.id > ID.STABLE
  }

  fun panel(settings: FlutterCreateAdditionalSettings) =
      panel {
        assert(channel != null)
        val ch = channel!!.id
        row {
          cell(isVerticalFlow = false) {
            makeCheckBox(this, FlutterBundle.message("npw_platform_android"), settings.platformAndroidProperty, configAndroid, ch, ID.STABLE)
            makeCheckBox(this, FlutterBundle.message("npw_platform_ios"), settings.platformIosProperty, configIos, ch, ID.STABLE)
            makeCheckBox(this, FlutterBundle.message("npw_platform_linux"), settings.platformLinuxProperty, configLinux, ch, ID.DEV)
            makeCheckBox(this, FlutterBundle.message("npw_platform_macos"), settings.platformMacosProperty, configMacos, ch, ID.DEV)
            makeCheckBox(this, FlutterBundle.message("npw_platform_web"), settings.platformWebProperty, configWeb, ch, ID.BETA)
            makeCheckBox(this, FlutterBundle.message("npw_platform_windows"), settings.platformWindowsProperty, configWindows, ch, ID.DEV)
          }
        }
        row {
          label(FlutterBundle.message("npw_platform_availability_help")).apply {
            comment(FlutterBundle.message("npw_platform_availability_comment"))
          }
        }
        row {
          label(FlutterBundle.message("npw_platform_selection_help"))
        }
      }.apply { component = this }

  private fun makeCheckBox(context: Cell,
                           name: String,
                           property: InitializeOnceBoolValueProperty,
                           config: Boolean?,
                           chan: ID,
                           min: ID) {
    context.apply {
      val wasSelected = chan >= min && config == true
      property.initialize(wasSelected)
      checkBox(name,
          property.get(),
          actionListener = { _, checkBox ->
            property.set(checkBox.isSelected)
          }).apply {
        val names: List<String> = listOf(
            FlutterBundle.message("npw_platform_android"),
            FlutterBundle.message("npw_platform_ios"),
            FlutterBundle.message("npw_platform_web"))
        enabled(names.contains(name) || wasSelected)
      }
    }
  }
}
