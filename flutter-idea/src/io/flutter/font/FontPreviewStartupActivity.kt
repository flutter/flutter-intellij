/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.font

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * This class functionality was previous provided by ProjectOpenListener.java, as part of
 * https://github.com/flutter/flutter-intellij/issues/6953, the functionality was moved over
 * as a ProjectActivity and backgroundPostStartupActivity extension.
 */
class FontPreviewStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Ensure this isn't part of testing
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    FontPreviewProcessor.analyze(project)
  }
}
