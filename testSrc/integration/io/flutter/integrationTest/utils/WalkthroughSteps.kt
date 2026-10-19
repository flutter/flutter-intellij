/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Announces the steps of a UI test for walkthrough recordings.
 *
 * Each call marks the start of a user-visible step: a short [title] (used as the screenshot caption) and what the
 * viewer should [expect] to see while the step runs. When the walkthrough recorder runs the test, it sets
 * `WALKTHROUGH_STEPS_FILE`, and each step is appended there with a timestamp so screenshots can be matched to steps.
 * Outside the recorder, steps are only printed.
 */
object WalkthroughSteps {
  private val stepsFile = System.getenv("WALKTHROUGH_STEPS_FILE")?.let(::File)
  private val timeFormat = DateTimeFormatter.ofPattern("HHmmssSSS")

  fun step(title: String, expect: String) {
    println("Walkthrough step: $title | $expect")
    stepsFile?.appendText("${LocalTime.now().format(timeFormat)}\t${clean(title)}\t${clean(expect)}\n")
  }

  private fun clean(text: String) = text.replace(Regex("[\\t\\r\\n|]+"), " ").trim()
}
