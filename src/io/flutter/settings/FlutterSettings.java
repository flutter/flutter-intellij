/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.EventDispatcher;
import com.jetbrains.lang.dart.analyzer.DartClosingLabelManager;
import io.flutter.analytics.Analytics;
import io.flutter.sdk.FlutterSdk;

import java.util.EventListener;

public class FlutterSettings {
  private static final String reloadOnSaveKey = "io.flutter.reloadOnSave";
  private static final String reloadWithErrorKey = "io.flutter.reloadWithError";
  private static final String openInspectorOnAppLaunchKey = "io.flutter.openInspectorOnAppLaunch";
  private static final String verboseLoggingKey = "io.flutter.verboseLogging";
  private static final String formatCodeOnSaveKey = "io.flutter.formatCodeOnSave";
  private static final String organizeImportsOnSaveKey = "io.flutter.organizeImportsOnSave";
  private static final String showOnlyWidgetsKey = "io.flutter.showOnlyWidgets";
  private static final String syncAndroidLibrariesKey = "io.flutter.syncAndroidLibraries";
  private static final String disableTrackWidgetCreationKey = "io.flutter.disableTrackWidgetCreation";
  private static final String showStructuredErrors = "io.flutter.showStructuredErrors";
  private static final String showBuildMethodGuidesKey = "io.flutter.editor.showBuildMethodGuides";

  /**
   * The Dart plugin uses this registry key to avoid bazel users getting their settings overridden on projects that include a
   * pubspec.yaml.
   * <p>
   * In other words, this key tells the plugin to configure dart projects without pubspec.yaml.
   */
  private static final String dartProjectsWithoutPubspecRegistryKey = "dart.projects.without.pubspec";

  /**
   * Registry key to suggest all run configurations instead of just one.
   * <p>
   * Useful for {@link io.flutter.run.bazelTest.FlutterBazelTestConfigurationType} to show both watch and regular configurations
   * in the left-hand gutter.
   */
  private static final String suggestAllRunConfigurationsFromContextKey = "suggest.all.run.configurations.from.context";

  public static FlutterSettings getInstance() {
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
    if (isReloadWithError()) {
      analytics.sendEvent("settings", afterLastPeriod(reloadWithErrorKey));
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
    if (isDisableTrackWidgetCreation()) {
      analytics.sendEvent("settings", afterLastPeriod(disableTrackWidgetCreationKey));
    }

    if (isShowBuildMethodGuides()) {
      analytics.sendEvent("settings", afterLastPeriod(showBuildMethodGuidesKey));
    }

    if (isShowStructuredErrors()) {
      analytics.sendEvent("settings", afterLastPeriod(showStructuredErrors));
    }
    if (shouldUseBazel()) {
      analytics.sendEvent("settings", afterLastPeriod(dartProjectsWithoutPubspecRegistryKey));
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

  public boolean isReloadWithError() {
    return getPropertiesComponent().getBoolean(reloadWithErrorKey, false);
  }

  public boolean isTrackWidgetCreationEnabled(Project project) {
    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk != null && flutterSdk.getVersion().isTrackWidgetCreationRecommended()) {
      return !getPropertiesComponent().getBoolean(disableTrackWidgetCreationKey, false);
    }
    else {
      return false;
    }
  }

  public boolean isDisableTrackWidgetCreation() {
    return getPropertiesComponent().getBoolean(disableTrackWidgetCreationKey, false);
  }

  public void setDisableTrackWidgetCreation(boolean value) {
    getPropertiesComponent().setValue(disableTrackWidgetCreationKey, value, false);

    fireEvent();
  }

  public void setReloadOnSave(boolean value) {
    getPropertiesComponent().setValue(reloadOnSaveKey, value, true);

    fireEvent();
  }

  public void setReloadWithError(boolean value) {
    getPropertiesComponent().setValue(reloadWithErrorKey, value, false);

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
   * Determines whether to use bazel package management.
   */
  public boolean shouldUseBazel() {
    return Registry.is(dartProjectsWithoutPubspecRegistryKey, false);
  }

  public void setShouldUseBazel(boolean value) {
    Registry.get(dartProjectsWithoutPubspecRegistryKey).setValue(value);

    fireEvent();
  }

  /**
   * Tells IntelliJ to show all run configurations possible when the user clicks on the left-hand green arror to run a test.
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

  public boolean isShowPreviewArea() {
    return true; // XXX implement.
  }
}
