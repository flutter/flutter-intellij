/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;

public class ProjectFixture extends AdaptedFixture<IdeaProjectTestFixture> {
  ProjectFixture(Factory<IdeaProjectTestFixture> factory) {
    super(factory);
  }

  public Project getProject() {
    return getInner().getProject();
  }

  public Module getModule() {
    return getInner().getModule();
  }
}
