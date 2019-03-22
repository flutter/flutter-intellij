/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui.fixtures

import com.intellij.openapi.project.Project
import com.intellij.testGuiFramework.fixtures.ToolWindowFixture
import org.fest.swing.core.Robot

class FlutterInspectorFixture(project: Project, robot: Robot) : ToolWindowFixture("Flutter Inspector", project, robot)
