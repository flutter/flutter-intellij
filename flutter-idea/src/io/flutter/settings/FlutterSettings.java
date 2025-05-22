/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
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
  private static final String enableBazelHotRestartKey = "io.flutter.editor.enableBazelHotRestart";
  private static final String showBazelHotRestartWarningKey = "io.flutter.showBazelHotRestartWarning";
  private static final String enableJcefBrowserKey = "io.flutter.enableJcefBrowser";
  private static final String fontPackagesKey = "io.flutter.fontPackages";
  private static final String allowTestsInSourcesRootKey = "io.flutter.allowTestsInSources";
  private static final String showBazelIosRunNotificationKey = "io.flutter.hideBazelIosRunNotification";
  private static final String sdkVersionOutdatedWarningAcknowledgedKey = "io.flutter.sdkVersionOutdatedWarningAcknowledged";
  private static final String androidStudioBotAcknowledgedKey = "io.flutter.androidStudioBotAcknowledgedKey";

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

  public void addListener(Listener listener) {
    dispatcher.addListener(listener);
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
  public boolean isSdkVersionOutdatedWarningAcknowledged(String versionText) {
    return getPropertiesComponent().getBoolean(getSdkVersionKey(versionText));
  }

  public void setSdkVersionOutdatedWarningAcknowledged(String versionText, boolean value) {
    getPropertiesComponent().setValue(getSdkVersionKey(versionText), value);
  }

  private String getSdkVersionKey(String versionText) {
    return sdkVersionOutdatedWarningAcknowledgedKey + "_" + versionText;
  }

  public boolean isAndroidStudioBotAcknowledged() {
    return getPropertiesComponent().getBoolean(androidStudioBotAcknowledgedKey, false);
  }

  public void setAndroidStudioBotAcknowledgedKey(boolean value) {
    getPropertiesComponent().setValue(androidStudioBotAcknowledgedKey, value);
  }
}
