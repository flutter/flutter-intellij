/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

/**
 * A line range describing the extent of and indent guide.
 */
public class LineRange {
  LineRange(int startLine, int endLine) {
    this.startLine = startLine;
    this.endLine = endLine;
  }

  final int startLine;
  final int endLine;
}
