/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class FlutterSdkManagerStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    FlutterSdkManager.getInstance(project).checkForFlutterSdkChange()
  }
}