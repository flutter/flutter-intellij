/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.TextEditor;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * View model for displaying perf stats for a TextEditor.
 *
 * This model tracks the state defining how perf stats should be displayed in
 * the text editor along with the actual perf stats accessible via the
 * getStats method.
 */
public interface EditorPerfModel extends PerfModel, Disposable {
  @NotNull
  FilePerfInfo getStats();

  @NotNull
  TextEditor getTextEditor();

  FlutterApp getApp();

  boolean getAlwaysShowLineMarkers();

  void setAlwaysShowLineMarkersOverride(boolean show);

  void setPerfInfo(FilePerfInfo stats);
}
