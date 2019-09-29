/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.util.TextRange;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorObjectGroupManager;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.InspectorStateService;
import io.flutter.run.daemon.FlutterApp;

import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static io.flutter.inspector.InspectorService.toSourceLocationUri;
import static java.lang.Math.min;

/**
 * Base class for a controller managing UI describing a widget.
 */
public abstract class WidgetViewController implements CustomHighlighterRenderer,
                                                      EditorMouseEventService.Listener,
                                                      InspectorStateService.Listener,
                                                      EditorPositionService.Listener, Disposable {
  protected boolean isSelected = false;
  // TODO(jacobr): make this private.
  final WidgetViewModelData data;

  boolean visible = false;
  private InspectorObjectGroupManager groups;
  boolean isDisposed = false;

  Rectangle visibleRect;
  DiagnosticsNode inspectorSelection;
  InspectorService inspectorService;
  Listener listener;

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public FlutterApp getApp() {
    if (inspectorService == null) return null;
    return inspectorService.getApp();
  }

  InspectorObjectGroupManager getGroups() {
    final InspectorService service = getInspectorService();
    if (service == null) return null;
    if (groups == null || groups.getInspectorService() != service) {
      groups = new InspectorObjectGroupManager(service, "active");
    }
    return groups;
  }

  public ArrayList<DiagnosticsNode> elements;
  public int activeIndex = 0;

  WidgetViewController(WidgetViewModelData data) {
    this.data = data;
    data.context.inspectorStateService.addListener(this);
    if (data.context.editor != null) {
      data.context.editorPositionService.addListener(data.context.editor, this);
    }
  }

  /**
   * Subclasses can override this method to be notified when whether the widget is visible in IntelliJ.
   *
   * This is whether the UI for this component is visible not whether the widget is visible on the device.
   */
  public void onVisibleChanged() {
  }

  @Override
  public void updateVisibleArea(Rectangle newRectangle) {
    visibleRect = newRectangle;
    if (getDescriptor() == null || data.getMarker() == null) {
      if (!visible) {
        visible = true;
        onVisibleChanged();
      }
      return;
    }
    final TextRange marker = data.getMarker();
    if (marker == null) return;

    final Point start = offsetToPoint(marker.getStartOffset());
    final Point end = offsetToPoint(marker.getEndOffset());
    final boolean nowVisible = newRectangle == null || newRectangle.y <= end.y && newRectangle.y + newRectangle.height >= start.y ||
                               updateVisiblityLocked(newRectangle);
    if (visible != nowVisible) {
      visible = nowVisible;
      onVisibleChanged();
    }
  }
  public boolean updateVisiblityLocked(Rectangle newRectangle) { return false; }

  @Override
  public void onInspectorAvailable(InspectorService inspectorService) {
    if (this.inspectorService == inspectorService) return;
    setElements(null);
    inspectorSelection = null;
    this.inspectorService = inspectorService;
    groups = null;
    onVisibleChanged();
  }

  public Point offsetToPoint(int offset) {
    return data.context.editor.visualPositionToXY( data.context.editor.offsetToVisualPosition(offset));
  }

  // @Override
  public void forceRender() {
    if (!visible) return;

    if (listener != null) {
      listener.forceRender();
    }
    if (data.context.editor != null) {
      data.context.editor.getComponent().repaint(); // XXX repaint rect?
    }
    /*
    if (data.descriptor == null) {
      // TODO(just repaint the sreenshot area.
      data.editor.repaint(0, data.document.getTextLength());
      return;
    }
    data.editor.repaint(0, data.document.getTextLength());

     */
/*
    final TextRange marker = data.getMarker();
    if (marker == null) return;

    data.editor.repaint(marker.getStartOffset(), marker.getEndOffset());
 */
  }

   public interface Listener {
     void forceRender();
   }

  public InspectorService getInspectorService() {
    return inspectorService;
  }

  boolean setSelection(boolean value) {
    if (value == isSelected) return false;
    isSelected = value;
    if (data.descriptor == null) {
      // TODO(jacobr): do we want to display any selection for the global preview?
      return true;
    }

    if (value) {
      // XXX computeActiveElements();
    }
    return true;
  }

  @Override
  public void dispose() {
    if (isDisposed) return;
    isDisposed = true;
    // Descriptors must be disposed so they stop getting notified about
    // changes to the Editor.
    data.context.inspectorStateService.removeListener(this);

    data.context.inspectorStateService.addListener(this);
    if (data.context.editor != null) {
      data.context.editorPositionService.removeListener(data.context.editor, this);
    }

    // TODO(Jacobr): fix missing code disposing descriptors?
    if (groups != null) {
      groups.clear(false);// XXX??
      groups = null;
    }
  }

  @Override
  public boolean isValid() {
    if (data.context.editor == null || !data.context.editor.isDisposed()) return true;
    dispose();
    return false;
  }

  @Override
  public void onSelectionChanged(DiagnosticsNode selection) {
    if (data.context.editor != null && data.context.editor.isDisposed()) {
      return;
    }

    final InspectorObjectGroupManager manager = getGroups();
    if (manager != null ){
      manager.cancelNext();;
    }
    // XX this might be too much.
    // It isn't that the visiblity changed it is that the selection changed.
    onVisibleChanged();
  }

  InspectorService.Location getLocation() {
    if (data.descriptor == null || data.descriptor.widget == null) return null;

    return InspectorService.Location.outlineToLocation(data.context.editor, data.descriptor.outlineNode);
  }

  void computeActiveElementsDeprecated() {
    final InspectorObjectGroupManager groupManager = getGroups();
    if (groupManager == null) {
      return;
      // XXX be smart based on if the element actually changed. The ValueId should work for this.
      //        screenshot = null;
    }
    groupManager.cancelNext();
    final InspectorService.ObjectGroup group = groupManager.getNext();
    // XXX
    final String file = data.context.editor != null ? toSourceLocationUri(data.context.editor.getVirtualFile().getPath()) : null;
    final CompletableFuture<ArrayList<DiagnosticsNode>> nodesFuture;
    if (data.descriptor == null) {
      // Special case for whole app preview.
      nodesFuture = group.getElementForScreenshot().thenApply((node) -> {
        final ArrayList<DiagnosticsNode> ret = new ArrayList<>();
        if (node != null) {
          ret.add(node);
        }
        return ret;
      });
    } else {
      nodesFuture = group.getElementsAtLocation(getLocation(), 10);
    }
    group.safeWhenComplete(nodesFuture, (nextNodes, error) -> {
      if (error != null || isDisposed) {
        setElements(null);
        activeIndex = 0;
        return;
      }
      final InspectorObjectGroupManager manager = getGroups();
      if (isEquivalent(elements, nextNodes)) {
        onMaybeFetchScreenshot();
        manager.cancelNext();
        // Continue using the current.
      } else {
        // TODO(jacobr): simplify this logic.
        if (elements != null && nextNodes != null && elements.size() == nextNodes.size() && nextNodes.size() > 1) {
          boolean found = false;
          for (int i = 0; i < elements.size(); i++) {
            if (nextNodes.get(0).getValueRef().equals(elements.get(i).getValueRef())) {
              if (i <= activeIndex) {
                // Hacky.. fixup as we went backwards.
                activeIndex = Math.max(0, i - 1);
              } else {
                activeIndex = i;
              }
              found = true;
              break;
            }
          }
          if (!found) {
            activeIndex = 0;
          }
        } else {
          activeIndex = 0;
        }
        manager.promoteNext();
        setElements(nextNodes);

        onActiveElementsChanged();
      }
    });
  }

  public void onMaybeFetchScreenshot()
  {

  }

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

  public WidgetIndentGuideDescriptor getDescriptor() { return data.descriptor; }

  public DiagnosticsNode getCurrentNode() {
    if (elements == null || elements.isEmpty()) {
      return null;
    }
    return elements.get(0);
  }
}
