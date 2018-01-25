/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.util.EventDispatcher;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public class FlutterDartAnalysisData {
  private final DartAnalysisServerService myService;

  public FlutterDartAnalysisData(DartAnalysisServerService service) {
    myService = service;
  }
}
