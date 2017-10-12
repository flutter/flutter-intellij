/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.tests.gui;

import com.android.tools.idea.tests.gui.framework.FlutterGuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewFlutterProjectWizardFixture;
import io.flutter.tests.util.WizardUtils;
import org.fest.swing.exception.WaitTimedOutError;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public class NewProjectTest {
  @Rule public final FlutterGuiTestRule myGuiTest = new FlutterGuiTestRule();

  //@Test
  public void createNewProjectWithDefaults() {
    NewFlutterProjectWizardFixture wizard = myGuiTest.welcomeFrame().createNewProject();
    Exception ex = null;
    try {
      wizard.clickNext().clickNext().clickFinish();
    }
    catch (Exception e) {
      ex = e;
      wizard.clickCancel();
    }
    assertThat(ex).isNotNull();
    assertThat(ex.getClass()).isAssignableTo(WaitTimedOutError.class);
    assertThat(ex.getMessage()).isEqualTo("Timed out waiting for matching JButton");
  }

  @Test
  public void createNewApplicationWithDefaults() {
    WizardUtils.createNewApplication(myGuiTest);
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.waitUntilErrorAnalysisFinishes();
    String expected =
      "import 'package:flutter/material.dart';\n" +
      "\n" +
      "void main() {\n" +
      "  runApp(new MyApp());\n" +
      "}\n";

    assertEquals(expected, editor.getCurrentFileContents().substring(0, expected.length()));
  }
}
