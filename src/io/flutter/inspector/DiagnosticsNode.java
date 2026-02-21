/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal stub for DiagnosticsNode to support compilation of the perf package.
 * <p>
 * Full widget tree inspection requires InspectorService (not restored in this PR).
 * Use DevTools for full widget tree features.
 */
public class DiagnosticsNode {
  public int getLocationId() {
    return -1;
  }

  public CompletableFuture<ArrayList<DiagnosticsNode>> getChildren() {
    return CompletableFuture.completedFuture(null);
  }

  @Nullable
  public DiagnosticsNode getParent() {
    return null;
  }

  public boolean isStateful() {
    return false;
  }

  @Nullable
  public String getWidgetRuntimeType() {
    return null;
  }

  @Nullable
  public InspectorSourceLocation getCreationLocation() {
    return null;
  }

  /**
   * Minimal source location stub.
   */
  public static class InspectorSourceLocation {
    @Nullable
    public String getFile() {
      return null;
    }
  }
}
