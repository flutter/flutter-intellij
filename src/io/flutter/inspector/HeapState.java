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
package io.flutter.inspector;

import io.flutter.vmService.HeapMonitor;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.VM;

import java.text.DecimalFormat;
import java.util.*;

public class HeapState implements HeapMonitor.HeapListener {
  private static final DecimalFormat df = new DecimalFormat();
  private static final DecimalFormat df1 = new DecimalFormat();

  static {
    df.setMaximumFractionDigits(0);
    df1.setMaximumFractionDigits(1);
  }

  private int rssBytes;

  // Running count of the max heap (in bytes).
  private int heapMaxInBytes;

  private final HeapSamples samples;

  // Contains new and old heaps
  private final Map<String, List<HeapMonitor.HeapSpace>> isolateHeaps = new HashMap<>();

  public HeapState(int maxSampleSizeMs) {
    samples = new HeapSamples(maxSampleSizeMs);
  }

  public int getMaxSampleSizeMs() {
    return samples.maxSampleSizeMs;
  }

  public List<HeapMonitor.HeapSample> getSamples() {
    return samples.samples;
  }

  // Allocated heap size.
  public int getCapacity() {
    int max = heapMaxInBytes;

    for (HeapMonitor.HeapSample sample : samples.samples) {
      max = Math.max(max, sample.getBytes());
    }

    return max;
  }

  private static String printMb(int bytes) {
    return df.format(bytes / (1024 * 1024.0)) + "MB";
  }

  private static String printMb1(int bytes) {
    return df1.format(bytes / (1024 * 1024.0)) + "MB";
  }

  public String getRSSSummary() {
    return printMb(rssBytes) + " RSS";
  }

  public int getRssBytes() {
    return rssBytes;
  }

  public String getHeapSummary() {
    return printMb1(samples.samples.getLast().getBytes()) + " of " + printMb1(heapMaxInBytes);
  }

  public String getSimpleHeapSummary() {
    return printMb(samples.samples.getLast().getBytes());
  }

  void addSample(HeapMonitor.HeapSample sample) {
    samples.add(sample);
  }

  @Override
  public void handleIsolatesInfo(VM vm, List<HeapMonitor.IsolateObject> isolates) {
    int current = 0;
    int total = 0;
    int external = 0;

    isolateHeaps.clear();

    for (HeapMonitor.IsolateObject isolate : isolates) {
      isolateHeaps.put(isolate.getId(), isolate.getHeaps());

      for (HeapMonitor.HeapSpace heap : isolate.getHeaps()) {
        current += heap.getUsed();
        total += heap.getCapacity();
        external += heap.getExternal();
      }
    }

    rssBytes = vm.getJson().get("_currentRSS").getAsInt();
    heapMaxInBytes = total;

    addSample(new HeapMonitor.HeapSample(current, external, false));
  }

  @Override
  public void handleGCEvent(IsolateRef isolateRef, HeapMonitor.HeapSpace newHeapSpace, HeapMonitor.HeapSpace oldHeapSpace) {
    int current = 0;
    int external = 0;

    isolateHeaps.put(isolateRef.getId(), new ArrayList<>(Arrays.asList(newHeapSpace, oldHeapSpace)));

    for (List<HeapMonitor.HeapSpace> heaps : isolateHeaps.values()) {
      for (HeapMonitor.HeapSpace heap : heaps) {
        current += heap.getUsed();
        external += heap.getExternal();
      }
    }

    addSample(new HeapMonitor.HeapSample(current, external, true));
  }
}
