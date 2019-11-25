/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class MainFileTest {

  @Rule
  public final ProjectFixture fixture = Testing.makeCodeInsightModule();

  @Rule
  public final TestDir tmp = new TestDir();

  VirtualFile contentRoot;
  String appDir;

  @Before
  public void setUp() throws Exception {
    contentRoot = tmp.ensureDir("root");
    appDir = tmp.ensureDir("root/work").getPath();
    tmp.writeFile("root/work/pubspec.yaml", "");
    tmp.ensureDir("root/work/lib");
    Testing.runOnDispatchThread(
      () -> ModuleRootModificationUtil.addContentRoot(fixture.getModule(), contentRoot.getPath()));
  }

  @Test
  public void shouldFindAppDirForValidFlutterApp() throws Exception {
    final String mainPath = tmp.writeFile("root/work/lib/main.dart",
                                          "import \"package:flutter/ui.dart\"\n" +
                                          "main() {}\n").getPath();

    final MainFile.Result result = Testing.computeOnDispatchThread(() -> MainFile.verify(mainPath, fixture.getProject()));
    if (!result.canLaunch()) {
      fail("Flutter app should be valid but got error: " + result.getError());
    }
    final MainFile main = result.get();
    assertEquals(mainPath, main.getFile().getPath());
    assertEquals(appDir, main.getAppDir().getPath());
    assertTrue(main.hasFlutterImports());
  }

  @Test
  public void shouldDetectErrors() throws Exception {
    checkInvalid(null, "hasn't been set");
    checkInvalid("notfound.dart", "not found");

    tmp.writeFile("root/foo.txt", "");
    checkInvalid("root/foo.txt", "not a Dart file");

    tmp.writeFile("root/foo.dart", "");
    checkInvalid("root/foo.dart", "doesn't contain a main function");

    tmp.writeFile("elsewhere.dart",
                  "import \"package:flutter/ui.dart\"\n" +
                  "main() {}\n");
    checkInvalid("elsewhere.dart", "isn't within the current project");
    tmp.writeFile("root/elsewhere.dart",
                  "import \"package:flutter/ui.dart\"\n" +
                  "main() {}\n");
    checkInvalid("root/elsewhere.dart", "isn't within a Flutter application");
  }

  private void checkInvalid(@Nullable String path, String expected) throws Exception {
    final String fullPath = path == null ? null : tmp.pathAt(path);
    final MainFile.Result main = Testing.computeOnDispatchThread(
      () -> MainFile.verify(fullPath, fixture.getProject()));

    assertFalse(main.canLaunch());
    if (main.getError().contains("{0}")) {
      fail("bad error message: " + main.getError());
    }
    if (!main.getError().contains(expected)) {
      fail("expected error to contain '" + expected + "' but got: " + main.getError());
    }
  }
}
