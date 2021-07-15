/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.project.Project;
import io.flutter.analytics.Analytics;
import io.flutter.utils.FileUtils;
import io.flutter.utils.JxBrowserUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;

import static io.flutter.jxbrowser.JxBrowserManager.DOWNLOAD_PATH;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class JxBrowserManagerTest {
  final Project mockProject = mock(Project.class);
  final Analytics mockAnalytics = mock(Analytics.class);
  final String PLATFORM_FILE_NAME = "test/platform/file/name";
  final String API_FILE_NAME = "test/api/file/name";
  final String SWING_FILE_NAME = "test/swing/file/name";

  @Before
  public void setUp() {
    JxBrowserManager.resetForTest();
  }

  @Test
  public void testSetUpIfKeyNotFound() throws FileNotFoundException {
    final JxBrowserUtils mockUtils = mock(JxBrowserUtils.class);
    when(mockUtils.getJxBrowserKey()).thenThrow(new FileNotFoundException("Key not found"));

    // If the directory for JxBrowser files cannot be created, the installation should fail.
    final JxBrowserManager manager = new JxBrowserManager(mockUtils, mockAnalytics, mock(FileUtils.class));

    manager.setUp(mockProject);
    Assert.assertEquals(JxBrowserStatus.INSTALLATION_FAILED, manager.getStatus());
  }

  @Test
  public void testSetUpIfDirectoryFails() throws FileNotFoundException {
    final JxBrowserUtils mockUtils = mock(JxBrowserUtils.class);
    when(mockUtils.getJxBrowserKey()).thenReturn("KEY");

    final FileUtils mockFileUtils = mock(FileUtils.class);
    when(mockFileUtils.makeDirectory(DOWNLOAD_PATH)).thenReturn(false);

    // If the directory for JxBrowser files cannot be created, the installation should fail.
    final JxBrowserManager manager = new JxBrowserManager(mockUtils, mockAnalytics, mockFileUtils);

    manager.setUp(mockProject);
    Assert.assertEquals(JxBrowserStatus.INSTALLATION_FAILED, manager.getStatus());
  }

  @Test
  public void testSetUpIfPlatformFileNotFound() throws FileNotFoundException {
    final JxBrowserUtils mockUtils = mock(JxBrowserUtils.class);
    when(mockUtils.getJxBrowserKey()).thenReturn("KEY");
    when(mockUtils.getPlatformFileName()).thenThrow(new FileNotFoundException());

    final FileUtils mockFileUtils = mock(FileUtils.class);
    when(mockFileUtils.makeDirectory(DOWNLOAD_PATH)).thenReturn(true);

    // If the system platform is not found among JxBrowser files, then the installation should fail.
    final JxBrowserManager manager = new JxBrowserManager(mockUtils, mockAnalytics, mockFileUtils);

    manager.setUp(mockProject);
    Assert.assertEquals(JxBrowserStatus.INSTALLATION_FAILED, manager.getStatus());
  }

  @Test
  public void testSetUpIfAllFilesExist() throws FileNotFoundException {
    final JxBrowserUtils mockUtils = mock(JxBrowserUtils.class);
    when(mockUtils.getJxBrowserKey()).thenReturn("KEY");
    when(mockUtils.getPlatformFileName()).thenReturn(PLATFORM_FILE_NAME);
    when(mockUtils.getApiFileName()).thenReturn(API_FILE_NAME);
    when(mockUtils.getSwingFileName()).thenReturn(SWING_FILE_NAME);

    final FileUtils mockFileUtils = mock(FileUtils.class);
    when(mockFileUtils.makeDirectory(DOWNLOAD_PATH)).thenReturn(true);
    when(mockFileUtils.fileExists(anyString())).thenReturn(true);

    // If all of the files are already downloaded, we should load the existing files.
    final JxBrowserManager manager = new JxBrowserManager(mockUtils, mockAnalytics, mockFileUtils);

    manager.setUp(mockProject);
    final String[] expectedFileNames = {PLATFORM_FILE_NAME, API_FILE_NAME, SWING_FILE_NAME};
    Assert.assertEquals(JxBrowserStatus.INSTALLED, manager.getStatus());
  }

  @Test
  public void testSetUpIfFilesMissing() throws FileNotFoundException {
    System.out.println("in testSetUpIfFilesMissing");
    final JxBrowserUtils mockUtils = mock(JxBrowserUtils.class);
    when(mockUtils.getJxBrowserKey()).thenReturn("KEY");
    when(mockUtils.getPlatformFileName()).thenReturn(PLATFORM_FILE_NAME);
    when(mockUtils.getApiFileName()).thenReturn(API_FILE_NAME);
    when(mockUtils.getSwingFileName()).thenReturn(SWING_FILE_NAME);

    final FileUtils mockFileUtils = mock(FileUtils.class);
    when(mockFileUtils.makeDirectory(DOWNLOAD_PATH)).thenReturn(true);
    when(mockFileUtils.fileExists(PLATFORM_FILE_NAME)).thenReturn(true);
    when(mockFileUtils.fileExists(API_FILE_NAME)).thenReturn(false);
    when(mockFileUtils.fileExists(SWING_FILE_NAME)).thenReturn(true);
    when(mockFileUtils.deleteFile(anyString())).thenReturn(true);

    // If any of our required files do not exist, we want to delete any existing files and start a download of all of the required files.
    final JxBrowserManager manager = new JxBrowserManager(mockUtils, mockAnalytics, mockFileUtils);
    final JxBrowserManager spy = spy(manager);
    final String[] expectedFileNames = {PLATFORM_FILE_NAME, API_FILE_NAME, SWING_FILE_NAME};
    doNothing().when(spy).downloadJxBrowser(mockProject, expectedFileNames);

    System.out.println("using spy");
    spy.setUp(mockProject);

    verify(mockFileUtils, times(1)).deleteFile(DOWNLOAD_PATH + File.separatorChar + PLATFORM_FILE_NAME);
    verify(mockFileUtils, times(1)).deleteFile(DOWNLOAD_PATH + File.separatorChar + API_FILE_NAME);
    verify(mockFileUtils, times(1)).deleteFile(DOWNLOAD_PATH + File.separatorChar + SWING_FILE_NAME);

    verify(spy, times(1)).downloadJxBrowser(mockProject, expectedFileNames);
  }
}
