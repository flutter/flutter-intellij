/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public final class SnapshotDataSeries implements DataSeries<SnapshotData> {
  FlutterAllMemoryData.ThreadSafeData snapshotData;

  public SnapshotDataSeries(FlutterAllMemoryData.ThreadSafeData snapshotDataSeries) {
    snapshotData = snapshotDataSeries;
  }

  @Override
  public List<SeriesData<SnapshotData>> getDataForXRange(@NotNull Range xRange) {
    List<SeriesData<SnapshotData>> seriesData = new ArrayList<>();

    List<SeriesData<Long>> rawGcValues = snapshotData.getDataForXRange(xRange);
    for (SeriesData<Long> sample : rawGcValues) {
      seriesData.add(new SeriesData<>(sample.x, new SnapshotData(sample.value)));
    }
    return seriesData;
  }
}
