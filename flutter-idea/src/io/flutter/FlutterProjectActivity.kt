/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

abstract class FlutterProjectActivity : ProjectActivity {

  protected val log = Logger.getInstance(this::class.java)
  abstract fun executeProjectStartup(project: Project)

  override suspend fun execute(project: Project) {
    runCatching {
      executeProjectStartup(project)
    }.onFailure {
      log.error(it)
    }
  }
}
