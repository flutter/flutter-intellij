/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.google.common.base.Joiner;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.FlutterUtils;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.TreeUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CompletableFuture;

/**
 * Listener that handles mouse interaction for the Inspector tree.
 * <p>
 * Handles displaying tooltips, notifying the InspectorPanel of what node is
 * being highlighted, and custom double click selection behavior.
 */
class InspectorTreeMouseListener extends MouseAdapter {
  private InspectorPanel panel;
  private final JTree tree;
  private DefaultMutableTreeNode lastHover;

  private static final Logger LOG = Logger.getInstance(InspectorTreeMouseListener.class);

  InspectorTreeMouseListener(InspectorPanel panel, JTree tree) {
    this.panel = panel;
    this.tree = tree;
  }

  @Override
  public void mouseExited(MouseEvent e) {
    clearTooltip();
    endShowNode();
  }

  @Override
  public void mouseClicked(MouseEvent event) {
    final DefaultMutableTreeNode node = getClosestTreeNode(event);
    // TODO(jacobr): support clicking on a property.
    // It would be reasonable for that to trigger selecting the parent of the
    // property.
    final DiagnosticsNode diagnostic = TreeUtils.maybeGetDiagnostic(node);
    if (diagnostic != null && !diagnostic.isProperty()) {
      // A double click triggers forcing changing the subtree root.
      if (event.getClickCount() == 2) {
        if (panel.isSummaryTree) {
          panel.applyNewSelection(diagnostic, diagnostic, true, false);
        }
        else if (panel.parentTree != null) {
          panel.parentTree.applyNewSelection(
            panel.firstAncestorInParentTree(node), diagnostic, true, false);
        }
      }
    }
    event.consume();
  }

  private void clearTooltip() {
    final DiagnosticsTreeCellRenderer r = (DiagnosticsTreeCellRenderer)tree.getCellRenderer();
    r.setToolTipText(null);
    lastHover = null;
  }

  @Override
  public void mouseMoved(MouseEvent event) {
    calculateTooltip(event);
    final DefaultMutableTreeNode treeNode = getTreeNode(event);
    final DiagnosticsNode node = TreeUtils.maybeGetDiagnostic(treeNode);
    if (node != null && !node.isProperty()) {
      if (panel.detailsSubtree && panel.isCreatedByLocalProject(node)) {
        panel.parentTree.highlightShowNode(node.getValueRef());
      }
      else if (panel.subtreePanel != null) {
        panel.subtreePanel.highlightShowNode(node.getValueRef());
      }
      panel.highlightShowNode(treeNode);
    }
  }

  private void endShowNode() {
    if (panel.detailsSubtree) {
      panel.parentTree.endShowNode();
    }
    else if (panel.subtreePanel != null) {
      panel.subtreePanel.endShowNode();
    }
    panel.endShowNode();
  }

  private void calculateTooltip(MouseEvent event) {
    final Point p = event.getPoint();
    final int row = tree.getClosestRowForLocation(p.x, p.y);

    final TreeCellRenderer r = tree.getCellRenderer();
    if (r == null) {
      return;
    }
    if (row == -1) {
      clearTooltip();
      return;
    }
    final TreePath path = tree.getPathForRow(row);
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    lastHover = node;
    final Component rComponent = r.getTreeCellRendererComponent(tree, node, tree.isRowSelected(row), tree.isExpanded(row),
                                                                tree.getModel().isLeaf(node), row, true);
    final Rectangle pathBounds = tree.getPathBounds(path);
    if (pathBounds == null) {
      // Something went wrong and the path isn't really visible.
      return;
    }
    p.translate(-pathBounds.x, -pathBounds.y);
    if (rComponent == null) {
      clearTooltip();
      return;
    }

    String tooltip = null;
    final DiagnosticsTreeCellRenderer renderer = (DiagnosticsTreeCellRenderer)rComponent;
    final DiagnosticsNode diagnostic = TreeUtils.maybeGetDiagnostic(node);
    if (diagnostic != null) {
      if (diagnostic.hasTooltip()) {
        tooltip = diagnostic.getTooltip();
      }
      final Icon icon = renderer.getIconAt(p.x);
      if (icon != null) {
        if (icon == panel.defaultIcon) {
          tooltip = "default value";
        }
      }
      else {
        if (diagnostic.getShowName()) {
          final int fragmentIndex = renderer.findFragmentAt(p.x);
          if (fragmentIndex == 0) {
            // The name fragment is being hovered over.
            // Set property description in tooltip.
            // TODO (pq):
            //  * consider tooltips for values
            //  * consider rich navigation hovers (w/ styling and navigable docs)
            final CompletableFuture<String> propertyDoc = diagnostic.getPropertyDoc();
            final String doc = propertyDoc.getNow(null);
            if (doc != null) {
              tooltip = doc;
            }
            else {
              tooltip = "Loading dart docs...";
              diagnostic.safeWhenComplete(propertyDoc, (String tip, Throwable th) -> {
                if (th != null) {
                  FlutterUtils.warn(LOG, th);
                }
                if (lastHover == node) {
                  // We are still hovering of the same node so show the user the tooltip.
                  renderer.setToolTipText(tip);
                }
              });
            }
          }
          else {
            if (diagnostic.isEnumProperty()) {
              // We can display a better tooltip as we have access to introspection
              // via the observatory service.
              diagnostic.safeWhenComplete(diagnostic.getValueProperties(), (properties, th) -> {
                if (properties == null || lastHover != node) {
                  return;
                }
                renderer.setToolTipText("Allowed values:\n" + Joiner.on('\n').join(properties.keySet()));
              });
            }
            else {
              renderer.setToolTipText(diagnostic.getTooltip());
            }
          }
        }
      }
    }
    renderer.setToolTipText(tooltip);
  }

  /**
   * Match IntelliJ's fuzzier standards for what it takes to select a node.
   */
  private DefaultMutableTreeNode getClosestTreeNode(MouseEvent event) {

    final Point p = event.getPoint();
    final int row = tree.getClosestRowForLocation(p.x, p.y);

    final TreeCellRenderer r = tree.getCellRenderer();
    if (row == -1 || r == null) {
      return null;
    }
    final TreePath path = tree.getPathForRow(row);
    return (DefaultMutableTreeNode)path.getLastPathComponent();
  }

  /**
   * Requires the mouse to actually be over a tree node.
   */
  private DefaultMutableTreeNode getTreeNode(MouseEvent event) {
    final Point p = event.getPoint();
    final int row = tree.getRowForLocation(p.x, p.y);
    final TreeCellRenderer r = tree.getCellRenderer();
    if (row == -1 || r == null) {
      return null;
    }
    final TreePath path = tree.getPathForRow(row);
    return (DefaultMutableTreeNode)path.getLastPathComponent();
  }
}
