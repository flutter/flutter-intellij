/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Each service extension needs to be manually added to [toggleableExtensionDescriptions].
public class ServiceExtensions {
  public static final ToggleableServiceExtensionDescription<Boolean> debugAllowBanner =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.debugAllowBanner",
      "Debug Banner",
      true,
      false,
      "Hide Debug Mode Banner",
      "Show Debug Mode Banner");

  public static final ToggleableServiceExtensionDescription<Boolean> debugPaint =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.debugPaint",
      "Debug Paint",
      true,
      false,
      "Hide Debug Paint",
      "Show Debug Paint");

  public static final ToggleableServiceExtensionDescription<Boolean> debugPaintBaselines =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.debugPaintBaselinesEnabled",
      "Paint Baselines",
      true,
      false,
      "Hide Paint Baselines",
      "Show Paint Baselines");

  public static final ToggleableServiceExtensionDescription<Boolean> performanceOverlay =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.showPerformanceOverlay",
      "Performance Overlay",
      true,
      false,
      "Hide Performance Overlay",
      "Show Performance Overlay");

  public static final ToggleableServiceExtensionDescription<Boolean> repaintRainbow =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.repaintRainbow",
      "Repaint Rainbow",
      true,
      false,
      "Hide Repaint Rainbow",
      "Show Repaint Rainbow");

  public static final ToggleableServiceExtensionDescription<Double> slowAnimations =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.timeDilation",
      "Slow Animations",
      5.0,
      1.0,
      "Disable Slow Animations",
      "Enable Slow Animations");

  public static final ServiceExtensionDescription<String> togglePlatformMode =
    new ServiceExtensionDescription<>(
      "ext.flutter.platformOverride",
      "Override Target Platform",
      Arrays.asList("iOS", "android", "fuchsia"),
      Arrays.asList("Platform: iOS", "Platform: Android", "Platform: Fuchsia"));

  /**
   * Toggle whether interacting with the device selects widgets or triggers
   * normal interactions.
   */
  public static final ToggleableServiceExtensionDescription<Boolean> toggleSelectWidgetMode =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.selectMode",
      "Select Widget Mode",
      true,
      false,
      "Disable Select Widget Mode",
      "Enable Select Widget Mode");

  public static final ToggleableServiceExtensionDescription<Boolean> toggleOnDeviceWidgetInspector =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.show",
      "Select Widget Mode",
      true,
      false,
      // Technically this enables the on-device widget inspector but for older
      // versions of package:flutter it makes sense to describe this extension as
      // toggling widget select mode as it is the only way to toggle that mode.
      "Disable Select Widget Mode",
      "Enable Select Widget Mode");

  /**
   * Toggle whether the inspector on-device overlay is enabled.
   *
   * When available, the inspector overlay can be enabled at any time as it will
   * not interfere with user interaction with the app unless inspector select
   * mode is triggered.
   */
  public static final ToggleableServiceExtensionDescription<Boolean> enableOnDeviceInspector =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.enable",
      "Enable on-device inspector",
      true,
      false,
      "Exit on-device inspector",
      "Enter on-device inspector");

  public static final ToggleableServiceExtensionDescription<Boolean> toggleShowStructuredErrors =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.structuredErrors",
      "Structured Errors",
      true,
      false,
      "Disable Showing Structured Errors",
      "Show Structured Errors");

  public static final ToggleableServiceExtensionDescription<Boolean> trackRebuildWidgets =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.trackRebuildDirtyWidgets",
      "Track Widget Rebuilds",
      true,
      false,
      "Do Not Track Widget Rebuilds",
      "Track Widget Rebuilds");

  public static final ToggleableServiceExtensionDescription<Boolean> trackRepaintWidgets =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.trackRepaintWidgets",
      "Track Widget Repaints",
      true,
      false,
      "Do Not Track Widget Repaints",
      "Track Widget Repaints");

  // This extension should never be displayed as a button so does not need to be a
  // ToggleableServiceExtensionDescription object.
  public static final String didSendFirstFrameEvent = "ext.flutter.didSendFirstFrameEvent";

  // These extensions are not toggleable and do not need to be stored as a ToggleableServiceExtensionDescription object.
  public static final String flutterPrefix = "ext.flutter.";
  public static final String inspectorPrefix = "ext.flutter.inspector.";
  public static final String setPubRootDirectories = "ext.flutter.inspector.setPubRootDirectories";
  public static final String enableLogs = "ext.flutter.logs.enable";
  public static final String loggingChannels = "ext.flutter.logs.loggingChannels";
  public static final String designerRender = "ext.flutter.designer.render";
  public static final String flutterListViews = "_flutter.listViews";
  public static final String displayRefreshRate = "_flutter.getDisplayRefreshRate";

  static final List<ServiceExtensionDescription> toggleableExtensionDescriptions = Arrays.asList(
    debugAllowBanner,
    debugPaint,
    debugPaintBaselines,
    performanceOverlay,
    repaintRainbow,
    slowAnimations,
    togglePlatformMode,
    toggleSelectWidgetMode,
    toggleOnDeviceWidgetInspector,
    enableOnDeviceInspector,
    toggleShowStructuredErrors,
    trackRebuildWidgets,
    trackRepaintWidgets);

  public static final Map<String, ServiceExtensionDescription> toggleableExtensionsWhitelist =
    toggleableExtensionDescriptions.stream().collect(
      Collectors.toMap(
        ServiceExtensionDescription::getExtension,
        extensionDescription -> extensionDescription));
}
