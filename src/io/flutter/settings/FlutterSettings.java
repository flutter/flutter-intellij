/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(
  name = "FlutterSettings",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class FlutterSettings implements PersistentStateComponent<FlutterSettings> {

  public static FlutterSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterSettings.class);
  }

  public interface Listener {
    void settingsChanged();
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final List<Listener> listeners = new ArrayList<>();

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public FlutterSettings getState() {
    return this;
  }

  public void loadState(FlutterSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
