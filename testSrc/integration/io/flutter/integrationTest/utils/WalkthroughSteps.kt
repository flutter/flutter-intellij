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
 *
 * While recording, the recorder also sets `WALKTHROUGH_STEP_PAUSE_MS`. Before each new step, and in [finish], the
 * test pauses for that long so the result of the previous step stays on screen and is captured.
 */
object WalkthroughSteps {
  private val stepsFile = System.getenv("WALKTHROUGH_STEPS_FILE")?.let(::File)
  private val pauseMs = (System.getenv("WALKTHROUGH_STEP_PAUSE_MS")?.toLongOrNull() ?: 0L).coerceIn(0L, 10_000L)
  private val timeFormat = DateTimeFormatter.ofPattern("HHmmssSSS")
  private var hasStep = false

  fun step(title: String, expect: String) {
    if (hasStep) pause()
    hasStep = true
    println("Walkthrough step: $title | $expect")
    stepsFile?.appendText("${LocalTime.now().format(timeFormat)}\t${clean(title)}\t${clean(expect)}\n")
  }

  /** Keeps the result of the last step on screen before the test ends. */
  fun finish() {
    if (hasStep) pause()
    hasStep = false
  }

  private fun pause() {
    if (pauseMs > 0) Thread.sleep(pauseMs)
  }

  private fun clean(text: String) = text.replace(Regex("[\\t\\r\\n|]+"), " ").trim()
}
