/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WorkspaceCacheTest {

  @Rule
  public final ProjectFixture fixture = Testing.makeEmptyModule();

  @Rule
  public final TestDir tmp = new TestDir();

  private WorkspaceCache cache;

  @Before
  public void setUp() throws Exception {
    cache = fixture.getProject().getComponent(WorkspaceCache.class);

    tmp.ensureDir("abc");
    tmp.writeFile("abc/WORKSPACE", "");
    tmp.ensureDir("abc/dart/config/intellij-plugins");
    final VirtualFile contentRoot = tmp.ensureDir("abc/dart/something");

    Testing.runOnDispatchThread(
      () -> ModuleRootModificationUtil.addContentRoot(fixture.getModule(), contentRoot.getPath()));
  }

  @Test
  public void shouldDetectConfigFileChanges() throws Exception {
    // This test causes stack traces to be logged at shutdown in local history: "Recursive records found".
    // (Unknown cause.)
    cache.waitForIdle();
    checkNoConfig();

    final String configPath = "abc/dart/config/intellij-plugins/flutter.json";

    tmp.writeFile(configPath, "{\"start_flutter_daemon\": \"first\"}");
    cache.waitForIdle();
    checkConfigSetting("first");

    tmp.writeFile(configPath, "{}");
    cache.waitForIdle();
    checkConfigSetting(null);

    tmp.writeFile(configPath, "{\"start_flutter_daemon\": \"second\"}");
    cache.waitForIdle();
    checkConfigSetting("second");

    tmp.deleteFile(configPath);
    cache.waitForIdle();
    checkNoConfig();
  }
  
  @Test
  public void shouldDetectModuleRootChange() throws Exception {
    cache.waitForIdle();
    checkWorkspaceExists();

    removeContentRoot();
    cache.waitForIdle();
    checkNoWorkspaceExists();
  }

  private void removeContentRoot() throws Exception {
    Testing.runOnDispatchThread(
      () -> ModuleRootModificationUtil.updateModel(
        fixture.getModule(),
        (ModifiableRootModel model) -> model.removeContentEntry(model.getContentEntries()[0])));
  }

  private void checkNoWorkspaceExists() {
    assertNull("expected no workspace to exist", cache.getValue());
  }


  private void checkWorkspaceExists() {
    assertNotNull("expected a workspace but it doesn't exist", cache.getValue());
  }

  private void checkNoConfig() {
    final Workspace w = cache.getValue();
    assertNotNull("expected a workspace but it doesn't exist", w);
    assertNull("workspace has unexpected plugin config", w.getPluginConfig());
  }

  private void checkConfigSetting(String expected) {
    final Workspace w = cache.getValue();
    assertNotNull("expected a workspace but it doesn't exist", w);

    final PluginConfig c = w.getPluginConfig();
    assertNotNull("config file is missing", c);
    assertEquals(expected, c.getFlutterDaemonScript());
  }
}
