/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.profiler;

import com.android.tools.profilers.*;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

// Refactored from Android 3.3 adt-ui code.
public class FlutterStudioMonitorStage extends FlutterStage {

  @NotNull
  private final List<ProfilerMonitor> monitors;

  public FlutterAllMemoryData.ThreadSafeData getMemoryUsedDataSeries() {
    return allMemoryData.getHeapUsedDataSeries();
  }

  public FlutterAllMemoryData.ThreadSafeData getMemoryMaxDataSeries() {
    return allMemoryData.getHeapMaxDetails();
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

