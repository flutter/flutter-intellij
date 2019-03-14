package io.flutter.tests.gui

import com.intellij.testGuiFramework.framework.GuiTestSuite
import com.intellij.testGuiFramework.framework.GuiTestSuiteRunner
import org.junit.runner.RunWith
import org.junit.runners.Suite

//*  gradle -Dtest.single=TestSuite clean test -Didea.gui.test.alternativeIdePath="<path_to_installed_IDE>"
@RunWith(GuiTestSuiteRunner::class)
@Suite.SuiteClasses(SmokeTest::class)
class TestSuite : GuiTestSuite()
