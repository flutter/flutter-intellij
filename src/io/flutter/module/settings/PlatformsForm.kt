/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings

import com.android.tools.idea.observable.core.BoolValueProperty
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.layout.Cell
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
  val androidSelected = BoolValueProperty()
  val iosSelected = BoolValueProperty()
  val linuxSelected = BoolValueProperty()
  val macosSelected = BoolValueProperty()
  val webSelected = BoolValueProperty()
  val windowsSelected = BoolValueProperty()

  fun initChannel() {
    val sdk = sdkGetter.get()
    channel = sdk.queryFlutterChannel(true)
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
    if (ch.id > ID.STABLE) {
      return if (isSomeConfigEnabled()) {
        true
      } else {
        // In case someone disables android and ios with 'flutter config' but does not enable any others,
        // this ensures legacy behavior is preserved.
        androidSelected.set(true)
        iosSelected.set(true)
        false
      }
    }
    return false
  }

  fun panel() = panel {
    assert(channel != null)
    loadPlatformSettings()
    if (!isSomePlatformSelected()) {
      androidSelected.set(true)
      iosSelected.set(true)
    }
    val ch = channel!!.id
    row {
      cell(isVerticalFlow = false) {
        makeCheckBox(this, "Android", androidSelected, configAndroid, ch, ID.STABLE)
        makeCheckBox(this, "iOS", iosSelected, configIos, ch, ID.STABLE)
        makeCheckBox(this, "Linux", linuxSelected, configLinux, ch, ID.DEV)
        makeCheckBox(this, "MacOS", macosSelected, configMacos, ch, ID.DEV)
        makeCheckBox(this, "Web", webSelected, configWeb, ch, ID.BETA)
        makeCheckBox(this, "Windows", windowsSelected, configWindows, ch, ID.DEV)
      }
    }
    row {
      label("Platform availability depends on the Flutter SDK channel, and on 'flutter config'.")
    }
  }

  fun isSomeConfigEnabled(): Boolean =
    configAndroid ?: false || configIos ?: false || configLinux ?: false || configMacos ?: false || configWeb ?: false || configWindows ?: false

  fun isSomePlatformSelected(): Boolean =
    androidSelected.get() || iosSelected.get() || linuxSelected.get() || macosSelected.get() || webSelected.get() || windowsSelected.get()

  private fun makeCheckBox(context: Cell,
                           name: String,
                           property: BoolValueProperty,
                           config: Boolean?,
                           chan: ID,
                           min: ID) {
    context.apply {
      val wasSelected = chan >= min && config == true
      if (!wasSelected) property.set(false)
      checkBox(name,
               property.get(),
               actionListener = { _, checkBox ->
                 property.set(checkBox.isSelected())
               }).apply {
        enabled(wasSelected)
        applyIfEnabled()
      }
    }
  }

  fun loadPlatformSettings() {
    val instance = PropertiesComponent.getInstance()
    androidSelected.set(instance.getBoolean(PROPERTIES_ANDROID_KEY, true))
    iosSelected.set(instance.getBoolean(PROPERTIES_IOS_KEY, true))
    linuxSelected.set(instance.getBoolean(PROPERTIES_LINUX_KEY, false))
    macosSelected.set(instance.getBoolean(PROPERTIES_MACOS_KEY, false))
    webSelected.set(instance.getBoolean(PROPERTIES_WEB_KEY, false))
    windowsSelected.set(instance.getBoolean(PROPERTIES_WINDOWS_KEY, false))
  }

  fun savePlatformSettings() {
    val instance = PropertiesComponent.getInstance()
    instance.setValue(PROPERTIES_ANDROID_KEY, androidSelected.get(), false)
    instance.setValue(PROPERTIES_IOS_KEY, iosSelected.get(), false)
    instance.setValue(PROPERTIES_LINUX_KEY, linuxSelected.get(), false)
    instance.setValue(PROPERTIES_MACOS_KEY, macosSelected.get(), false)
    instance.setValue(PROPERTIES_WEB_KEY, webSelected.get(), false)
    instance.setValue(PROPERTIES_WINDOWS_KEY, windowsSelected.get(), false)
  }

  companion object {
    private const val PROPERTIES_ANDROID_KEY = "FLUTTER_NPW_ANDROID_SELECTED"
    private const val PROPERTIES_IOS_KEY = "FLUTTER_NPW_IOS_SELECTED"
    private const val PROPERTIES_LINUX_KEY = "FLUTTER_NPW_LINUX_SELECTED"
    private const val PROPERTIES_MACOS_KEY = "FLUTTER_NPW_MACOS_SELECTED"
    private const val PROPERTIES_WEB_KEY = "FLUTTER_NPW_WEB_SELECTED"
    private const val PROPERTIES_WINDOWS_KEY = "FLUTTER_NPW_WINDOWS_SELECTED"
  }
}
