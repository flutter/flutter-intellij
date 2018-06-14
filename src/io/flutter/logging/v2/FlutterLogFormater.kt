/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.logging.v2

import io.flutter.logging.FlutterLog
import io.flutter.logging.FlutterLogEntry
import java.text.SimpleDateFormat

class FlutterLogFormater {

  fun format(flutterLogEntry: FlutterLogEntry): String = flutterLogEntry.run {
    "%-${MAX_TIMESTAMP_CHARACTER}s %-${MAX_LOG_LEVEL_CHARACTER}s %-10s %s".format(
        TIMESTAMP_FORMAT.format(timestamp).toString().subSequenceBound(MAX_TIMESTAMP_CHARACTER),
        levelName.subSequenceBound(MAX_LOG_LEVEL_CHARACTER),
        category.subSequenceBound(MAX_CATEGORY_CHARACTER),
        message
    )
  }

  companion object {
    private val TIMESTAMP_FORMAT = SimpleDateFormat("HH:mm:ss.SSS")
    private const val MAX_TIMESTAMP_CHARACTER = 13
    private const val MAX_LOG_LEVEL_CHARACTER = 6
    private const val MAX_CATEGORY_CHARACTER = 15

    private fun String.subSequenceBound(maxCharacters: Int): CharSequence = subSequence(0, Math.min(maxCharacters, length))
    private val FlutterLogEntry.levelName: String
      get() {
        val logLevel = FlutterLog.Level.forValue(level)
        return logLevel?.name ?: level.toString()
      }
  }
}
