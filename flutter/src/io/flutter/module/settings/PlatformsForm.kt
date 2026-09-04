/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import io.flutter.FlutterBundle
import io.flutter.sdk.FlutterCreateAdditionalSettings

class PlatformsForm {
  private var configAndroid: Boolean = true
  private var configIos: Boolean = true
  private var configLinux: Boolean = true
  private var configMacos: Boolean = true
  private var configWeb: Boolean = true
  private var configWindows: Boolean = true
  var component: DialogPanel? = null

  fun panel(settings: FlutterCreateAdditionalSettings) =
    panel {
      row {
        makeCheckBox(FlutterBundle.message("npw_platform_android"), settings.platformAndroidProperty, configAndroid)
        makeCheckBox(FlutterBundle.message("npw_platform_ios"), settings.platformIosProperty, configIos)
        makeCheckBox(FlutterBundle.message("npw_platform_linux"), settings.platformLinuxProperty, configLinux)
        makeCheckBox(FlutterBundle.message("npw_platform_macos"), settings.platformMacosProperty, configMacos)
        makeCheckBox(FlutterBundle.message("npw_platform_web"), settings.platformWebProperty, configWeb)
        makeCheckBox(FlutterBundle.message("npw_platform_windows"), settings.platformWindowsProperty, configWindows)
      }
      row {
        label(FlutterBundle.message("npw_platform_selection_help"))
      }
    }.apply { component = this }

  private fun Row.makeCheckBox(
    name: String,
    property: InitializeOnceBoolValueProperty,
    config: Boolean?
  ) {
    val wasSelected = config == true
    property.initialize(wasSelected)

    val names: List<String> = listOfNotNull(
      FlutterBundle.message("npw_platform_android"),
      FlutterBundle.message("npw_platform_ios"),
      FlutterBundle.message("npw_platform_web"),
      FlutterBundle.message("npw_platform_windows"),
      FlutterBundle.message("npw_platform_linux"),
      FlutterBundle.message("npw_platform_macos"),
    )

    checkBox(name).actionListener { _, button -> property.set(button.isSelected) }.selected(property.get())
      .enabledIf(ComponentPredicate.fromValue(names.contains(name) || wasSelected))
  }
}
