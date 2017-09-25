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
package io.flutter.testing;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.NewModuleDialogFixture;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static com.android.tools.idea.testing.FileSubject.file;

public class NewProjectTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void createNewAppModuleWithDefaults() throws Exception {
    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Flutter Project...")
      .chooseModuleType("Application")
      .clickNextToStep("Phone & Tablet Module")
//      .setProjectName("application-project")
      .clickNextToStep("Add an Activity to Mobile")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
//    assertAbout(file()).that(new File(guiTest.getProjectPath(), "application-module")).isDirectory();
  }
}
