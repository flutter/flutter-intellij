/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.EventDispatcher;
import com.jetbrains.lang.dart.analyzer.DartClosingLabelManager;
import io.flutter.FlutterUtils;
import io.flutter.analytics.Analytics;

import java.util.EventListener;

public class FlutterSettings {
  private static final String reloadOnSaveKey = "io.flutter.reloadOnSave";
  private static final String openInspectorOnAppLaunchKey = "io.flutter.openInspectorOnAppLaunch";
  private static final String verboseLoggingKey = "io.flutter.verboseLogging";
  private static final String formatCodeOnSaveKey = "io.flutter.formatCodeOnSave";
  private static final String organizeImportsOnSaveKey = "io.flutter.organizeImportsOnSave";
  private static final String showOnlyWidgetsKey = "io.flutter.showOnlyWidgets";
  private static final String syncAndroidLibrariesKey = "io.flutter.syncAndroidLibraries";
  private static final String showStructuredErrors = "io.flutter.showStructuredErrors";
  private static final String showBuildMethodGuidesKey = "io.flutter.editor.showBuildMethodGuides";
  private static final String enableHotUiKey = "io.flutter.editor.enableHotUi";
  private static final String enableEmbeddedBrowsersKey = "io.flutter.editor.enableEmbeddedBrowsers";

  /**
   * Registry key to suggest all run configurations instead of just one.
   * <p>
   * Useful for {@link io.flutter.run.bazelTest.FlutterBazelTestConfigurationType} to show both watch and regular configurations
   * in the left-hand gutter.
   */
  private static final String suggestAllRunConfigurationsFromContextKey = "suggest.all.run.configurations.from.context";

  private static FlutterSettings testInstance;

  /**
   * This is only used for testing.
   */
  @VisibleForTesting
  public static void setInstance(FlutterSettings instance) {
    testInstance = instance;
  }

  public static FlutterSettings getInstance() {
    if (testInstance != null) {
      return testInstance;
    }

    return ServiceManager.getService(FlutterSettings.class);
  }

  protected static PropertiesComponent getPropertiesComponent() {
    return PropertiesComponent.getInstance();
  }

  public interface Listener extends EventListener {
    void settingsChanged();
  }

  private final EventDispatcher<Listener> dispatcher = EventDispatcher.create(Listener.class);

  public FlutterSettings() {
  }

  public void sendSettingsToAnalytics(Analytics analytics) {
    final PropertiesComponent properties = getPropertiesComponent();

    // Send data on the number of experimental features enabled by users.
    analytics.sendEvent("settings", "ping");

    if (isReloadOnSave()) {
      analytics.sendEvent("settings", afterLastPeriod(reloadOnSaveKey));
    }

    if (isOpenInspectorOnAppLaunch()) {
      analytics.sendEvent("settings", afterLastPeriod(openInspectorOnAppLaunchKey));
    }

    if (isFormatCodeOnSave()) {
      analytics.sendEvent("settings", afterLastPeriod(formatCodeOnSaveKey));

      if (isOrganizeImportsOnSave()) {
        analytics.sendEvent("settings", afterLastPeriod(organizeImportsOnSaveKey));
      }
    }

    if (isShowOnlyWidgets()) {
      analytics.sendEvent("settings", afterLastPeriod(showOnlyWidgetsKey));
    }

    if (isSyncingAndroidLibraries()) {
      analytics.sendEvent("settings", afterLastPeriod(syncAndroidLibrariesKey));
    }

    if (isShowBuildMethodGuides()) {
      analytics.sendEvent("settings", afterLastPeriod(showBuildMethodGuidesKey));
    }

    if (isShowStructuredErrors()) {
      analytics.sendEvent("settings", afterLastPeriod(showStructuredErrors));
    }

    if (showAllRunConfigurationsInContext()) {
      analytics.sendEvent("settings", "showAllRunConfigurations");
    }

    if (isEnableEmbeddedBrowsers()) {
      analytics.sendEvent("settings", afterLastPeriod(enableEmbeddedBrowsersKey));
    }

    if (isEnableHotUi()) {
      analytics.sendEvent("settings", afterLastPeriod(enableHotUiKey));
    }
  }

  public void addListener(Listener listener) {
    dispatcher.addListener(listener);
  }

  public void removeListener(Listener listener) {
    dispatcher.removeListener(listener);
  }

  public boolean isReloadOnSave() {
    return getPropertiesComponent().getBoolean(reloadOnSaveKey, true);
  }

  public void setReloadOnSave(boolean value) {
    getPropertiesComponent().setValue(reloadOnSaveKey, value, true);

    fireEvent();
  }

