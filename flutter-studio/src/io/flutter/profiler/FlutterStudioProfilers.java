/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.FpsTimer;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StopwatchTimer;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.android.tools.profilers.*;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import io.flutter.server.vmService.HeapMonitor;
import io.flutter.inspector.HeapState;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.VM;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The suite of profilers inside Android Studio. This object is responsible for
 * maintaining the information global across all the profilers, device
 * management, process management, current state of the tool etc.
 * Refactored from Android 3.2 Studio adt-ui code.
 */
public class FlutterStudioProfilers
  extends AspectModel<ProfilerAspect> implements Updatable {

  /**
   * The number of updates per second our simulated object models receive.
   */
  public static final int PROFILERS_UPDATE_RATE = 60;

  @NotNull
  private FlutterStage stage;

  private final ProfilerTimeline timeline;

  private final List<FlutterStudioProfilers> profilers;

  private Updater updater;

  private AxisComponentModel viewAxis;

  private long refreshDevices;

  public Disposable getParentDisposable() {
    return parentDisposable;
  }

  public FlutterApp getApp() {
    return app;
  }

  private Disposable parentDisposable;
  private FlutterApp app;

  /**
   * Whether the profiler should auto-select a process to profile.
   */
  private boolean myAutoProfilingEnabled = true;

  public FlutterStudioProfilers(Disposable parentDisposable, FlutterApp app) {
    this(parentDisposable, app, new FpsTimer(PROFILERS_UPDATE_RATE));
  }

  public FlutterStudioProfilers(Disposable theParentDisposable, FlutterApp theApp,
                                StopwatchTimer timer) {
    updater = new Updater(timer);
    this.app = theApp;
    this.parentDisposable = theParentDisposable;

    ImmutableList.Builder<FlutterStudioProfilers> profilersBuilder =
      new ImmutableList.Builder<>();

    // TODO(terry): Supporting multiple profiles.
    /*
    profilersBuilder.add(new EventProfiler(this));
    profilersBuilder.add(new CpuProfiler(this));
    profilersBuilder.add(new MemoryProfiler(this));
    profilersBuilder.add(new NetworkProfiler(this));
*/

    profilers = profilersBuilder.build();

    timeline = new ProfilerTimeline(updater);
    syncVmClock();
    viewAxis = new ResizingAxisComponentModel.Builder(timeline.getViewRange(),
                                                      TimeAxisFormatter.DEFAULT)
      .setGlobalRange(timeline.getDataRange()).build();
    setMonitoringStage();
  }

  private void syncVmClock() {
    boolean[] isClockSynced = {false};
    HeapMonitor.HeapListener listener = new HeapMonitor.HeapListener() {
      @Override
      public void handleIsolatesInfo(VM vm,
                                     List<HeapMonitor.IsolateObject> isolates) {
        final HeapState heapState = new HeapState(60 * 1000);
        heapState.handleIsolatesInfo(vm, isolates);
        updateModel(heapState);
      }

      @Override
      public void handleGCEvent(IsolateRef iIsolateRef,
                                HeapMonitor.HeapSpace newHeapSpace,
                                HeapMonitor.HeapSpace oldHeapSpace) {
      }

      private void updateModel(HeapState heapState) {
        if (!isClockSynced[0]) {
          isClockSynced[0] = true;
          long timeUs =
            TimeUnit.MILLISECONDS.toNanos(heapState.getSamples()
                                            .get(0).getSampleTime());
          timeline.reset(timeUs, timeUs);
        }
      }
    };
    assert app.getVMServiceManager() != null;
    app.getVMServiceManager().addHeapListener(listener);
    Disposer.register(parentDisposable,
                      () -> app.getVMServiceManager().removeHeapListener(listener));
  }

  public boolean isStopped() {
    return !updater.isRunning();
  }

  public void stop() {
    if (isStopped()) {
      // Profiler is already stopped. Nothing to do. Ideally, this method
      // shouldn't be called when the profiler is already stopped. However,
      // some exceptions might be thrown when listeners are notified about
      // ProfilerAspect.STAGE aspect change and react accordingly. In this
      // case, we could end up with an inconsistent model and allowing to
      // try to call stop and notify the listeners again can only make it
      // worse. Therefore, we return early to avoid making the model problem
      // bigger.
      return;
    }
    // The following line can't throw an exception, will stop the updater's
    // timer and guarantees future calls to isStopped() return true.
    updater.stop();
    changed(ProfilerAspect.STAGE);
  }

  @Override
  public void update(long elapsedNs) {
    refreshDevices += elapsedNs;
    if (refreshDevices < TimeUnit.SECONDS.toNanos(1)) {
      return;
    }
    refreshDevices = 0;

    // These need to be fired every time the process list changes so that
    // the device/process dropdown always reflects the latest.
    //changed(ProfilerAspect.DEVICES);
    changed(ProfilerAspect.PROCESSES);
  }

  public void setMonitoringStage() {
    setStage(new FlutterStudioMonitorStage(this));
  }

  @NotNull
  public FlutterStage getStage() {
    return stage;
  }

  @Nullable
  public ProfilerClient getClient() {
    return null;
  }

  /**
   * Return the selected app's device name running.
   */
  @NotNull
  public String getSelectedAppName() {
    return app.deviceId();
  }

  public void setStage(@NotNull FlutterStage theStage) {
    if (stage != null) {
      stage.exit();
    }
    getTimeline().getSelectionRange().clear();
    stage = theStage;
    stage.getStudioProfilers().getUpdater().reset();
    stage.enter();
    this.changed(ProfilerAspect.STAGE);
  }

  @NotNull
  public ProfilerTimeline getTimeline() {
    return timeline;
  }

  public List<FlutterStudioProfilers> getProfilers() {
    return profilers;
  }

  public ProfilerMode getMode() {
    return stage.getProfilerMode();
  }

  public void modeChanged() {
    changed(ProfilerAspect.MODE);
  }

  public Updater getUpdater() {
    return updater;
  }

  public AxisComponentModel getViewAxis() {
    return viewAxis;
  }

  /**
   * Return the list of stages that target a specific profiler, which a user might want to jump
   * between. This should exclude things like the top-level profiler stage, null stage, etc.
   */
  public List<Class<? extends FlutterStage>> getDirectStages() {
    ImmutableList.Builder<Class<? extends FlutterStage>> listBuilder = ImmutableList.builder();

    // TODO(terry): Support multiple profilers?
    /*
    listBuilder.add(CpuProfilerStage.class);
    listBuilder.add(MemoryProfilerStage.class);
    listBuilder.add(NetworkProfilerStage.class);
    */

    return listBuilder.build();
  }

  @NotNull
  public Class<? extends FlutterStage> getStageClass() {
    return stage.getClass();
  }

  // TODO: Unify with how monitors expand.
  public void setNewStage(Class<? extends FlutterStage> clazz) {
    try {
      Constructor<? extends FlutterStage> constructor =
        clazz.getConstructor(FlutterStudioProfilers.class);
      FlutterStage stage = constructor.newInstance(this);
      setStage(stage);
    }
    catch (NoSuchMethodException | IllegalAccessException
      | InstantiationException | InvocationTargetException e) {
      // will not happen
    }
  }
}
