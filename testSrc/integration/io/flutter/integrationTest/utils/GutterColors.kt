/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

/**
 * Reads the color preview icons that IntelliJ shows in an editor's gutter, for example the squares the Flutter plugin's
 * `FlutterColorProvider` adds next to `Colors.blue` or `Color(0xFF...)`.
 *
 * Returns, for each 0-based line that has at least one color icon, the icon colors as `0xRRGGBB` (alpha dropped).
 * Lines whose colors were merged into one multi-color icon are not included.
 */
fun JEditorUiComponent.gutterColors(): Map<Int, List<Int>> = driver.withContext(OnDispatcher.EDT) {
  val gutter = cast(editor, EditorExRef::class).getGutterComponentEx()
  val result = sortedMapOf<Int, List<Int>>()
  for (line in 0 until document.getLineCount()) {
    val colors = gutter.getGutterRenderers(line).mapNotNull { it.iconColor(this) }
    if (colors.isNotEmpty()) result[line] = colors
  }
  result
}

/** Formats a `0xRRGGBB` color as `#RRGGBB`. */
fun Int.toHexColor(): String = "#%06X".format(this and 0xFFFFFF)

private fun GutterMarkRef.iconColor(driver: Driver): Int? = runCatching {
  driver.cast(getIcon() ?: return null, ColorIconRef::class).getIconColor().getRGB() and 0xFFFFFF
}.getOrNull()

@Remote("com.intellij.openapi.editor.ex.EditorEx")
internal interface EditorExRef {
  fun getGutterComponentEx(): EditorGutterRef
}

@Remote("com.intellij.openapi.editor.ex.EditorGutterComponentEx")
internal interface EditorGutterRef {
  fun getGutterRenderers(line: Int): List<GutterMarkRef>
}

@Remote("com.intellij.codeInsight.daemon.GutterMark")
internal interface GutterMarkRef {
  fun getIcon(): IconRef?
}

@Remote("javax.swing.Icon")
internal interface IconRef

@Remote("com.intellij.util.ui.ColorIcon")
internal interface ColorIconRef {
  fun getIconColor(): ColorRef
}

@Remote("java.awt.Color")
internal interface ColorRef {
  fun getRGB(): Int
}
