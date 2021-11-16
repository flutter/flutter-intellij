/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.junit.Test;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IconPreviewGeneratorTest {

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void generateCupertino() throws IOException {
    final String fontPath = "testData/utils/CupertinoIcons.ttf";
    final String propertiesPath = "testData/utils/cupertino.properties";
    final Path tempDir = Files.createTempDirectory("preview");
    final String outputPath = tempDir.toAbsolutePath().toString();
    File preview = new File(outputPath);
    assertEquals(0, preview.list().length);
    IconPreviewGenerator ipg = new IconPreviewGenerator(fontPath, 16, 16, Color.black);
    ipg.batchConvert(outputPath, propertiesPath, "");
    assertTrue(preview.exists());
    assertTrue(preview.isDirectory());
    final String[] list = preview.list();
    assertEquals(1233, list.length);
    boolean found = false;
    // The list is not sorted and there's no need to sort it.
    // We just want to verify that a specific file exists.
    for (String each : list) {
      if (each.equals("add.png")) {
        found = true;
        break;
      }
    }
    assertTrue(found);
    for (File each : preview.listFiles()) {
      each.delete();
    }
    preview.delete();
  }
}
