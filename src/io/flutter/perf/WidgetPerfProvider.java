/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import io.flutter.inspector.DiagnosticsNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining what information about widget performance can be fetched
 * from the running device.
 *
 * See VMServiceWidgetPerfProvider for the non-test implementation of this class.
 */
public interface WidgetPerfProvider extends Disposable {
  void setTarget(WidgetPerfListener widgetPerfListener);

  boolean isStarted();

  boolean isConnected();

  boolean shouldDisplayPerfStats(FileEditor editor);

  CompletableFuture<DiagnosticsNode> getWidgetTree();
}
