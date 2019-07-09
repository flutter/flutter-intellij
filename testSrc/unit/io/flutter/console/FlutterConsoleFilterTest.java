/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class FlutterConsoleFilterTest {
  @Rule
  public final ProjectFixture fixture = Testing.makeCodeInsightModule();

  @Rule
  public final TestDir tmp = new TestDir();

  VirtualFile contentRoot;
  String appDir;

  @Before
  public void setUp() throws Exception {
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
}
