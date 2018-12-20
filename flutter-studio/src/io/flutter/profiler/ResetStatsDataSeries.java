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

public final class ResetStatsDataSeries implements DataSeries<ResetData> {
  FlutterAllMemoryData.ThreadSafeData resetData;

  public ResetStatsDataSeries(FlutterAllMemoryData.ThreadSafeData gcDataSeries) {
    resetData = gcDataSeries;
  }

  @Override
  public List<SeriesData<ResetData>> getDataForXRange(@NotNull Range xRange) {
    List<SeriesData<ResetData>> seriesData = new ArrayList<>();

    List<SeriesData<Long>> rawGcValues = resetData.getDataForXRange(xRange);
    for (SeriesData<Long> sample : rawGcValues) {
      seriesData.add(new SeriesData<>(sample.x, new ResetData(sample.value)));
    }
    return seriesData;
  }
}
