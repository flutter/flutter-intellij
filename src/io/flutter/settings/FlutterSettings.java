/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;


/**
 * Persists Flutter settings.
 */
@State(
  name = "FlutterSettings",
  storages = {
    @Storage("FlutterSettings.xml")}
)
public class FlutterSettings implements PersistentStateComponent<FlutterSettings> {

  private boolean ignoreMismatchedDartSdks;

  @Nullable
  public static FlutterSettings getInstance(Project project) {
    return ServiceManager.getService(project, FlutterSettings.class);
  }

  @Nullable
  @Override
  public FlutterSettings getState() {
    return this;
  }

  @Override
  public void loadState(FlutterSettings settings) {
    XmlSerializerUtil.copyBean(settings, this);
  }

  public boolean ignoreMismatchedDartSdks() {
    return ignoreMismatchedDartSdks;
  }

  public void setIgnoreMismatchedDartSdks(boolean ignoreMismatchedDartSdks) {
    this.ignoreMismatchedDartSdks = ignoreMismatchedDartSdks;
  }
}