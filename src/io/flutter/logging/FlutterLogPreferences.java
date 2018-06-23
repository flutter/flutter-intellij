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
  public boolean SHOW_TIMESTAMP = true;
  public boolean SHOW_SEQUENCE_NUMBERS = false;
  public boolean SHOW_LOG_LEVEL = true;
  public boolean SHOW_LOG_CATEGORY = true;
  public boolean SHOW_COLOR = true;

  public int TOOL_WINDOW_LOG_LEVEL = 800; // Level INFO
  public boolean TOOL_WINDOW_MATCH_CASE = false;
  public boolean TOOL_WINDOW_REGEX = false;

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
}
