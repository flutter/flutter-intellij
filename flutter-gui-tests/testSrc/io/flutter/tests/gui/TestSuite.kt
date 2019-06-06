/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui

import com.intellij.testGuiFramework.framework.GuiTestSuite
import com.intellij.testGuiFramework.framework.GuiTestSuiteRunner
import org.junit.runner.RunWith
import org.junit.runners.Suite

//*  gradle -Dtest.single=TestSuite clean test -Didea.gui.test.alternativeIdePath="<path_to_installed_IDE>"
// The log file is at flutter-gui-tests/build/idea-sandbox/system/log/idea.log
// The test report is at flutter-gui-tests/build/reports/tests/test/index.html
@RunWith(GuiTestSuiteRunner::class)
@Suite.SuiteClasses(SmokeTest::class, InspectorTest::class)
class TestSuite : GuiTestSuite()
