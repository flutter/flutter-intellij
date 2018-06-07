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
import com.android.tools.idea.tests.gui.framework.GuiTestSuiteRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterFrameFixture;
import com.intellij.ide.ui.UISettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * cd .../studio-master-dev
 * mkdir community
 * cd community
 * ln -s ../platform .
 */
@RunWith(GuiTestSuiteRunner.class)
public class ProjectWizardTest {
  @Rule public final FlutterGuiTestRule myGuiTest = new FlutterGuiTestRule();

  @Test
  public void createNewProjectWithDefaults() throws Exception {
    //import project
    FlutterFrameFixture ideFrameFixture = myGuiTest.ideFrame();
    EditorFixture editor = ideFrameFixture.getEditor();
    editor.waitUntilErrorAnalysisFinishes();

    //check toolbar and open if is hidden
    if (!UISettings.getInstance().getShowMainToolbar()) {
      //ideFrameFixture.invokeMenuPath("View", "Toolbar");
    }
    ideFrameFixture.invokeMenuPath("File", "New", "Project...");
  }
}
