/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;

public class WorkspaceCacheTest {

  @Rule
  public final ProjectFixture fixture = Testing.makeEmptyModule();

  @Rule
  public final TestDir tmp = new TestDir();

  private WorkspaceCache cache;

  @Before
  public void setUp() throws Exception {
    cache = WorkspaceCache.getInstance(fixture.getProject());
    assertNotNull(cache);

    tmp.ensureDir("abc");
    tmp.writeFile("abc/WORKSPACE", "");
    tmp.ensureDir("abc/dart/config/intellij-plugins");
    final VirtualFile contentRoot = tmp.ensureDir("abc/dart/something");

    Testing.runOnDispatchThread(
      () -> ModuleRootModificationUtil.addContentRoot(fixture.getModule(), contentRoot.getPath()));
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void shouldDetectConfigFileChanges() throws Exception {
    // This test causes stack traces to be logged at shutdown in local history: "Recursive records found".
    // (Unknown cause.)
    checkNoConfig();

    final String configPath = "abc/dart/config/intellij-plugins/flutter.json";

    tmp.writeFile(configPath, "{\"daemonScript\": \"first.sh\"}");
    tmp.writeFile("abc/first.sh", "");
    checkConfigSetting("first.sh");

    tmp.writeFile(configPath, "{}");
    checkConfigSetting(null);

    tmp.writeFile(configPath, "{\"daemonScript\": \"second.sh\"}");
    tmp.writeFile("abc/second.sh", "");
    checkConfigSetting("second.sh");

    tmp.deleteFile(configPath);
    checkNoConfig();
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void shouldDetectModuleRootChange() throws Exception {
    checkWorkspaceExists();
    removeContentRoot();
    checkNoWorkspaceExists();
  }

  private void removeContentRoot() throws Exception {
    Testing.runOnDispatchThread(
      () -> ModuleRootModificationUtil.updateModel(
        fixture.getModule(),
        (ModifiableRootModel model) -> model.removeContentEntry(model.getContentEntries()[0])));
  }

  private void checkNoWorkspaceExists() {
    assertNull("expected no workspace to exist", cache.getWhenReady());
  }


  private void checkWorkspaceExists() {
    assertNotNull("expected a workspace but it doesn't exist", cache.getWhenReady());
  }

  private void checkNoConfig() {
    final Workspace w = cache.getWhenReady();
    assertNotNull("expected a workspace but it doesn't exist", w);
    assertFalse("workspace has unexpected plugin config", w.hasPluginConfig());
  }

  private void checkConfigSetting(String expected) {
    final Workspace w = cache.getWhenReady();
    assertNotNull("expected a workspace but it doesn't exist", w);
    assertEquals(expected, w.getDaemonScript());
  }
}
