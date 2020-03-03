/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("PersistentStateUtil")

package com.android.tools.idea.npw.assetstudio.wizard

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.io.File
import java.util.TreeMap

/**
 * Convenience method for loading component state.
 */
fun PersistentStateComponent<PersistentState>.load(state: PersistentState?) {
  if (state == null) {
    noStateLoaded()
  } else {
    loadState(state)
  }
}

/**
 * Hierarchical storage of values keyed by ids. All values are stored as strings but convenience methods
 * are provided that accept and return boolean, integer, long, float, double, and enum values.
 */
class PersistentState {
  // Fields are public so that they can be serialized/deserialized.
  var values: MutableMap<String, String>? = null
  var children: MutableMap<String, PersistentState>? = null

  /**
   * Returns the string value with the given [id], or null if not found.
   */
  operator fun get(id: String): String? {
    return values?.get(id)
  }

  /**
   * Returns the string value with the given [id], or the [defaultValue] if not found.
   */
  fun get(id: String, defaultValue: String): String {
    return get(id) ?: defaultValue
  }

  /**
   * Returns the boolean value with the given [id], or the [defaultValue] if not found or not a boolean.
   */
  fun get(id: String, defaultValue: Boolean): Boolean {
    val value = get(id)
    return try {
      if (value == null) defaultValue else java.lang.Boolean.parseBoolean(value)
    }
    catch (e: NumberFormatException) {
      defaultValue
    }
  }

  /**
   * Returns the integer value with the given [id], or the [defaultValue] if not found or not an int.
   */
  fun get(id: String, defaultValue: Int): Int {
    val value = get(id)
    return try {
      if (value == null) defaultValue else Integer.parseInt(value)
    }
    catch (e: NumberFormatException) {
      defaultValue
    }
  }

  /**
   * Returns the long value with the given [id], or the [defaultValue] if not found or not a long.
   */
  fun get(id: String, defaultValue: Long): Long {
    val value = get(id)
    return try {
      if (value == null) defaultValue else java.lang.Long.parseLong(value)
    }
    catch (e: NumberFormatException) {
      defaultValue
    }
  }

  /**
   * Returns the float value with the given [id], or the [defaultValue] if not found or not a float.
   */
  fun get(id: String, defaultValue: Float): Float {
    val value = get(id)
    return try {
      if (value == null) defaultValue else java.lang.Float.parseFloat(value)
    }
    catch (e: NumberFormatException) {
      defaultValue
    }
  }

  /**
   * Returns the color value with the given [id], or the [defaultValue] if not found or not a valid color value.
   */
  fun get(id: String, defaultValue: Color): Color {
    val value = get(id)
    return try {
      if (value == null) defaultValue else ColorUtil.fromHex(value)
    }
    catch (e: IllegalArgumentException) {
      defaultValue
    }
  }

  /**
   * Returns the file value with the given [id], or the [defaultValue] if not found.
   */
  fun get(id: String, defaultValue: File): File {
    val value = get(id)
    return if (value == null) defaultValue else File(value)
  }

  /**
   * Returns the enum value with the given [id], or the [defaultValue] if not found or not a valid enum value.
   */
  fun <T : Enum<T>> get(id: String, defaultValue: T): T {
    val value = get(id)
    return try {
      if (value == null) defaultValue else java.lang.Enum.valueOf(defaultValue.javaClass, value)
    }
    catch (e: IllegalArgumentException) {
      defaultValue
    }
  }

  /**
   * Returns the decoded value with the given [id], or null if not found or not a valid value. The provided
   * [decoder] function is used to decode the [String] value. If the [decoder] function doesn't recognize
   * a string value, it should throw an [IllegalArgumentException].
   */
  fun <T> getDecoded(id: String, decoder: (String) -> T): T? {
    val value = get(id)
    return if (value == null) null else try { decoder(value) } catch (_: IllegalArgumentException) { null }
  }

  /**
   * If [value] is not null, associates it with the given [id], otherwise removes the value associated with the id.
   */
  operator fun set(id: String, value: String?) {
    if (value == null) {
      values?.remove(id)
    }
    else {
      getValuesMap().put(id, value)
    }
  }

  /**
   * Associates the given string value with the given [id] unless it is equal to the default value,
   * in which case the value is removed.
   */
  fun set(id: String, value: String?, defaultValue: String) {
    set(id, if (value == defaultValue) null else value)
  }

  /**
   * Associates the given boolean value with the given [id].
   */
  operator fun set(id: String, value: Boolean) {
    set(id, java.lang.Boolean.toString(value))
  }

