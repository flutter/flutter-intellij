/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.gson.JsonObject;

/**
 * Interface defining events relevant to tracking widget performance.
 * <p>
 * See FlutterWidgetPerf which is the cannonical implementation.
 */
public interface WidgetPerfListener {
  void requestRepaint(When when);

  void onWidgetPerfEvent(PerfReportKind kind, JsonObject json);

  void onNavigation();

  void addPerfListener(PerfModel listener);

  void removePerfListener(PerfModel listener);
}
