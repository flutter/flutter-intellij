/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.coverage;

import com.intellij.rt.coverage.data.ProjectData;
import io.flutter.run.coverage.LcovInfo;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LcovInfoTest {

  @Test
  public void testReadData() throws IOException {
    final File sessionDataFile = new File("testData/coverage", "lcov.info");
    final ProjectData projectData = new ProjectData();
    LcovInfo.readInto(projectData, sessionDataFile);
    // The file contains data for one class, with 110 lines, but we don't know the class name.
    projectData.getClasses().entrySet().iterator().forEachRemaining(entry -> {
      assertEquals(110, entry.getValue().getLines().length);
    });
  }
}
