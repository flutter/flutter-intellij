/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.profilers.*;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

// Refactored from Android Studio 3.2 adt-ui code.
public class FlutterStudioMonitorStage extends FlutterStage {

  @NotNull
  private final List<ProfilerMonitor> monitors;

  public FlutterAllMemoryData.ThreadSafeData getUsedDataSeries() {
    return allMemoryData.getUsedDataSeries();
  }

  public FlutterAllMemoryData.ThreadSafeData getCapacityDataSeries() {
    return allMemoryData.getCapacityDataSeries();
  }

  public FlutterAllMemoryData.ThreadSafeData getExternalDataSeries() {
    return allMemoryData.getExternalDataSeries();
  }

  private final FlutterAllMemoryData allMemoryData;

  // TODO(terry): Constructor must take a StudioProfilers???
  public FlutterStudioMonitorStage(@NotNull FlutterStudioProfilers profiler) {
    super(profiler);
    monitors = new LinkedList<>();
    allMemoryData = new FlutterAllMemoryData(profiler.getParentDisposable(),
                                             profiler.getApp());
  }

  @Override
  public void enter() {
    // Clear the selection
    getStudioProfilers().getTimeline().getSelectionRange().clear();

    // TODO(terry): As more profilers are added CPU, etc. we'll need some
    //              session notion and enable monitor a particular profiler
    //              view.
    /*
    Common.Session session = getStudioProfilers().getSession();
    if (session != Common.Session.getDefaultInstance()) {
      for (StudioProfiler profiler : getStudioProfilers().getProfilers()) {
        monitors.add(profiler.newMonitor());
      }
    }
    */

    monitors.forEach(ProfilerMonitor::enter);
  }

  @Override
  public void exit() {
    monitors.forEach(ProfilerMonitor::exit);
    monitors.clear();
  }

  @NotNull
  public List<ProfilerMonitor> getMonitors() {
    return monitors;
  }

  @Override
  public void setTooltip(ProfilerTooltip tooltip) {
    super.setTooltip(tooltip);
    monitors.forEach(monitor -> monitor
      .setFocus(getTooltip() instanceof ProfilerMonitorTooltip &&
                ((ProfilerMonitorTooltip)getTooltip()).getMonitor() == monitor));
  }
}

