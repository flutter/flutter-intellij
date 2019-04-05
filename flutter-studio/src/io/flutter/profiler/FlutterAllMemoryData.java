/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.adtui.model.*;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
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
  private static final Logger LOG = Logger.getInstance(FlutterAllMemoryData.class);

  // Indexes into multiData for each memory profiler value.
  private static final int HEAP_USED = 0;
  private static final int HEAP_CAPACITY = 1;
  private static final int EXTERNAL_MEMORY_USED = 2;
  // Resident set size (RSS) memory held by a process in main memory.
  private static final int RSS_SIZE = 3;
  private static final int GC_DATA = 4;
  private static final int SNAPSHOT = 5;
  private static final int RESET = 6;

  private boolean manualGC = false;

  boolean isManualGC() { return manualGC; }

  void setManualGC(boolean value) { manualGC = value; }

  // DataSeries of Use Heap space used, External space and Heap capacity.
  public class ThreadSafeData implements DataSeries<Long> {
    List<SeriesData<Long>> mData = new CopyOnWriteArrayList<SeriesData<Long>>();

    public ThreadSafeData() { }

    @Override
    public List<SeriesData<Long>> getDataForXRange(Range range) {
      // NOTE: adt-ui is off on the range, need to adjust. The view range (visible data in the chart passed) doesn't
      //       lineup with the last data point in the data series (we're looking at the next item to fill in now the
      //       current last item). All adt-ui profiling does a similar adjustment - not efficient but we match their code.
      range = new Range(range.getMin() - TimeUnit.SECONDS.toNanos(1), range.getMax() + TimeUnit.SECONDS.toNanos(1));
      List<SeriesData<Long>> outData = new ArrayList<>();

      // TODO(terry): Consider binary search for mData.x >= xRange.getMin() add till max
      final Iterator<SeriesData<Long>> it = mData.iterator();
      SeriesData<Long> lastData = null;

      while (it.hasNext()) {
        SeriesData<Long> data = it.next();
        if (data.x > range.getMax()) {
          break;
        }
        if (data.x >= range.getMin()) {
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
  //    5. GC
  //    6. Snapshot
  //    7. Reset memory statistics
  // And store a a data series.
  public FlutterAllMemoryData(Disposable parentDisposable, FlutterApp app) {
    multiData = new ArrayList<>();
    multiData.add(HEAP_USED, new ThreadSafeData());             // Heap used.
    multiData.add(HEAP_CAPACITY, new ThreadSafeData());         // Heap capacity.
    multiData.add(EXTERNAL_MEMORY_USED, new ThreadSafeData());  // External memory size.
    multiData.add(RSS_SIZE, new ThreadSafeData());              // RSS of application (process) memory.
    multiData.add(GC_DATA, new ThreadSafeData());               // GC occurred
    multiData.add(SNAPSHOT, new ThreadSafeData());              // Snapshot or reset memory statistics
    multiData.add(RESET, new ThreadSafeData());                 // Reset memory statistics

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
        // TODO(terry): This event isn't being called?
        // Add a GC event.
        int diffCapacity = oldHeapSpace.getCapacity() - newHeapSpace.getCapacity();
        long timestamp = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        int entriesLength = multiData.get(HEAP_CAPACITY).mData.size();
        if (entriesLength > 0) {
          SeriesData<Long> lastItem = multiData.get(HEAP_CAPACITY).mData.get(entriesLength - 1);
          long yValue = lastItem.value;
          multiData.get(GC_DATA).mData.add(new SeriesData<Long>(timestamp, yValue));
        }
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

          multiData.get(RSS_SIZE).mData.add(new SeriesData<>(sampleTime, (long)heapState.getRssBytes()));
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

  ThreadSafeData getGcDataSeries() { return multiData.get(GC_DATA); }

  ThreadSafeData getResetDataSeries() { return multiData.get(RESET); }

  ThreadSafeData getSnapshotDataSeries() { return multiData.get(SNAPSHOT); }

  // Added a GC event into the timeline.
  void recordGC() {
    int lastEntry = multiData.get(HEAP_CAPACITY).mData.size();
    SeriesData<Long> lastItem = multiData.get(HEAP_CAPACITY).mData.get(lastEntry - 1);
    long timestamp = lastItem.x;
    long yValue = lastItem.value;
    multiData.get(GC_DATA).mData.add(new SeriesData<Long>(timestamp, yValue));
  }

  // Added a Snapshot event into the timeline.
  void recordSnapshot() {
    int lastEntry = multiData.get(HEAP_CAPACITY).mData.size();
    SeriesData<Long> lastItem = multiData.get(HEAP_CAPACITY).mData.get(lastEntry - 1);
    long timestamp = lastItem.x;
    long yValue = lastItem.value;
    multiData.get(SNAPSHOT).mData.add(new SeriesData<Long>(timestamp, yValue));
  }

  // Add a reset memory statistics into the timeline.
  void recordReset() {
    int lastEntry = multiData.get(HEAP_CAPACITY).mData.size();
    SeriesData<Long> lastItem = multiData.get(HEAP_CAPACITY).mData.get(lastEntry - 1);
    long timestamp = lastItem.x;
    long yValue = lastItem.value;
    multiData.get(RESET).mData.add(new SeriesData<Long>(timestamp, yValue));
  }
}