  public boolean isFormatCodeOnSave() {
    return getPropertiesComponent().getBoolean(formatCodeOnSaveKey, false);
  }

  public void setFormatCodeOnSave(boolean value) {
    getPropertiesComponent().setValue(formatCodeOnSaveKey, value, false);

    fireEvent();
  }

  public boolean isOrganizeImportsOnSave() {
    return getPropertiesComponent().getBoolean(organizeImportsOnSaveKey, false);
  }

  public void setOrganizeImportsOnSave(boolean value) {
    getPropertiesComponent().setValue(organizeImportsOnSaveKey, value, false);

    fireEvent();
  }

  public boolean isShowOnlyWidgets() {
    return getPropertiesComponent().getBoolean(showOnlyWidgetsKey, false);
  }

  public void setShowOnlyWidgets(boolean value) {
    getPropertiesComponent().setValue(showOnlyWidgetsKey, value, false);

    fireEvent();
  }

  public boolean isSyncingAndroidLibraries() {
    return getPropertiesComponent().getBoolean(syncAndroidLibrariesKey, false);
  }

  public void setSyncingAndroidLibraries(boolean value) {
    getPropertiesComponent().setValue(syncAndroidLibrariesKey, value, false);

    fireEvent();
  }

  public boolean isShowStructuredErrors() {
    return getPropertiesComponent().getBoolean(showStructuredErrors, true);
  }

  public void setShowStructuredErrors(boolean value) {
    getPropertiesComponent().setValue(showStructuredErrors, value, true);

    fireEvent();
  }

  public boolean isOpenInspectorOnAppLaunch() {
    return getPropertiesComponent().getBoolean(openInspectorOnAppLaunchKey, false);
  }

  public void setOpenInspectorOnAppLaunch(boolean value) {
    getPropertiesComponent().setValue(openInspectorOnAppLaunchKey, value, false);

    fireEvent();
  }

  /**
   * Tells IntelliJ to show all run configurations possible when the user clicks on the left-hand green arrow to run a test.
   * <p>
   * Useful for {@link io.flutter.run.bazelTest.FlutterBazelTestConfigurationType} to show both watch and regular configurations
   * in the left-hand gutter.
   */
  public boolean showAllRunConfigurationsInContext() {
    return Registry.is(suggestAllRunConfigurationsFromContextKey, false);
  }

  public void setShowAllRunConfigurationsInContext(boolean value) {
    Registry.get(suggestAllRunConfigurationsFromContextKey).setValue(value);

    fireEvent();
  }

  public boolean isVerboseLogging() {
    return getPropertiesComponent().getBoolean(verboseLoggingKey, false);
  }

  public void setVerboseLogging(boolean value) {
    getPropertiesComponent().setValue(verboseLoggingKey, value, false);

    fireEvent();
  }

  public boolean isShowBuildMethodGuides() {
    return getPropertiesComponent().getBoolean(showBuildMethodGuidesKey, true);
  }

  public void setShowBuildMethodGuides(boolean value) {
    getPropertiesComponent().setValue(showBuildMethodGuidesKey, value, true);

    fireEvent();
  }

  public boolean isShowClosingLabels() {
    return DartClosingLabelManager.getInstance().getShowClosingLabels();
  }

  public void setShowClosingLabels(boolean value) {
    DartClosingLabelManager.getInstance().setShowClosingLabels(value);
  }

  protected void fireEvent() {
    dispatcher.getMulticaster().settingsChanged();
  }

  private static String afterLastPeriod(String str) {
    final int index = str.lastIndexOf('.');
    return index == -1 ? str : str.substring(index + 1);
  }

  public boolean isEnableHotUi() {
    return getPropertiesComponent().getBoolean(enableHotUiKey, false);
  }

  public void setEnableHotUi(boolean value) {
    getPropertiesComponent().setValue(enableHotUiKey, value, false);

    fireEvent();
  }

  public boolean isEnableHotUiInCodeEditor() {
    // We leave this setting off for now to avoid possible performance and
    // usability issues rendering previews directly in the code editor.
    return false;
  }

  public boolean isEnableEmbeddedBrowsers() {
    return getPropertiesComponent().getBoolean(enableEmbeddedBrowsersKey, isPluginVersionDev());
  }

  public void setEnableEmbeddedBrowsers(boolean value) {
    getPropertiesComponent().setValue(enableEmbeddedBrowsersKey, value, isPluginVersionDev());

    fireEvent();
  }

  private static boolean isPluginVersionDev() {
    final IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(FlutterUtils.getPluginId());
    assert descriptor != null;
    return descriptor.getVersion().contains("dev") || descriptor.getVersion().contains("SNAPSHOT");
  }
}
