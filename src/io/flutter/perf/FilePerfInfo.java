/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.TIntHashSet;

class FilePerfInfo {
  private final VirtualFile file;

  private final TIntHashSet coveredLines = new TIntHashSet();
  private final TIntHashSet uncoveredLines = new TIntHashSet();

  public FilePerfInfo(VirtualFile file) {
    this.file = file;
  }

  public VirtualFile getFile() {
    return file;
  }

  // XXX THESE ARE THE WRONG STATS.
  public void addCovered(Pair<Integer, Integer> pos) {
    if (pos == null) {
      return;
    }

    final int line = pos.first;
    coveredLines.add(line);
    uncoveredLines.remove(line);
  }

  public void addUncovered(int line) {
    if (!coveredLines.contains(line)) {
      uncoveredLines.add(line);
    }
  }

  public void addUncovered(Pair<Integer, Integer> pos) {
    if (pos == null) {
      return;
    }
    final int line = pos.first;
    if (!coveredLines.contains(line)) {
      uncoveredLines.add(line);
    }
  }

  public int[] getCoveredLines() {
    return coveredLines.toArray();
  }

  public int[] getUncoveredLines() {
    return uncoveredLines.toArray();
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean isCovered(int line) {
    return coveredLines.contains(line);
  }
}
