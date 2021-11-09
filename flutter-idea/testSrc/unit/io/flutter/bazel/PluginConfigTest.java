/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public class PluginConfigTest {

  @Rule
  public ProjectFixture fixture = Testing.makeEmptyProject();

  @Rule
  public TestDir dir = new TestDir();

  @Test
  public void shouldReturnNullForSyntaxError() throws Exception {
    final VirtualFile config = dir.writeFile("config.json", "asdf");
    final PluginConfig result = PluginConfig.load(config);
    assertNull(result);
  }
}
