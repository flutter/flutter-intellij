/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.util

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.NonNls
import java.io.PrintStream

/* A riff on org.jetbrains.kotlin.utils.PrintingLogger */
class PrintingLogger(private val out: PrintStream) : Logger() {
  override fun isDebugEnabled() = true

  override fun debug(@NonNls message: String?) = out.println(message)

  override fun debug(t: Throwable?) {
    t?.printStackTrace(this.out)
  }

  override fun debug(@NonNls message: String?, t: Throwable?) {
    debug(message)
    debug(t)
  }

  override fun info(@NonNls message: String?) = debug(message)

  override fun info(@NonNls message: String?, t: Throwable?) = debug(message, t)

  override fun warn(@NonNls message: String?, t: Throwable?) = debug(message, t)

  override fun error(@NonNls message: String?, t: Throwable?, @NonNls vararg details: String) {
    debug(message, t)

    for (detail in details) {
      debug(detail)
    }
  }

  companion object {
    val SYSTEM_OUT: Logger = PrintingLogger(System.out)
  }
}
