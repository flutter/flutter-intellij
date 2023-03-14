/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ProjectWatchTest {

  @Rule
  public final ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyModule();

  @Test
  @Ignore
  public void shouldSendEventWhenProjectCloses() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final AtomicInteger callCount = new AtomicInteger();
      final ProjectWatch listen = ProjectWatch.subscribe(fixture.getProject(), callCount::incrementAndGet);

      ProjectManager.getInstance().closeProject(fixture.getProject());
      // The number of events fired is an implementation detail of the project manager. We just need at least one.
      assertNotEquals(0, callCount.get());
    });
  }

  @Test @Ignore
  public void shouldSendEventWhenModuleRootsChange() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final AtomicInteger callCount = new AtomicInteger();
      final ProjectWatch listen = ProjectWatch.subscribe(fixture.getProject(), callCount::incrementAndGet);

      VirtualFile[] contentRoots = ModuleRootManager.getInstance(fixture.getModule()).getContentRoots();
      VirtualFile dir = contentRoots[0].createChildDirectory(this, "testDir");
      ModuleRootModificationUtil.addContentRoot(fixture.getModule(), dir);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      // The number of events fired is an implementation detail of the project manager. We just need at least one.
      assertNotEquals(0, callCount.get());
    });
  }

}
