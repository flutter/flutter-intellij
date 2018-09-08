/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface WidgetPerfProvider extends Disposable {
  void setTarget(Repaintable repaintable);

  boolean isStarted();

  boolean isConnected();

  CompletableFuture<JsonObject> getPerfSourceReports(List<String> paths);

  boolean shouldDisplayPerfStats(FileEditor editor);
}
