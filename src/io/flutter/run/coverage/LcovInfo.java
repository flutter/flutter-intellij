/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class LcovInfo {
  private static final Logger LOG = Logger.getInstance(LcovInfo.class.getName());

  private static final String FILE_LABEL = "SF:";
  private static final String DATA_LABEL = "DA:";
  private static final String END_LABEL = "end_of_record";

  private final Map<String, List<LineCount>> counts = new HashMap<>();

  private Path currentFile = null;
  private List<LineCount> lineCounts = null;
  private final String base;

  private LcovInfo(String base) {
    this.base = base;
  }

  public static void readInto(ProjectData data, File file) throws IOException {
    final String filePath = file.getAbsolutePath();
    final int index = filePath.indexOf("coverage");
    if (index < 0) {
      // TODO Define at least one class in data
      return;
    }
    final LcovInfo lcov = new LcovInfo(filePath.substring(0, index));
    try (final Stream<String> lines = Files.lines(file.toPath())) {
      lines.forEach(lcov::processLine);
    }
    for (String path : lcov.counts.keySet()) {
      final ClassData classData = data.getOrCreateClassData(path);
      final List<LineCount> list = lcov.counts.get(path);
      classData.setSource(fullPath(path));
      final int max = list.get(list.size() - 1).lineNum + 1;
      final LineData[] lines = new LineData[max];
      for (LineCount line : list) {
        final LineData lineData = new LineData(line.lineNum, null);
        lineData.setHits(line.execCount);
        lines[line.lineNum] = lineData;
        classData.registerMethodSignature(lineData);
      }
      classData.setLines(lines);
    }
  }

  void processLine(String line) {
    line = line.trim();
    if (line.startsWith(DATA_LABEL)) {
      assert currentFile != null;
      assert lineCounts != null;
      addLineCount(line.substring(DATA_LABEL.length()));
    }
    else if (line.startsWith(FILE_LABEL)) {
      //currentFile = Paths.get(line.substring(FILE_LABEL.length())).normalize();
      final File file = new File(base, line.substring(FILE_LABEL.length()));
      final URI normalize = file.toURI().normalize();
      currentFile = Paths.get(normalize);
      lineCounts = new ArrayList<>();
    }
    else if (line.equals(END_LABEL)) {
      storeLineCounts();
      currentFile = null;
      lineCounts = null;
    }
  }

  private void addLineCount(String data) {
    final String[] parts = data.split(",");
    assert parts.length >= 2;
    final int lineNum = safelyParse(parts[0]);
    final int execCount = safelyParse(parts[1]);
    lineCounts.add(new LineCount(lineNum, execCount));
  }

  private static int safelyParse(String val) {
    try {
      return Integer.parseInt(val);
    }
    catch (NumberFormatException ex) {
      return 0;
    }
  }

  private void storeLineCounts() {
    final String path = currentFile.toString();
    counts.put(path, lineCounts);
  }

  private static String fullPath(String path) {
    String absPath = new File(path).getAbsolutePath();
    if (SystemInfo.isWindows) {
      absPath = absPath.replaceAll("\\\\", "/");
    }
    return absPath;
  }

  private static class LineCount {
    int lineNum;
    int execCount;

    public LineCount(int num, int count) {
      lineNum = num;
      execCount = count;
    }
  }
}
