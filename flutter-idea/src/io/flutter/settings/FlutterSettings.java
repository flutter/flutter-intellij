/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.EventDispatcher;
import com.jetbrains.lang.dart.analyzer.DartClosingLabelManager;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.Objects;

public class FlutterSettings {
  private static final String reloadOnSaveKey = "io.flutter.reloadOnSave";
  private static final String openInspectorOnAppLaunchKey = "io.flutter.openInspectorOnAppLaunch";
  private static final String perserveLogsDuringHotReloadAndRestartKey = "io.flutter.persereLogsDuringHotReloadAndRestart";
  private static final String verboseLoggingKey = "io.flutter.verboseLogging";
  private static final String formatCodeOnSaveKey = "io.flutter.formatCodeOnSave";
  private static final String organizeImportsOnSaveKey = "io.flutter.organizeImportsOnSave";
  private static final String showOnlyWidgetsKey = "io.flutter.showOnlyWidgets";
  private static final String syncAndroidLibrariesKey = "io.flutter.syncAndroidLibraries";
  private static final String showStructuredErrorsKey = "io.flutter.showStructuredErrors";
  private static final String includeAllStackTracesKey = "io.flutter.includeAllStackTraces";
  private static final String showBuildMethodGuidesKey = "io.flutter.editor.showBuildMethodGuides";
  private static final String enableHotUiKey = "io.flutter.editor.enableHotUi";
  private static final String enableBazelHotRestartKey = "io.flutter.editor.enableBazelHotRestart";
  private static final String showBazelHotRestartWarningKey = "io.flutter.showBazelHotRestartWarning";
  private static final String enableJcefBrowserKey = "io.flutter.enableJcefBrowser";
  private static final String fontPackagesKey = "io.flutter.fontPackages";
  private static final String allowTestsInSourcesRootKey = "io.flutter.allowTestsInSources";
  private static final String showBazelIosRunNotificationKey = "io.flutter.hideBazelIosRunNotification";
  private static final String sdkVersionOutdatedWarningAcknowledgedKey = "io.flutter.sdkVersionOutdatedWarningAcknowledged";
  private static final String androidStudioBotAcknowledgedKey = "io.flutter.androidStudioBotAcknowledgedKey";

  // TODO(helin24): This is to change the embedded browser setting back to true only once for Big Sur users. If we
  // switch to enabling the embedded browser for everyone, then delete this key.
  private static final String changeBigSurToTrueKey = "io.flutter.setBigSurToTrueKey2";

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

  public static @NotNull FlutterSettings getInstance() {
    if (testInstance != null) {
      return testInstance;
    }

    return Objects.requireNonNull(Objects.requireNonNull(ApplicationManager.getApplication()).getService(FlutterSettings.class));
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
    return getPropertiesComponent().getBoolean(showStructuredErrorsKey, true);
  }

  public void setShowStructuredErrors(boolean value) {
    getPropertiesComponent().setValue(showStructuredErrorsKey, value, true);

    fireEvent();
  }

  public boolean isIncludeAllStackTraces() {
    return getPropertiesComponent().getBoolean(includeAllStackTracesKey, true);
  }

  public void setIncludeAllStackTraces(boolean value) {
    getPropertiesComponent().setValue(includeAllStackTracesKey, value, true);

    fireEvent();
  }

  public boolean isOpenInspectorOnAppLaunch() {
    return getPropertiesComponent().getBoolean(openInspectorOnAppLaunchKey, false);
  }

  public void setOpenInspectorOnAppLaunch(boolean value) {
    getPropertiesComponent().setValue(openInspectorOnAppLaunchKey, value, false);

    fireEvent();
  }

  public boolean isPerserveLogsDuringHotReloadAndRestart() {
    return getPropertiesComponent().getBoolean(perserveLogsDuringHotReloadAndRestartKey, false);
  }

  public void setPerserveLogsDuringHotReloadAndRestart(boolean value) {
    getPropertiesComponent().setValue(perserveLogsDuringHotReloadAndRestartKey, value, false);
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

  public boolean isEnableJcefBrowser() {
    return getPropertiesComponent().getBoolean(enableJcefBrowserKey, false);
  }

  public void setEnableJcefBrowser(boolean value) {
    getPropertiesComponent().setValue(enableJcefBrowserKey, value, false);

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

  public String getFontPackages() {
    return getPropertiesComponent().getValue(fontPackagesKey, "");
  }

  public void setFontPackages(String value) {
    getPropertiesComponent().setValue(fontPackagesKey, value == null ? "" : value);
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


  public boolean isEnableBazelHotRestart() {
    return getPropertiesComponent().getBoolean(enableBazelHotRestartKey, false);
  }

  public void setEnableBazelHotRestart(boolean value) {
    getPropertiesComponent().setValue(enableBazelHotRestartKey, value, false);
    fireEvent();
  }

  public boolean isShowBazelHotRestartWarning() {
    return getPropertiesComponent().getBoolean(showBazelHotRestartWarningKey, true);
  }

  public void setShowBazelHotRestartWarning(boolean value) {
    getPropertiesComponent().setValue(showBazelHotRestartWarningKey, value, true);
    fireEvent();
  }

  public boolean isChangeBigSurToTrue() {
    return getPropertiesComponent().getBoolean(changeBigSurToTrueKey, true);
  }

  public void setChangeBigSurToTrue(boolean value) {
    getPropertiesComponent().setValue(changeBigSurToTrueKey, value, true);
    fireEvent();
  }

  public boolean isAllowTestsInSourcesRoot() {
    return getPropertiesComponent().getBoolean(allowTestsInSourcesRootKey, false);
  }

  public void setAllowTestsInSourcesRoot(boolean value) {
    getPropertiesComponent().setValue(allowTestsInSourcesRootKey, value, false);
    fireEvent();
  }

  public boolean isShowBazelIosRunNotification() {
    return getPropertiesComponent().getBoolean(showBazelIosRunNotificationKey, true);
  }

  public void setShowBazelIosRunNotification(boolean value) {
    getPropertiesComponent().setValue(showBazelIosRunNotificationKey, value, true);
    fireEvent();
  }

  /**
   * See {FlutterSdkVersion#MIN_SDK_SUPPORTED}.
   */
  public boolean isSdkVersionOutdatedWarningAcknowledged(String versionText, boolean isBeforeSunset) {
    return getPropertiesComponent().getBoolean(getSdkVersionKey(versionText, isBeforeSunset));
  }

  public void setSdkVersionOutdatedWarningAcknowledged(String versionText, boolean isBeforeSunset, boolean value) {
    getPropertiesComponent().setValue(getSdkVersionKey(versionText, isBeforeSunset), value);
  }

  private String getSdkVersionKey(String versionText, boolean isBeforeSunset) {
    return sdkVersionOutdatedWarningAcknowledgedKey + "_" + versionText + "_" + (isBeforeSunset ? "beforeSunset" : "afterSunset");
  }

  public boolean isAndroidStudioBotAcknowledged() {
    return getPropertiesComponent().getBoolean(androidStudioBotAcknowledgedKey, false);
  }

  public void setAndroidStudioBotAcknowledgedKey(boolean value) {
    getPropertiesComponent().setValue(androidStudioBotAcknowledgedKey, value);
  }
}
