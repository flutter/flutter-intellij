/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import static org.junit.Assert.assertNotNull;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class FlutterConsoleFilterTest {
  @ClassRule
  public static final ProjectFixture fixture = Testing.makeEmptyProject();

  @ClassRule
  public static final TestDir tmp = new TestDir();

  static VirtualFile contentRoot;
  static String appDir;

  @BeforeClass
  public static void setUp() throws Exception {
    contentRoot = tmp.ensureDir("root");
    appDir = tmp.ensureDir("root/test").getPath();
    tmp.writeFile("root/test/widget_test.dart", "");
    Testing.runOnDispatchThread(
      () -> ModuleRootModificationUtil.addContentRoot(fixture.getModule(), contentRoot.getPath()));
  }

  @Test
  public void checkTestFileUrlLink() {
    final String line = "#4      main.<anonymous closure> (file://" + appDir + "/widget_test.dart:23:18)\n";
    final Filter.Result link = new FlutterConsoleFilter(fixture.getModule()).applyFilter(line, 659);
    assertNotNull(link);
  }

  @Test
  public void checkLaunchingLink() {
    String line = "Launching test/widget_test.dart on Android SDK built for x86 in debug mode...\n";
    Filter.Result link = new FlutterConsoleFilter(fixture.getModule()).applyFilter(line, line.length());
    assertNotNull(link);
  }

  @Test
  public void checkErrorMessage() {
    final String line = "test/widget_test.dart:23:18: Error: Expected ';' after this.";
    final Filter.Result link = new FlutterConsoleFilter(fixture.getModule()).applyFilter(line, line.length());
    assertNotNull(link);
  }
}
