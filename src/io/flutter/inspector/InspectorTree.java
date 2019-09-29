/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import io.flutter.view.InspectorTreeUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

public class InspectorTree extends Tree implements DataProvider, Disposable {
  public final boolean detailsSubtree;

  private DefaultMutableTreeNode highlightedRoot;
  private TreeScrollAnimator scrollAnimator;

  public static final DataKey<Tree> INSPECTOR_KEY = DataKey.create("Flutter.InspectorKey");

  static final JBColor VERY_LIGHT_GREY = new JBColor(Gray._220, Gray._65);

  public DefaultMutableTreeNode getHighlightedRoot() {
    return highlightedRoot;
  }

  public void setHighlightedRoot(DefaultMutableTreeNode value) {
    if (highlightedRoot == value) {
      return;
    }
    highlightedRoot = value;
    // TODO(jacobr): we only really need to repaint the selected subtree.
    repaint();
  }

  @Override
  protected boolean isCustomUI() {
    return true;
  }

  @Override
  protected boolean isWideSelection() {
    return false;
  }

  @Override
  public void scrollRectToVisible(Rectangle rect) {
    // By overriding the default scrollRectToVisible behavior we ensure that
    // all attempts to scroll the tree by the rest of the IntelliJ UI
    // will go through the scrollAnimator which makes sure all scrolls are
    // smooth so the user stays better oriented in the tree.
    // This also allows us to implement custom logic to ensure that the tree view is
    // automatically scrolled along the X axis to keep the relevant content in
    // view instead of being scrolled 100s or 10000s of pixels off to the side.
    scrollAnimator.animateTo(rect);
  }

  /**
   * Escape hatch for cases where need to immediately scroll a rectangle to
   * visible instead of performing an animated transition.
   */
  public void immediateScrollRectToVisible(Rectangle rect) {
    super.scrollRectToVisible(rect);
  }

  @Override
  public void setUI(TreeUI ui) {
    final InspectorTreeUI inspectorTreeUI = ui instanceof InspectorTreeUI ? (InspectorTreeUI)ui : new InspectorTreeUI();
    super.setUI(inspectorTreeUI);
    inspectorTreeUI.setRightChildIndent(JBUI.scale(4));
  }

  public InspectorTree(final DefaultMutableTreeNode treemodel,
                       String treeName,
                       boolean detailsSubtree,
                       String parentTreeName,
                       boolean rootVisible,
                       boolean legacyMode) {
    super(treemodel);
    setUI(new InspectorTreeUI());
    final BasicTreeUI ui = (BasicTreeUI)getUI();
    this.detailsSubtree = detailsSubtree;

    setRootVisible(rootVisible);
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    registerShortcuts();
    if (detailsSubtree) {
      // TODO(devoncarew): This empty text is not showing up for the details area, even when there are no detail nodes.
      getEmptyText().setText(treeName + " subtree of the selected " + parentTreeName);
    }
    else {
      getEmptyText().setText(treeName + " tree for the running app");
    }
  }

  void registerShortcuts() {
    DebuggerUIUtil.registerActionOnComponent(InspectorActions.JUMP_TO_TYPE_SOURCE, this, this);
    DebuggerUIUtil.registerActionOnComponent(InspectorActions.JUMP_TO_SOURCE, this, this);
    DebuggerUIUtil.registerActionOnComponent(InspectorActions.INSPECT_WITH_DEBUGGER, this, this);
  }

  @Override
  public void dispose() {
    // TODO(jacobr): do we have anything to dispose?
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (InspectorTree.INSPECTOR_KEY.is(dataId)) {
      return this;
    }
    if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
      final XValueNodeImpl[] selectedNodes = getSelectedNodes(XValueNodeImpl.class, null);
      if (selectedNodes.length == 1 && selectedNodes[0].getFullValueEvaluator() == null) {
        return DebuggerUIUtil.getNodeRawValue(selectedNodes[0]);
      }
    }
    return null;
  }

  public void setScrollAnimator(TreeScrollAnimator scrollAnimator) {
    this.scrollAnimator = scrollAnimator;
  }
}
