/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.project.Project;
import io.flutter.utils.FileUtils;
import io.flutter.utils.JxBrowserUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.FileNotFoundException;

import static io.flutter.jxbrowser.JxBrowserManager.DOWNLOAD_PATH;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({FileUtils.class, JxBrowserUtils.class})
public class JxBrowserManagerTest {
  @Mock Project mockProject;
  final String PLATFORM_FILE_NAME = "test/platform/file/name";
  final String API_FILE_NAME = "test/api/file/name";
  final String SWING_FILE_NAME = "test/swing/file/name";

  @Test
  public void testSetUpIfDirectoryFails() {
    // If the directory for JxBrowser files cannot be created, the installation should fail.
    final JxBrowserManager manager = JxBrowserManager.getInstance();
    PowerMockito.mockStatic(FileUtils.class);
    when(FileUtils.makeDirectoryIfNotExists(DOWNLOAD_PATH)).thenReturn(false);

    manager.setUp(mockProject);
    Assert.assertEquals(manager.getStatus(), JxBrowserStatus.INSTALLATION_FAILED);
  }

  @Test
  public void testSetUpIfPlatformFileNotFound() throws FileNotFoundException {
    // If the system platform is not found among JxBrowser files, then the installation should fail.
    final JxBrowserManager manager = JxBrowserManager.getInstance();

    PowerMockito.mockStatic(FileUtils.class);
    when(FileUtils.makeDirectoryIfNotExists(DOWNLOAD_PATH)).thenReturn(true);

    PowerMockito.mockStatic(JxBrowserUtils.class);
    when(JxBrowserUtils.getPlatformFileName()).thenThrow(new FileNotFoundException());

    manager.setUp(mockProject);
    Assert.assertEquals(manager.getStatus(), JxBrowserStatus.INSTALLATION_FAILED);
  }

  @Test
  public void testSetUpIfAllFilesExist() throws FileNotFoundException {
    // Status should be set to 'installed' if all of the files are already downloaded and we can load them with the ClassLoader.
    final JxBrowserManager manager = JxBrowserManager.getInstance();

    PowerMockito.mockStatic(FileUtils.class);
    when(FileUtils.makeDirectoryIfNotExists(DOWNLOAD_PATH)).thenReturn(true);
    when(FileUtils.fileExists(anyString())).thenReturn(true);
    when(FileUtils.loadClassWithClassLoader(any(ClassLoader.class), eq(PLATFORM_FILE_NAME))).thenReturn(true);
    when(FileUtils.loadClassWithClassLoader(any(ClassLoader.class), eq(API_FILE_NAME))).thenReturn(true);
    when(FileUtils.loadClassWithClassLoader(any(ClassLoader.class), eq(SWING_FILE_NAME))).thenReturn(true);

    PowerMockito.mockStatic(JxBrowserUtils.class);
    when(JxBrowserUtils.getPlatformFileName()).thenReturn(PLATFORM_FILE_NAME);
    when(JxBrowserUtils.getApiFileName()).thenReturn(API_FILE_NAME);
    when(JxBrowserUtils.getSwingFileName()).thenReturn(SWING_FILE_NAME);

    manager.setUp(mockProject);
    Assert.assertEquals(manager.getStatus(), JxBrowserStatus.INSTALLED);
  }

  @Test
  public void testSetUpIfFilesMissing() throws FileNotFoundException {
    // If any of our required files do not exist, we want to delete any existing files and start a download of all of the required files.
    final JxBrowserManager partialMockManager = mock(JxBrowserManager.class);
    doCallRealMethod().when(partialMockManager).setUp(mockProject);

    PowerMockito.mockStatic(FileUtils.class);
    when(FileUtils.makeDirectoryIfNotExists(DOWNLOAD_PATH)).thenReturn(true);
    when(FileUtils.fileExists(PLATFORM_FILE_NAME)).thenReturn(true);
    when(FileUtils.fileExists(API_FILE_NAME)).thenReturn(false);
    when(FileUtils.fileExists(SWING_FILE_NAME)).thenReturn(true);
    when(FileUtils.deleteFileIfExists(anyString())).thenReturn(true);

    PowerMockito.mockStatic(JxBrowserUtils.class);
    when(JxBrowserUtils.getPlatformFileName()).thenReturn(PLATFORM_FILE_NAME);
    when(JxBrowserUtils.getApiFileName()).thenReturn(API_FILE_NAME);
    when(JxBrowserUtils.getSwingFileName()).thenReturn(SWING_FILE_NAME);

    partialMockManager.setUp(mockProject);

    verifyStatic(FileUtils.class);
    FileUtils.deleteFileIfExists(PLATFORM_FILE_NAME);
    verifyStatic(FileUtils.class);
    FileUtils.deleteFileIfExists(API_FILE_NAME);
    verifyStatic(FileUtils.class);
    FileUtils.deleteFileIfExists(SWING_FILE_NAME);

    final String[] expectedFileNames = {PLATFORM_FILE_NAME, API_FILE_NAME, SWING_FILE_NAME};
    verify(partialMockManager, times(1)).downloadJxBrowser(mockProject, expectedFileNames);
  }
}
