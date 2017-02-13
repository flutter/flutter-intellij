/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ProjectWatchTest {

  @Rule
  public final ProjectFixture fixture = Testing.makeEmptyModule();

  @Test
  public void shouldSendEventWhenProjectCloses() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final AtomicInteger callCount = new AtomicInteger();
      final ProjectWatch listen = ProjectWatch.subscribe(fixture.getProject(), callCount::incrementAndGet);

      ProjectManager.getInstance().closeProject(fixture.getProject());
      assertEquals(1, callCount.get());
    });
  }

  @Test
  public void shouldSendEventWhenModuleRootsChange() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final AtomicInteger callCount = new AtomicInteger();
      final ProjectWatch listen = ProjectWatch.subscribe(fixture.getProject(), callCount::incrementAndGet);

      ModuleRootModificationUtil.addContentRoot(fixture.getModule(), "testDir");
      assertEquals(1, callCount.get());
    });
  }

}
