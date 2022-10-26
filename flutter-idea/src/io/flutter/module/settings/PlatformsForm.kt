/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import io.flutter.FlutterBundle
import io.flutter.sdk.FlutterCreateAdditionalSettings
import io.flutter.sdk.FlutterSdk
import io.flutter.sdk.FlutterSdkChannel
import io.flutter.sdk.FlutterSdkChannel.ID
import java.util.function.Supplier

class PlatformsForm() {

  var channel: FlutterSdkChannel? = null
  var configAndroid: Boolean = true
  var configIos: Boolean = true
  var configLinux: Boolean = true
  var configMacos: Boolean = true
  var configWeb: Boolean = true
  var configWindows: Boolean = true
  var component: DialogPanel? = null

  fun panel(settings: FlutterCreateAdditionalSettings) =
    panel {
      row {
        cell(isVerticalFlow = false) {
          makeCheckBox(this, FlutterBundle.message("npw_platform_android"), settings.platformAndroidProperty, configAndroid)
          makeCheckBox(this, FlutterBundle.message("npw_platform_ios"), settings.platformIosProperty, configIos)
          makeCheckBox(this, FlutterBundle.message("npw_platform_linux"), settings.platformLinuxProperty, configLinux)
          makeCheckBox(this, FlutterBundle.message("npw_platform_macos"), settings.platformMacosProperty, configMacos)
          makeCheckBox(this, FlutterBundle.message("npw_platform_web"), settings.platformWebProperty, configWeb)
          makeCheckBox(this, FlutterBundle.message("npw_platform_windows"), settings.platformWindowsProperty, configWindows)
        }
      }
      row {
        label(FlutterBundle.message("npw_platform_selection_help"))
      }
    }.apply { component = this }

  private fun makeCheckBox(context: Cell,
                           name: String,
                           property: InitializeOnceBoolValueProperty,
                           config: Boolean?) {
    context.apply {
      val wasSelected = config == true
      property.initialize(wasSelected)
      checkBox(name,
               property.get(),
               actionListener = { _, checkBox ->
                 property.set(checkBox.isSelected)
               }).apply {
        val names: List<String> = listOfNotNull(
          FlutterBundle.message("npw_platform_android"),
          FlutterBundle.message("npw_platform_ios"),
          FlutterBundle.message("npw_platform_web"),
          FlutterBundle.message("npw_platform_windows"),
          FlutterBundle.message("npw_platform_linux"),
          FlutterBundle.message("npw_platform_macos"),
        )
        enabled(names.contains(name) || wasSelected)
      }
    }
  }
}
