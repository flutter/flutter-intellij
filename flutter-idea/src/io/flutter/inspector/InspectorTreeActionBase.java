/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ui.treeStructure.Tree;
import io.flutter.view.InspectorPanel;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public abstract class InspectorTreeActionBase extends AnAction {
  @Override
  public void actionPerformed(final AnActionEvent e) {
    final DefaultMutableTreeNode node = getSelectedNode(e.getDataContext());
    if (node != null) {
      final Object diagnostic = node.getUserObject();
      if (diagnostic instanceof DiagnosticsNode) {
        perform(node, (DiagnosticsNode)diagnostic, e);
      }
    }
  }

  protected abstract void perform(final DefaultMutableTreeNode node, DiagnosticsNode diagnosticsNode, final AnActionEvent e);

  @Override
  public void update(final AnActionEvent e) {
    final DefaultMutableTreeNode node = getSelectedNode(e.getDataContext());
    e.getPresentation().setEnabled(node != null && isEnabled(node, e));
  }

  protected boolean isEnabled(final DefaultMutableTreeNode node, AnActionEvent e) {
    return node.getUserObject() instanceof DiagnosticsNode && isSupported((DiagnosticsNode)node.getUserObject());
  }

  protected boolean isSupported(DiagnosticsNode diagnosticsNode) {
    return true;
  }

  public static DefaultMutableTreeNode getSelectedNode(final DataContext dataContext) {
    final Tree tree = InspectorPanel.getTree(dataContext);
    if (tree == null) return null;

    final TreePath path = tree.getSelectionPath();
    if (path == null) return null;

    return (DefaultMutableTreeNode)path.getLastPathComponent();
  }

  public static DiagnosticsNode getSelectedValue(DataContext dataContext) {
    final DefaultMutableTreeNode node = getSelectedNode(dataContext);
    if (node == null) {
      return null;
    }
    final Object userObject = node.getUserObject();
    return userObject instanceof DiagnosticsNode ? (DiagnosticsNode)userObject : null;
  }
}
