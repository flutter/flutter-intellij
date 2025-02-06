/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.teamdev.jxbrowser.zoom.ZoomLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static java.util.Map.entry;

public class ZoomLevelSelector {
  @NotNull final Map<Integer, ZoomLevel> zoomLevels = Map.ofEntries(
    entry(25, ZoomLevel.P_25),
    entry(33, ZoomLevel.P_33),
    entry(50, ZoomLevel.P_50),
    entry(67, ZoomLevel.P_67),
    entry(75, ZoomLevel.P_75),
    entry(80, ZoomLevel.P_80),
    entry(90, ZoomLevel.P_90),
    entry(100, ZoomLevel.P_100),
    entry(110, ZoomLevel.P_110),
    entry(125, ZoomLevel.P_125),
    entry(150, ZoomLevel.P_150),
    entry(175, ZoomLevel.P_175),
    entry(200, ZoomLevel.P_200),
    entry(250, ZoomLevel.P_250),
    entry(300, ZoomLevel.P_300),
    entry(400, ZoomLevel.P_400),
    entry(500, ZoomLevel.P_500)
  );

  public @NotNull ZoomLevel getClosestZoomLevel(int zoomPercent) {
    ZoomLevel closest = ZoomLevel.P_100;
    int minDifference = Integer.MAX_VALUE;

    for (Map.Entry<Integer, ZoomLevel> entry : zoomLevels.entrySet()) {
      int currentDifference = Math.abs(zoomPercent - entry.getKey());
      if (currentDifference < minDifference) {
        minDifference = currentDifference;
        closest = entry.getValue();
      }
    }

    return closest;
  }}
