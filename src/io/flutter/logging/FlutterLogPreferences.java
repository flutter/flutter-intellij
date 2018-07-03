/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "FlutterLogPreferences", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class FlutterLogPreferences implements PersistentStateComponent<FlutterLogPreferences> {
  private boolean showTimestamp = false;
  private boolean showSequenceNumbers = false;
  private boolean showLogLevel = true;
  private boolean showLogCategory = true;
  private boolean showColor = false;

  private int toolWindowLogLevel = FlutterLog.Level.CONFIG.value;
  private boolean toolWindowMatchCase = false;
  private boolean toolWindowRegex = false;

  public static FlutterLogPreferences getInstance(Project project) {
    return ServiceManager.getService(project, FlutterLogPreferences.class);
  }

  @Override
  public FlutterLogPreferences getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull FlutterLogPreferences object) {
    XmlSerializerUtil.copyBean(object, this);
  }

  public boolean isShowTimestamp() {
    return showTimestamp;
  }

  public void setShowTimestamp(boolean showTimestamp) {
    this.showTimestamp = showTimestamp;
  }

  public boolean isShowSequenceNumbers() {
    return showSequenceNumbers;
  }

  public void setShowSequenceNumbers(boolean showSequenceNumbers) {
    this.showSequenceNumbers = showSequenceNumbers;
  }

  public boolean isShowLogLevel() {
    return showLogLevel;
  }

  public void setShowLogLevel(boolean showLogLevel) {
    this.showLogLevel = showLogLevel;
  }

  public boolean isShowLogCategory() {
    return showLogCategory;
  }

  public void setShowLogCategory(boolean showLogCategory) {
    this.showLogCategory = showLogCategory;
  }

  public boolean isShowColor() {
    return showColor;
  }

  public void setShowColor(boolean showColor) {
    this.showColor = showColor;
  }

  public int getToolWindowLogLevel() {
    return toolWindowLogLevel;
  }

  public void setToolWindowLogLevel(int toolWindowLogLevel) {
    this.toolWindowLogLevel = toolWindowLogLevel;
  }

  public boolean isToolWindowMatchCase() {
    return toolWindowMatchCase;
  }

  public void setToolWindowMatchCase(boolean toolWindowMatchCase) {
    this.toolWindowMatchCase = toolWindowMatchCase;
  }

  public boolean isToolWindowRegex() {
    return toolWindowRegex;
  }

  public void setToolWindowRegex(boolean toolWindowRegex) {
    this.toolWindowRegex = toolWindowRegex;
  }
}
