/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import io.flutter.vmService.HeapMonitor;
import org.dartlang.vm.service.element.MemoryUsage;

import java.text.DecimalFormat;
import java.util.List;

public class HeapState {
  private static final DecimalFormat df = new DecimalFormat();

  static {
    df.setMaximumFractionDigits(1);
  }

  // Running count of the max heap (in bytes).
  private int heapMaxInBytes;

  private final HeapSamples samples;

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

  public String getHeapSummary() {
    return printMb(samples.samples.getLast().getBytes()) + " of " + printMb(heapMaxInBytes);
  }

  void addSample(HeapMonitor.HeapSample sample) {
    samples.addSample(sample);
  }

  public void handleMemoryUsage(List<MemoryUsage> memoryUsages) {
    int current = 0;
    int total = 0;
    int external = 0;

    for (MemoryUsage usage : memoryUsages) {
      current += usage.getHeapUsage();
      total += usage.getHeapCapacity();
      external += usage.getExternalUsage();
    }

    heapMaxInBytes = total;

    addSample(new HeapMonitor.HeapSample(current, external));
  }
}
