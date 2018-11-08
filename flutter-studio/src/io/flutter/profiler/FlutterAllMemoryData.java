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

// Refactored from Android Studio 3.2 adt-ui code.
public class FlutterAllMemoryData {
  // Indexes into mMultiData for each memory profiler value.
  private static final int HEAP_USED = 0;
  private static final int HEAP_CAPACITY = 1;
  private static final int EXTERNAL_MEMORY_USED = 2;

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
            // Copy data because of modification of the original by LineChart for stacked plotting.
            // TODO(terry): Replace next line with this "outData.add(data)" when data isn't modified by the chart.
            outData.add(new SeriesData<>(lastData.x, lastData.value));
          }
          // Copy data because of modification of the original by LineChart for stacked plotting.
          // TODO(terry): Replace next line with this "outData.add(data)" when data isn't modified by the chart.
          outData.add(new SeriesData<>(data.x, data.value));
        }
        lastData = data;
      }

      return outData;
    }
  }

  // mMultiData[HEAP_USED] is total heap in use.
  // mMultiData[HEAP_CAPACITY] is heap capacity.
  // mMultiData[EXTERNAL_MEMORY_USED] is total external in use.
  List<ThreadSafeData> mMultiData;

  public FlutterAllMemoryData(Disposable parentDisposable, FlutterApp app) {
    mMultiData = new ArrayList<>();
    mMultiData.add(HEAP_USED, new ThreadSafeData());              // Heap used.
    mMultiData.add(HEAP_CAPACITY, new ThreadSafeData());          // Heap capacity.
    mMultiData.add(EXTERNAL_MEMORY_USED, new ThreadSafeData());   // External memory size.

    HeapMonitor.HeapListener listener = new HeapMonitor.HeapListener() {
      @Override
      public void handleIsolatesInfo(VM vm, List<HeapMonitor.IsolateObject> isolates) {
        final HeapState heapState = new HeapState(60 * 1000);
        heapState.handleIsolatesInfo(vm, isolates);
        updateModel(heapState);
      }

      @Override
      public void handleGCEvent(IsolateRef iIsolateRef,
                                HeapMonitor.HeapSpace newHeapSpvace,
                                HeapMonitor.HeapSpace oldHeapSpace) {
        // TODO(terry): Add trashcan glyph for GC in timeline.
      }

      private void updateModel(HeapState heapState) {
        List<HeapMonitor.HeapSample> samples = heapState.getSamples();
        for (HeapMonitor.HeapSample sample : samples) {
          long sampleTime = TimeUnit.MILLISECONDS.toMicros(sample.getSampleTime());
          mMultiData.get(HEAP_USED).mData.add(new SeriesData<>(sampleTime,
                                                               (long)sample.getBytes()));
          mMultiData.get(HEAP_CAPACITY).mData.add(new SeriesData<>(sampleTime,
                                                                   (long)heapState.getCapacity()));
          mMultiData.get(EXTERNAL_MEMORY_USED).mData.add(new SeriesData<>(sampleTime,
                                                                          (long)sample.getExternal()));
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
    return mMultiData.get(HEAP_USED);
  }

  ThreadSafeData getCapacityDataSeries() {
    return mMultiData.get(HEAP_CAPACITY);
  }

  ThreadSafeData getExternalDataSeries() {
    return mMultiData.get(EXTERNAL_MEMORY_USED);
  }
}
