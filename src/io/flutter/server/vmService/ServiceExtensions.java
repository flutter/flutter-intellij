/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.server.vmService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Each service extension needs to be manually added to [toggleableExtensionDescriptions].
public class ServiceExtensions {
  // TODO(kenzie): Alter these values to match other extensions once service extension states are restored from device.
  // These values are currently configured differently because we invert the value of this service extension state.
  public static final ToggleableServiceExtensionDescription<Boolean> debugAllowBanner =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.debugAllowBanner",
      false,
      true,
      "Show Debug Mode Banner",
      "Hide Debug Mode Banner");

  public static final ToggleableServiceExtensionDescription<Boolean> debugPaint =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.debugPaint",
      true,
      false,
      "Hide Debug Paint",
      "Show Debug Paint");

  public static final ToggleableServiceExtensionDescription<Boolean> debugPaintBaselines =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.debugPaintBaselinesEnabled",
      true,
      false,
      "Hide Paint Baselines",
      "Show Paint Baselines");

  public static final ToggleableServiceExtensionDescription<Boolean> performanceOverlay =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.showPerformanceOverlay",
      true,
      false,
      "Hide Performance Overlay",
      "Show Performance Overlay");

  public static final ToggleableServiceExtensionDescription<Boolean> repaintRainbow =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.repaintRainbow",
      true,
      false,
      "Hide Repaint Rainbow",
      "Show Repaint Rainbow");

  public static final ToggleableServiceExtensionDescription<Double> slowAnimations =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.timeDilation",
      5.0,
      1.0,
      "Disable Slow Animations",
      "Enable Slow Animations");

  public static final ToggleableServiceExtensionDescription<String> togglePlatformMode =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.platformOverride",
      "iOS",
      "android",
      "Toggle Platform",
      "Toggle Platform");

  public static final ToggleableServiceExtensionDescription<Boolean> toggleSelectWidgetMode =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.show",
      true,
      false,
      "Disable Select Widget Mode",
      "Enable Select Widget Mode");

  public static final ToggleableServiceExtensionDescription<Boolean> trackRebuildWidgets =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.trackRebuildDirtyWidgets",
      true,
      false,
      "Track Widget Rebuilds",
      "Do Not Track Widget Rebuilds");

  public static final ToggleableServiceExtensionDescription<Boolean> trackRepaintWidgets =
    new ToggleableServiceExtensionDescription<>(
      "ext.flutter.inspector.trackRepaintWidgets",
      true,
      false,
      "Track Widget Repaints",
      "Do Not Track Widget Repaints");

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

  static final List<ToggleableServiceExtensionDescription> toggleableExtensionDescriptions = Arrays.asList(
    debugAllowBanner,
    debugPaint,
    debugPaintBaselines,
    performanceOverlay,
    repaintRainbow,
    slowAnimations,
    togglePlatformMode,
    toggleSelectWidgetMode,
    trackRebuildWidgets,
    trackRepaintWidgets);

  public static final Map<String, ToggleableServiceExtensionDescription> toggleableExtensionsWhitelist =
    toggleableExtensionDescriptions.stream().collect(
      Collectors.toMap(
        ToggleableServiceExtensionDescription::getExtension,
        extensionDescription -> extensionDescription));
}
