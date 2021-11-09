/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

/**
 * Kinds of performance reports supported by package:flutter.
 */
public enum PerfReportKind {
  repaint("repaint"),
  rebuild("rebuild");

  public final String name;

  PerfReportKind(String name) {
    this.name = name;
  }
}
