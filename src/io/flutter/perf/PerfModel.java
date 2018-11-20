/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.Disposable;

/**
 * Base class for all view models displaying performance stats
 */
public interface PerfModel {
  void markAppIdle();

  void clear();

  void onFrame();

  boolean isAnimationActive();
}
