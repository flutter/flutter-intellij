/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorObjectGroupManager;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.InspectorGroupManagerService;
import io.flutter.run.daemon.FlutterApp;

import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;

import static java.lang.Math.min;

/**
 * Base class for a controller managing UI describing a widget.
 *
 * See PreviewViewController which extends this class to render previews of
 * widgets. This class is also intented to be extended to support rendering
 * visualizations of widget properties inline in a text editor.
 */
public abstract class WidgetViewController implements EditorMouseEventService.Listener, Disposable {
  protected boolean isSelected = false;
  protected final WidgetViewModelData data;

  protected boolean visible = false;
  boolean isDisposed = false;

  Rectangle visibleRect;
  DiagnosticsNode inspectorSelection;

  final InspectorGroupManagerService.Client groupClient;

  protected abstract void onFlutterFrame();

  public InspectorObjectGroupManager getGroupManagner() {
    return groupClient.getGroupManager();
  }
  public FlutterApp getApp() {
    return groupClient.getApp();
  }

  InspectorObjectGroupManager getGroups() {
    return groupClient.getGroupManager();
  }

  public ArrayList<DiagnosticsNode> elements;
  public int activeIndex = 0;

  WidgetViewController(WidgetViewModelData data, Disposable parent) {
    this.data = data;
    Disposer.register(parent, this);
    groupClient = new InspectorGroupManagerService.Client(parent) {
      @Override
      public void onInspectorAvailabilityChanged() {
        WidgetViewController.this.onInspectorAvailabilityChanged();
      }

      @Override
      public void requestRepaint(boolean force) {
        onFlutterFrame();
      }

      @Override
      public void onFlutterFrame() {
        WidgetViewController.this.onFlutterFrame();
      }

      public void onSelectionChanged(DiagnosticsNode selection) {
        WidgetViewController.this.onSelectionChanged(selection);
      }
    };
    data.context.inspectorGroupManagerService.addListener(groupClient, parent);
  }

  /**
   * Subclasses can override this method to be notified when whether the widget is visible in IntelliJ.
   *
   * This is whether the UI for this component is visible not whether the widget is visible on the device.
   */
  public void onVisibleChanged() {
  }

  public boolean updateVisiblityLocked(Rectangle newRectangle) { return false; }

  public void onInspectorAvailabilityChanged() {
    setElements(null);
    inspectorSelection = null;
    onVisibleChanged();
    forceRender();
  }

  // @Override
  public abstract void forceRender();

  public InspectorService getInspectorService() {
    return groupClient.getInspectorService();
  }

  @Override
  public void dispose() {
  }

  public void onSelectionChanged(DiagnosticsNode selection) {

    final InspectorObjectGroupManager manager = getGroups();
    if (manager != null ){
      manager.cancelNext();;
    }
  }

  abstract InspectorService.Location getLocation();

  public boolean isElementsEmpty() {
    return elements == null || elements.isEmpty() || isDisposed;
  }

  public void setElements(ArrayList<DiagnosticsNode> elements) {
    this.elements = elements;
  }

  public void onActiveElementsChanged() {
    if (isElementsEmpty()) return;
    final InspectorObjectGroupManager manager = getGroups();
    if (manager == null) return;

    if (isSelected) {
      manager.getCurrent().setSelection(
        getSelectedElement().getValueRef(),
        false,
        true
      );
    }
  }

  public DiagnosticsNode getSelectedElement() {
    if (isElementsEmpty()) return null;
    return elements.get(0);
  }

  private boolean isEquivalent(ArrayList<DiagnosticsNode> a, ArrayList<DiagnosticsNode> b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      if (!isEquivalent(a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean isEquivalent(DiagnosticsNode a, DiagnosticsNode b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    if (!Objects.equals(a.getValueRef(), b.getValueRef())) return false;
    if (!a.identicalDisplay(b)) return false;
    return true;
  }
}
