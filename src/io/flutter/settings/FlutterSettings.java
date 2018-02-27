/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.analytics.Analytics;

import java.util.ArrayList;
import java.util.List;

public class FlutterSettings {
  private static final String reloadOnSaveKey = "io.flutter.reloadOnSave";
  private static final String openInspectorOnAppLaunchKey = "io.flutter.openInspectorOnAppLaunch";
  private static final String verboseLoggingKey = "io.flutter.verboseLogging";
  private static final String previewViewKey = "io.flutter.previewView";
  private static final String previewDart2 = "io.flutter.getPreviewDart2";


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

    if (getEnablePreviewView()) {
      analytics.sendEvent("settings", afterLastPeriod(previewViewKey));
    }
    if (getPreviewDart2()) {
      analytics.sendEvent("settings", afterLastPeriod(previewDart2));
    }
    if (isReloadOnSave()) {
      analytics.sendEvent("Settings", afterLastPeriod(reloadOnSaveKey));
    }
    if (isOpenInspectorOnAppLaunch()) {
      analytics.sendEvent("Settings", afterLastPeriod(openInspectorOnAppLaunchKey));
    }
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public boolean getEnablePreviewView() {
    return getPropertiesComponent().getBoolean(previewViewKey, false);
  }

  public void setEnablePreviewView(boolean value) {
    getPropertiesComponent().setValue(previewViewKey, value, false);

    fireEvent();
  }

  public boolean getPreviewDart2() {
    return getPropertiesComponent().getBoolean(previewDart2, false);
  }

  public void setPreviewDart2(boolean value) {
    getPropertiesComponent().setValue(previewDart2, value, false);

    updateAnalysisServerArgs(value);

    fireEvent();
  }

  private void updateAnalysisServerArgs(boolean previewDart2) {
    final String serverRegistryKey = "dart.server.additional.arguments";
    final String previewDart2Flag = "--preview-dart-2";

    final List<String> params = StringUtil.split(Registry.stringValue(serverRegistryKey), " ");

    if (previewDart2 && !params.contains(previewDart2Flag)) {
      final List<String> copy = new ArrayList<>(params);
      copy.add(previewDart2Flag);
      Registry.get(serverRegistryKey).setValue(StringUtil.join(copy, " "));
    }
    else if (!previewDart2 && params.contains(previewDart2Flag)) {
      final List<String> copy = new ArrayList<>(params);
      copy.removeIf(previewDart2Flag::equals);
      Registry.get(serverRegistryKey).setValue(StringUtil.join(copy, " "));
    }
  }

  public boolean isReloadOnSave() {
    return getPropertiesComponent().getBoolean(reloadOnSaveKey, true);
  }

  public boolean isOpenInspectorOnAppLaunch() {
    return getPropertiesComponent().getBoolean(openInspectorOnAppLaunchKey, true);
  }

  public void setReloadOnSave(boolean value) {
    getPropertiesComponent().setValue(reloadOnSaveKey, value, true);

    fireEvent();
  }

  public void setOpenInspectorOnAppLaunch(boolean value) {
    getPropertiesComponent().setValue(openInspectorOnAppLaunchKey, value, true);

    fireEvent();
  }

  public boolean isVerboseLogging() {
    return getPropertiesComponent().getBoolean(verboseLoggingKey, false);
  }

  public void setVerboseLogging(boolean value) {
    getPropertiesComponent().setValue(verboseLoggingKey, value, false);

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
