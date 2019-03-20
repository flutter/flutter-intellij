package io.flutter.tests.gui

import com.intellij.testGuiFramework.fixtures.ActionButtonFixture
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import org.fest.swing.edt.GuiActionRunner.execute
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.timing.Pause
import org.fest.swing.util.StringTextMatcher
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.awt.Container
import java.awt.event.KeyEvent

@RunWithIde(CommunityIde::class)
class InspectorTest : GuiTestCase() {

  @Test
  fun importSimpleProject() {
    // TODO(messick) killall Simulator
    ProjectCreator.importProject()
    ideFrame {
      selectSimulator()
      Pause.pause(10000) // Give the simulator time to get started
      findRunApplicationButton().click()
      runToolWindow.findContent("main.dart").waitForOutput(StringTextMatcher(".*Syncing files to device.*"), Timeouts.seconds10)
    }
  }

  fun IdeFrameFixture.selectSimulator() {
    // TODO(messick) Detect existing iOS Simulator and reuse it.
    selectDevice("Open iOS Simulator")
    // TODO(messick) Delete this old code once I don't need to refer to it anymore.
//    navigationBar {
//      val selector = button("<no devices>")
//      assert(exists { selector }) { "Button `<no devices>` not found on Navigation bar" }
//      selector.click()
//    }
//    val deviceSelector = combobox("<no devices>", Timeouts.seconds03)
//    deviceSelector.selectItem("Open iOS Simulator")
  }

  fun IdeFrameFixture.selectDevice(devName: String) {
    val runButton = findRunApplicationButton()
    val actionToolbarContainer = execute<Container>(object : GuiQuery<Container>() {
      @Throws(Throwable::class)
      override fun executeInEDT(): Container? {
        return runButton.target().getParent()
      }
    })
    assertNotNull(actionToolbarContainer)

    // These next two lines look like the right way to select the simulator, but it does not work.
//    val comboBoxActionFixture = ComboBoxActionFixture.findComboBoxByText(robot(), actionToolbarContainer!!, "<no devices>")
//    comboBoxActionFixture.selectItem(devName)
    // Need to get focus on the combo box but the ComboBoxActionFixture.click() method is private, so it is inlined here.
    val selector = button("<no devices>")
    val comboBoxButtonFixture = JButtonFixture(robot(), selector.target())
    GuiTestUtilKt.waitUntil("ComboBoxButton will be enabled", Timeouts.minutes02) {
      GuiTestUtilKt.computeOnEdt { comboBoxButtonFixture.target().isEnabled } ?: false
    }
    comboBoxButtonFixture.click()
    robot().pressAndReleaseKey(KeyEvent.VK_DOWN)
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
    robot().waitForIdle()
  }

  private fun IdeFrameFixture.findActionButtonByActionId(actionId: String): ActionButtonFixture {
    var button: ActionButtonFixture? = null
    ideFrame {
      button = ActionButtonFixture.fixtureByActionId(target(), robot(), actionId)
    }
    return button!!
  }

}
