/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.ide.FlutterCodeInsightFixtureTestCase;

import java.lang.reflect.Constructor;
import java.util.Arrays;

public class FlutterCommandTest extends FlutterCodeInsightFixtureTestCase {

  private FlutterSdk createFlutterSdk() throws Exception {
    VirtualFile home = ModuleRootManager.getInstance(getModule()).getContentRoots()[0];

    Constructor<FlutterSdk> constructor =
      FlutterSdk.class.getDeclaredConstructor(VirtualFile.class, FlutterSdkVersion.class);
    constructor.setAccessible(true);

    return constructor.newInstance(home, new FlutterSdkVersion("3.22.2"));
  }

  public void testRunCommandDoesNotAddNoColor() throws Exception {
    FlutterCommand command =
      new FlutterCommand(createFlutterSdk(), null, FlutterCommand.Type.RUN);

    GeneralCommandLine line = command.createGeneralCommandLine(getProject());

    assertTrue(line.getParametersList().getList().contains("run"));
    assertFalse(line.getParametersList().getList().contains("--no-color"));
  }

  public void testPubGetCommandDoesNotAddNoColor() throws Exception {
    FlutterCommand command =
      new FlutterCommand(createFlutterSdk(), null, FlutterCommand.Type.PUB_GET);

    GeneralCommandLine line = command.createGeneralCommandLine(getProject());

    assertTrue(line.getParametersList().getList().containsAll(Arrays.asList("pub", "get")));
    assertFalse(line.getParametersList().getList().contains("--no-color"));
  }
}
