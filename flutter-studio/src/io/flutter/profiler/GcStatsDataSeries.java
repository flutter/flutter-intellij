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

public final class GcStatsDataSeries implements DataSeries<GcDurationData> {
  FlutterAllMemoryData.ThreadSafeData gcData;

  public GcStatsDataSeries(FlutterAllMemoryData.ThreadSafeData gcDataSeries) {
    gcData = gcDataSeries;
  }

  @Override
  public List<SeriesData<GcDurationData>> getDataForXRange(@NotNull Range xRange) {
    // TODO: Change the Memory API to allow specifying padding in the request as number of samples.

    List<SeriesData<GcDurationData>> seriesData = new ArrayList<>();

    List<SeriesData<Long>> rawGcValues = gcData.getDataForXRange(xRange);
    for (SeriesData<Long> sample : rawGcValues) {
      seriesData.add(new SeriesData<>(sample.x, new GcDurationData(sample.value)));
    }
    return seriesData;
  }
}
