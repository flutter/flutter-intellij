/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import io.flutter.inspector.InspectorService.ObjectGroup;

import java.util.concurrent.CompletableFuture;

/**
 * Manager that simplifies preventing memory leaks when using the
 * InspectorService.
 * <p>
 * This class is designed for the use case where you want to manage
 * object references associated with the current displayed UI and object
 * references associated with the candidate next frame of UI to display. Once
 * the next frame is ready, you determine whether you want to display it and
 * discard the current frame and promote the next frame to the the current
 * frame if you want to display the next frame otherwise you discard the next
 * frame.
 * <p>
 * To use this class load all data you want for the next frame by using
 * the object group specified by getNext() and then if you decide to switch
 * to display that frame, call promoteNext() otherwise call clearNext().
 */
public class InspectorObjectGroupManager {
  private final InspectorService inspectorService;
  private final String debugName;
  private ObjectGroup current;
  private ObjectGroup next;

  private CompletableFuture<Void> pendingNextFuture;

  public InspectorService getInspectorService() { return inspectorService; }
  public InspectorObjectGroupManager(InspectorService inspectorService, String debugName) {
    this.inspectorService = inspectorService;
    this.debugName = debugName;
  }

  public CompletableFuture<?> getPendingUpdateDone() {
    if (pendingNextFuture != null) {
      return pendingNextFuture;
    }
    if (next == null) {
      // There is no pending update.
      return CompletableFuture.completedFuture(null);
    }

    pendingNextFuture = new CompletableFuture<>();
    return pendingNextFuture;
  }

  public ObjectGroup getCurrent() {
    if (current == null) {
      current = inspectorService.createObjectGroup(debugName);
    }
    return current;
  }

  public ObjectGroup getNext() {
    if (next == null) {
      next = inspectorService.createObjectGroup(debugName);
    }
    return next;
  }

  public void clear(boolean isolateStopped) {
    if (isolateStopped) {
      // The Dart VM will handle GCing the underlying memory.
      current = null;
      setNextNull();
    }
    else {
      clearCurrent();
      cancelNext();
    }
  }

  public void promoteNext() {
    clearCurrent();
    current = next;
    setNextNull();
  }

  private void clearCurrent() {
    if (current != null) {
      current.dispose();
      current = null;
    }
  }

  public void cancelNext() {
    if (next != null) {
      next.dispose();
      setNextNull();
    }
  }

  private void setNextNull() {
    next = null;
    if (pendingNextFuture != null) {
      pendingNextFuture.complete(null);
      pendingNextFuture = null;
    }
  }
}
