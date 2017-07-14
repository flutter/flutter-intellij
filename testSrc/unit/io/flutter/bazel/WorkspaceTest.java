/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class WorkspaceTest {

  @Rule
  public final ProjectFixture fixture = Testing.makeEmptyModule();

  @Rule
  public final TestDir tmp = new TestDir();

  @Test
  public void canLoadWorkspaceWithoutConfigFile() throws Exception {
    final VirtualFile expectedRoot = tmp.ensureDir("abc");
    tmp.writeFile("abc/WORKSPACE", "");

    tmp.ensureDir("abc/dart");
    final VirtualFile contentRoot = tmp.ensureDir("abc/dart/something");
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), contentRoot.getPath());

    final Workspace w = Workspace.load(fixture.getProject());

    assertNotNull("expected a workspace", w);
    assertEquals(expectedRoot, w.getRoot());
    assertFalse("config shouldn't be there", w.hasPluginConfig());
  }

  @Test
  public void canLoadWorkspaceWithConfigFile() throws Exception {
    final VirtualFile expectedRoot = tmp.ensureDir("abc");
    tmp.writeFile("abc/WORKSPACE", "");

    tmp.ensureDir("abc/dart");
    final VirtualFile contentRoot = tmp.ensureDir("abc/dart/something");
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), contentRoot.getPath());

    tmp.ensureDir("abc/dart/config/intellij-plugins");
    tmp.writeFile("abc/dart/config/intellij-plugins/flutter.json",
                  "{\"daemonScript\": \"something_daemon.sh\"," +
                  "\"doctorScript\": \"something_doctor.sh\"}");
    tmp.writeFile("abc/something_daemon.sh", "");
    tmp.writeFile("abc/something_doctor.sh", "");

    final Workspace w = Workspace.load(fixture.getProject());

    assertNotNull("expected a workspace", w);
    assertEquals(expectedRoot, w.getRoot());
    assertEquals("something_daemon.sh", w.getDaemonScript());
    assertEquals("something_doctor.sh", w.getDoctorScript());
  }

  @Test
  public void canLoadWorkspaceWithConfigFileAndScriptInReadonly() throws Exception {
    final VirtualFile expectedRoot = tmp.ensureDir("abc");
    tmp.writeFile("abc/WORKSPACE", "");

    tmp.ensureDir("abc/dart");
    final VirtualFile contentRoot = tmp.ensureDir("abc/dart/something");
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), contentRoot.getPath());

    tmp.ensureDir("READONLY/abc/dart/config/intellij-plugins");
    tmp.writeFile("READONLY/abc/dart/config/intellij-plugins/flutter.json",
                  "{\"daemonScript\": \"scripts/flutter_daemon.sh\"," +
                  "\"doctorScript\": \"scripts/flutter_doctor.sh\"}");
    tmp.ensureDir("READONLY/abc/scripts");
    tmp.writeFile("READONLY/abc/scripts/flutter_daemon.sh", "");
    tmp.writeFile("READONLY/abc/scripts/flutter_doctor.sh", "");

    final Workspace w = Workspace.load(fixture.getProject());

    assertNotNull("expected a workspace", w);
    assertEquals(expectedRoot, w.getRoot());
    assertEquals("../READONLY/abc/scripts/flutter_daemon.sh", w.getDaemonScript());
    assertEquals("../READONLY/abc/scripts/flutter_doctor.sh", w.getDoctorScript());
  }

  @Test
  public void canDetectFlutterModule() throws Exception {
    tmp.ensureDir("abc");
    tmp.writeFile("abc/WORKSPACE", "");
    tmp.ensureDir("abc/dart/config/intellij-plugins");
    tmp.writeFile("abc/dart/config/intellij-plugins/flutter.json",
                  "{\"directoryPatterns\": [\"/mobile\"]}");

    final VirtualFile otherApp = tmp.ensureDir("abc/something/notmobile/other");
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), otherApp.getPath());
    Workspace w = Workspace.load(fixture.getProject());
    assertNotNull(w);
    assertTrue(w.hasPluginConfig());
    assertFalse("shouldn't have detected flutter module", w.usesFlutter(fixture.getModule()));

    final VirtualFile mobileApp = tmp.ensureDir("abc/something/mobile/hello");
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), mobileApp.getPath());
    w = Workspace.load(fixture.getProject());
    assertNotNull(w);
    assertTrue("failed to detect flutter module", w.usesFlutter(fixture.getModule()));

    // Check default configuration (no flutter.json)
    tmp.deleteFile("abc/dart/config/intellij-plugins/flutter.json");
    w = Workspace.load(fixture.getProject());
    assertNotNull(w);
    assertFalse("shouldn't have detected flutter module (no config)", w.usesFlutter(fixture.getModule()));

    final VirtualFile flutterApp = tmp.ensureDir("abc/something/flutter/hello");
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), flutterApp.getPath());
    assertTrue("should have detected flutter module (no config)", w.usesFlutter(fixture.getModule()));
  }
}
