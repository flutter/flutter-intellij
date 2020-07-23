/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.project.Project;
import io.flutter.utils.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static io.flutter.jxbrowser.JxBrowserManager.DOWNLOAD_PATH;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({FileUtils.class})
public class JxBrowserManagerTest {
  @Mock Project mockProject;

  @Test
  public void testSetUpIfDirectoryFails() {
    final JxBrowserManager manager = JxBrowserManager.getInstance();
    PowerMockito.mockStatic(FileUtils.class);
    when(FileUtils.makeDirectoryIfNotExists(DOWNLOAD_PATH)).thenReturn(false);

    manager.setUp(mockProject);
    Assert.assertEquals(manager.getStatus(), JxBrowserStatus.INSTALLATION_FAILED);
  }
}