  /**
   * Associates the given boolean value with the given [id] unless it is equal to the default value,
   * in which case the value is removed.
   */
  fun set(id: String, value: Boolean?, defaultValue: Boolean) {
    set(id, if (value == null || value == defaultValue) null else java.lang.Boolean.toString(value))
  }

  /**
   * Associates the given integer value with the given [id].
   */
  operator fun set(id: String, value: Int) {
    set(id, Integer.toString(value))
  }

  /**
   * Associates the given integer value with the given [id] unless it is equal to the default value,
   * in which case the value is removed.
   */
  fun set(id: String, value: Int?, defaultValue: Int) {
    set(id, if (value == null || value == defaultValue) null else java.lang.Integer.toString(value))
  }

  /**
   * Associates the given long value with the given [id].
   */
  operator fun set(id: String, value: Long) {
    set(id, java.lang.Long.toString(value))
  }

  /**
   * Associates the given long value with the given [id] unless it is equal to the default value,
   * in which case the value is removed.
   */
  fun set(id: String, value: Long?, defaultValue: Long) {
    set(id, if (value == null || value == defaultValue) null else java.lang.Long.toString(value))
  }

  /**
   * Associates the given float value with the given [id].
   */
  operator fun set(id: String, value: Float) {
    set(id, java.lang.Float.toString(value))
  }

  /**
   * Associates the given float value with the given [id] unless it is equal to the default value,
   * in which case the value is removed.
   */
  fun set(id: String, value: Float?, defaultValue: Float) {
    set(id, if (value == null || value == defaultValue) null else java.lang.Float.toString(value))
  }

  /**
   * Associates the given color value with the given [id].
   */
  operator fun set(id: String, value: Color) {
    set(id, ColorUtil.toHex(value))
  }

  /**
   * Associates the given color value with the given [id] unless it is equal to the default value,
   * in which case the value is removed.
   */
  fun set(id: String, value: Color?, defaultValue: Color) {
    set(id, if (value == null || value == defaultValue) null else ColorUtil.toHex(value))
  }

  /**
   * Associates the given file value with the given [id].
   */
  operator fun set(id: String, value: File) {
    set(id, value.path)
  }

  /**
   * Associates the given file value with the given [id] unless it is equal to the default value,
   * in which case the value is removed.
   */
  fun set(id: String, value: File?, defaultValue: File) {
    set(id, if (value == null || FileUtil.filesEqual(value, defaultValue)) null else value.path)
  }

  /**
   * Associates the given enum value with the given [id].
   */
  operator fun set(id: String, value: java.lang.Enum<*>) {
    set(id, value.name())
  }

  /**
   * Associates the given enum value with the given [id] unless it is equal to the default value,
   * in which case the value is removed.
   */
  fun <T : java.lang.Enum<*>> set(id: String, value: T?, defaultValue: T) {
    set(id, if (value == null || value == defaultValue) null else value.name())
  }

  /**
   * Encodes the given value of an arbitrary type and associates it with the given [id]. The provided [encoder]
   * function is used to convert the value to a [String].
   */
  fun <T> setEncoded(id: String, value: T?, encoder: (T) -> String) {
    set(id, if (value == null) null else encoder(value))
  }

  /**
   * The same as [setChild].
   */
  operator fun set(id: String, child: PersistentState) {
    setChild(id, child)
  }

  /**
   * Returns the child state with the given [id], or null if not found.
   */
  fun getChild(id: String): PersistentState? {
    return children?.get(id)
  }

  /**
   * Returns the child state with the given [id], or creates a new child state, associates it with the [id] and retuns it.
   */
  fun getOrCreateChild(id: String): PersistentState {
    var child = getChild(id)
    if (child == null) {
      child = PersistentState()
      getChildrenMap().put(id, child)
    }
    return child
  }

  /**
   * If [child] is not null, associates it with the given [id], otherwise removes the child associated with the [id].
   */
  fun setChild(id: String, child: PersistentState?) {
    if (child == null || child.isEmpty()) {
      children?.remove(id)
    }
    else {
      getChildrenMap().put(id, child)
    }
  }

  private fun getValuesMap(): MutableMap<String, String> {
    var values = this.values
    if (values == null) {
      values = TreeMap()
      this.values = values
    }
    return values
  }

  private fun getChildrenMap(): MutableMap<String, PersistentState> {
    var children = this.children
    if (children == null) {
      children = TreeMap()
      this.children = children
    }
    return children
  }

  private fun isEmpty(): Boolean {
    return values.isNullOrEmpty() && children.isNullOrEmpty()
  }
}

private fun <K, V> Map<K, V>?.isNullOrEmpty(): Boolean {
  return this == null || this.isEmpty()
}

