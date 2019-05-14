/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.Document;

import java.util.Arrays;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Class used to determine whether regular indent guides intersect with
 * WidgetIndentGuides.
 * <p>
 * If indent guides are allowed to render that intersect with widget indent
 * guides, there will be flickering or other strange visual artifacts.
 * <p>
 * It is not possible to get indent guides to render in a stable z-order so
 * there is no way to make regular indent guides render before widget indent
 * guides. Even if that was possible it would not be desirable as there are
 * cases where displaying both guides would be distracting such as showing a
 * regular guide at indent 2 that draws a line through the middle of the
 * horizontal leg of widget indent guides for a list of children with indent 4.
 */
public class WidgetIndentHitTester {
  /**
   * Whether each line overlaps a Widget Indent Guide.
   */
  private final boolean[] lines;

  WidgetIndentHitTester(List<WidgetIndentGuideDescriptor> descriptors, Document document) {
    final int lineCount = document.getLineCount();
    lines = new boolean[lineCount];
    // TODO(jacobr): optimize using a more clever data structure.
    for (WidgetIndentGuideDescriptor descriptor : descriptors) {
      // if (descriptor.parent)
      {
        final int last = min(lines.length, descriptor.endLine + 1);
        for (int i = max(descriptor.startLine - 1, 0); i < last; i++) {
          lines[i] = true;
        }
      }
    }
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(lines);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WidgetIndentHitTester)) return false;
    final WidgetIndentHitTester other = (WidgetIndentHitTester)o;
    return Arrays.equals(lines, other.lines);
  }

  // TODO(jacobr): we could be smarter about intersection detection by
  // considering the indent of the intersecting lineRange as well.
  // If we could do that we would have to filter out fewer regular indent
  // guides that appear to intersect with the widget indent guides.
  // This could really be reframed as a rectangle intersection problem but that
  // would introduce additional complexity and we have yet to receive feedback
  // complaining about the missing regular indent guides for cases where the
  // guides overlap horizontally.
  public boolean intersects(LineRange lineRange) {
    final int last = min(lines.length, lineRange.endLine + 1);
    // TODO(jacobr): why the -1 on startLine?
    for (int i = max(lineRange.startLine - 1, 0); i < last; i++) {
      if (lines[i]) {
        return true;
      }
    }
    return false;
  }
}
