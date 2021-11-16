/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import io.flutter.utils.JsonUtils;

public class InspectorSourceLocation {
  private final JsonObject json;
  private final InspectorSourceLocation parent;
  private final Project project;

  public InspectorSourceLocation(JsonObject json, InspectorSourceLocation parent, Project project) {
    this.json = json;
    this.parent = parent;
    this.project = project;
  }

  public String getFile() {
    return JsonUtils.getStringMember(json, "file");
  }

  public VirtualFile getVirtualFile() {
    String fileName = getFile();
    if (fileName == null) {
      return parent != null ? parent.getVirtualFile() : null;
    }

    fileName = InspectorService.fromSourceLocationUri(fileName, project);

    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
    if (virtualFile != null && !virtualFile.exists()) {
      return null;
    }
    return virtualFile;
  }

  public int getLine() {
    return JsonUtils.getIntMember(json, "line");
  }

  public String getName() {
    return JsonUtils.getStringMember(json, "name");
  }

  public int getColumn() {
    return JsonUtils.getIntMember(json, "column");
  }

  public XSourcePosition getXSourcePosition() {
    final VirtualFile file = getVirtualFile();
    if (file == null) {
      return null;
    }
    final int line = getLine();
    final int column = getColumn();
    if (line < 0 || column < 0) {
      return null;
    }
    return XSourcePositionImpl.create(file, line - 1, column - 1);
  }

  public InspectorService.Location getLocation() {
    return new InspectorService.Location(getVirtualFile(), getLine(), getColumn(), getXSourcePosition().getOffset());
  }
}
