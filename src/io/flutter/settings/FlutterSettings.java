/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import io.flutter.analytics.Analytics;

import java.util.ArrayList;
import java.util.List;

public class FlutterSettings {
  private static final String reloadOnSaveKey = "io.flutter.reloadOnSave";
  private static final String verboseLoggingKey = "io.flutter.verboseLogging";
  private static final String memoryDashboardKey = "io.flutter.memoryDashboard";
  private static final String widgetInspectorKey = "io.flutter.widgetInspector";

  public static FlutterSettings getInstance() {
    return ServiceManager.getService(FlutterSettings.class);
  }

  protected static PropertiesComponent getPropertiesComponent() {
    return PropertiesComponent.getInstance();
  }

  public interface Listener {
    void settingsChanged();
  }

  private final List<Listener> listeners = new ArrayList<>();

  public void sendSettingsToAnalytics(Analytics analytics) {
    final PropertiesComponent properties = getPropertiesComponent();

    // Send data on the number of experimental features enabled by users.
    analytics.sendEvent("settings", "ping");
    if (isReloadOnSave()) {
      analytics.sendEvent("settings", afterLastPeriod(reloadOnSaveKey));
    }
    if (isMemoryDashboardEnabled()) {
      analytics.sendEvent("settings", afterLastPeriod(memoryDashboardKey));
    }
    if (isWidgetInspectorEnabled()) {
      analytics.sendEvent("settings", afterLastPeriod(widgetInspectorKey));
    }
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public boolean isReloadOnSave() {
    return getPropertiesComponent().getBoolean(reloadOnSaveKey, true);
  }

  public void setReloadOnSave(boolean value) {
    getPropertiesComponent().setValue(reloadOnSaveKey, value, true);

    fireEvent();
  }

  public boolean isVerboseLogging() {
    return getPropertiesComponent().getBoolean(verboseLoggingKey, false);
  }

  public void setVerboseLogging(boolean value) {
    getPropertiesComponent().setValue(verboseLoggingKey, value, false);

    fireEvent();
  }

  public boolean isMemoryDashboardEnabled() {
    return getPropertiesComponent().getBoolean(memoryDashboardKey, false);
  }

  public void setMemoryDashboardEnabled(boolean value) {
    getPropertiesComponent().setValue(memoryDashboardKey, value, false);

    fireEvent();
  }

  public boolean isWidgetInspectorEnabled() {
    return getPropertiesComponent().getBoolean(widgetInspectorKey, false);
  }

  public void setWidgetInspectorEnabled(boolean value) {
    getPropertiesComponent().setValue(widgetInspectorKey, value, false);

    fireEvent();
  }


  protected void fireEvent() {
    for (Listener listener : listeners) {
      listener.settingsChanged();
    }
  }

  private static String afterLastPeriod(String str) {
    final int index = str.lastIndexOf('.');
    return index == -1 ? str : str.substring(index + 1);
  }
}
