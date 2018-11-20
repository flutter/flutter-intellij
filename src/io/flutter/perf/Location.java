/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE path.
 */
package io.flutter.perf;

import com.google.common.base.Objects;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import io.flutter.inspector.InspectorService;

/**
 * Source location within a file with an id that is unique with the current
 * running Flutter application.
 */
public class Location {
  public Location(String path, int line, int column, int id, TextRange textRange, String name) {
    this.path = path;
    this.line = line;
    this.column = column;
    this.id = id;
    this.textRange = textRange;
    this.name = name;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Location)) return false;
    final Location other = (Location) obj;
    return Objects.equal(line, other.line)
           && Objects.equal(column, other.column)
           && Objects.equal(path, other.path)
           && Objects.equal(id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(line, column, path, id);
  }

  public final int line;
  public final int column;
  public final int id;
  public final String path;

  public XSourcePosition getXSourcePosition() {
    final String fileName = InspectorService.fromSourceLocationUri(path);
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fileName);

    if (file == null) {
      return null;
    }
    return XSourcePositionImpl.create(file, line - 1, column - 1);
  }

  /**
   * Range in the file corresponding to the identify name at this location.
   */
  public final TextRange textRange;

  /**
   * Text of the identifier at this location.
   *
   * Typically this is the name of a widget class.
   */
  public final String name;
}
