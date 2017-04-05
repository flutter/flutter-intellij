/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import org.junit.rules.ExternalResource;

import static org.junit.Assert.assertNotNull;

/**
 * Represents a temporary directory to be used in a JUnit 4 test.
 *
 * <p>To set up, use JUnit 4's @Rule or @ClassRule attribute.
 */
public class TestDir extends ExternalResource {
  final TempDirTestFixtureImpl fixture = new TempDirTestFixtureImpl();

  @Override
  protected void before() throws Exception {
    fixture.setUp();
  }

  @Override
  protected void after() {
    try {
      if (ApplicationManager.getApplication() != null) {
        fixture.tearDown();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Creates a subdirectory of the temp directory if it doesn't already exist.
   *
   * @param path relative to the temp directory.
   * @return The corresponding VirtualFile.
   */
  public VirtualFile ensureDir(String path) throws Exception {
    return fixture.findOrCreateDir(path);
  }

  /**
   * Sets the contents of a file in the temp directory.
   *
   * <p>Creates it if it doesn't exist.
   *
   * @return The curresponding VirtualFile.
   */
  public VirtualFile writeFile(String path, String text) throws Exception {
    return Testing.computeInWriteAction(() -> fixture.createFile(path, text));
  }

  /**
   * Deletes a file in the temp directory.
   *
   * @param path relative to the temp directory.
   */
  public void deleteFile(String path) throws Exception {
    Testing.runInWriteAction(() -> {
      final VirtualFile target = fixture.getFile(path);
      assertNotNull("attempted to delete nonexistent file: " + path, target);
      target.delete(this);
    });
  }

  /**
   * Given a path relative to the temp directory, returns the absolute path.
   */
  public String pathAt(String path) throws Exception {
    return fixture.getTempDirPath() + "/" + path;
  }
}
