/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import org.dartlang.analysis.server.protocol.FlutterOutline;

public class OutlineOffsetConverter {
  private final VirtualFile currentFile;
  private final Project project;
  public OutlineOffsetConverter(Project project, VirtualFile currentFile) {
    this.project = project;
    this.currentFile = currentFile;
  }

  public int getConvertedFileOffset(int offset) {
    return DartAnalysisServerService.getInstance(project).getConvertedOffset(currentFile, offset);
  }

  public int getConvertedOutlineOffset(FlutterOutline outline) {
    final int offset = outline.getOffset();
    return getConvertedFileOffset(offset);
  }

  public int getConvertedOutlineEnd(FlutterOutline outline) {
    final int end = outline.getOffset() + outline.getLength();
    return getConvertedFileOffset(end);
  }

  // TODO(jacobr): move this method to a different class or rename this class.
  public FlutterOutline findOutlineAtOffset(FlutterOutline outline, int offset) {
    if (outline == null) {
      return null;
    }
    if (getConvertedOutlineOffset(outline) <= offset && offset <= getConvertedOutlineEnd(outline)) {
      if (outline.getChildren() != null) {
        for (FlutterOutline child : outline.getChildren()) {
          final FlutterOutline foundChild = findOutlineAtOffset(child, offset);
          if (foundChild != null) {
            return foundChild;
          }
        }
      }
      return outline;
    }
    return null;
  }
}
