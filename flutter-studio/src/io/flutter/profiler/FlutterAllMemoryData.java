/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.adtui.model.*;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import io.flutter.inspector.HeapState;
import io.flutter.server.vmService.HeapMonitor;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.VM;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

// Refactored from Android Studio 3.2 adt-ui code.
public class FlutterAllMemoryData {
  // Indexes into multiData for each memory profiler value.
  private static final int HEAP_USED = 0;
  private static final int HEAP_CAPACITY = 1;
  private static final int EXTERNAL_MEMORY_USED = 2;
  // Resident set size (RSS) memory held by a process in main memory.
  private static final int RSS_SIZE = 3;

  private boolean manualGC = false;

  boolean isManualGC() { return manualGC; }
  void setManualGC(boolean value) { manualGC = value; }

  // DataSeries of Use Heap space used, External space and Heap capacity.
  public class ThreadSafeData implements DataSeries<Long> {
    List<SeriesData<Long>> mData = new CopyOnWriteArrayList<SeriesData<Long>>();

    public ThreadSafeData() { }

    @Override
    public List<SeriesData<Long>> getDataForXRange(Range xRange) {
      List<SeriesData<Long>> outData = new ArrayList<>();

      // TODO(terry): Consider binary search for mData.x >= xRange.getMin() add till max
      final Iterator<SeriesData<Long>> it = mData.iterator();
      SeriesData<Long> lastData = null;

      while (it.hasNext()) {
        SeriesData<Long> data = it.next();
        if (data.x > xRange.getMax()) {
          break;
        }
        if (data.x >= xRange.getMin()) {
          // TODO(terry): Hack for now to get last item so we don't flicker. Consider using a for/loop instead of iterator.
          if (lastData != null && outData.isEmpty()) {
            // TODO(terry): Copy data because of modification of the original by LineChart for stacked plotting.
            //              Replace next line with this "outData.add(data)" when data isn't modified by the chart.
            outData.add(new SeriesData<>(lastData.x, lastData.value));
          }
          // TODO(terry): Copy data because of modification of the original by LineChart for stacked plotting.
          //              Replace next line with this "outData.add(data)" when data isn't modified by the chart.
          outData.add(new SeriesData<>(data.x, data.value));
        }
        lastData = data;
      }

      // TODO(terry): Hack to display legend if outData series is empty show the last value in the series.  Expected
      //              getTimeline().getDataRange() passed to new SeriesLegend in FlutterStudioMonitorStageView to be
      //              within the series x-range.
      if (outData.size() == 0 && lastData != null)
        outData.add(lastData);
      return outData;
    }
  }

  // multiData[HEAP_USED] is total heap in use.
  // multiData[HEAP_CAPACITY] is heap capacity.
  // multiData[EXTERNAL_MEMORY_USED] is total external in use.
  // multiData[RSS_SIZE] is Resident Set Size of the application (process) in memory.
  List<ThreadSafeData> multiData;

  // Hookup VM listeners to collect all the VM memory information:
  //    1. heap used
  //    2. external space used
  //    3. heap capacity
  //    4. RSS (Resident Set Size)
  // And store a a data series.
  public FlutterAllMemoryData(Disposable parentDisposable, FlutterApp app) {
    multiData = new ArrayList<>();
    multiData.add(HEAP_USED, new ThreadSafeData());             // Heap used.
    multiData.add(HEAP_CAPACITY, new ThreadSafeData());         // Heap capacity.
    multiData.add(EXTERNAL_MEMORY_USED, new ThreadSafeData());  // External memory size.
    multiData.add(RSS_SIZE, new ThreadSafeData());              // RSS of application (process) memory.

    HeapMonitor.HeapListener listener = new HeapMonitor.HeapListener() {
      @Override
      public void handleIsolatesInfo(VM vm, List<HeapMonitor.IsolateObject> isolates) {
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
        List<HeapMonitor.HeapSample> samples = heapState.getSamples();
        for (HeapMonitor.HeapSample sample : samples) {
          long sampleTime = TimeUnit.MILLISECONDS.toMicros(sample.getSampleTime());
          multiData.get(HEAP_USED).mData.add(new SeriesData<>(sampleTime,
                                                              (long)sample.getBytes()));
          multiData.get(HEAP_CAPACITY).mData.add(new SeriesData<>(sampleTime,
                                                                  (long)heapState.getCapacity() + sample.getExternal()));
          multiData.get(EXTERNAL_MEMORY_USED).mData.add(new SeriesData<>(sampleTime,
                                                                         (long)sample.getExternal()));

          String rssString = heapState.getRSSSummary();
          rssString = rssString.substring(0, rssString.indexOf(" RSS"));
          int rssLength = rssString.length();
          int rssSize = Integer.valueOf(rssString.substring(0, rssLength - 2));
          String rssUnit = rssString.substring(rssLength - 2);
          if (rssUnit.equals("KB")) {
            rssSize *= 1000;
          } else if (rssUnit.equals("MB")) {
            rssSize *= 1000000;
          } else if (rssUnit.equals("GB")) {
            rssSize *= 1000000000;
          }
          multiData.get(RSS_SIZE).mData.add(new SeriesData<>(sampleTime, (long)rssSize));
        }
      }
    };

    assert app.getVMServiceManager() != null;
    app.getVMServiceManager().addHeapListener(listener);
    Disposer.register(parentDisposable, () -> app.getVMServiceManager().removeHeapListener(listener));
  }

  protected RangedContinuousSeries createRangedSeries(StudioProfilers profilers, String name, Range range) {
    return new RangedContinuousSeries(name, profilers.getTimeline().getViewRange(), range, getCapacityDataSeries());
  }

  ThreadSafeData getUsedDataSeries() {
    return multiData.get(HEAP_USED);
  }

  ThreadSafeData getCapacityDataSeries() {
    return multiData.get(HEAP_CAPACITY);
  }

  ThreadSafeData getExternalDataSeries() {
    return multiData.get(EXTERNAL_MEMORY_USED);
  }

  ThreadSafeData getRSSDataSeries() {
    return multiData.get(RSS_SIZE);
  }

}
