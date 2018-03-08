/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.ui.JBColor;
import com.intellij.ui.LoadingNode;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

abstract class InspectorColoredTreeCellRenderer extends MultiIconSimpleColoredComponent implements TreeCellRenderer {
  /**
   * Defines whether the tree has focus or not
   */
  private boolean myFocused;
  private boolean myFocusedCalculated;

  protected JTree myTree;

  private boolean myOpaque = true;

  @Override
  public final Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
    myTree = tree;

    clear();

    myFocusedCalculated = false;

    // We paint background if and only if tree path is selected and tree has focus.
    // If path is selected and tree is not focused then we just paint focused border.
    if (UIUtil.isFullRowSelectionLAF()) {
      setBackground(selected ? UIUtil.getTreeSelectionBackground() : null);
    }
    else if (WideSelectionTreeUI.isWideSelection(tree)) {
      setPaintFocusBorder(false);
      if (selected) {
        setBackground(hasFocus ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeUnfocusedSelectionBackground());
      }
    }
    else if (selected) {
      setPaintFocusBorder(true);
      if (isFocused()) {
        setBackground(UIUtil.getTreeSelectionBackground());
      }
      else {
        setBackground(null);
      }
    }
    else {
      setBackground(null);
    }

    if (value instanceof LoadingNode) {
      setForeground(JBColor.GRAY);
    }
    else {
      setForeground(tree.getForeground());
      setIcon(null);
    }

    super.setOpaque(myOpaque || selected && hasFocus || selected && isFocused()); // draw selection background even for non-opaque tree


    if (tree.getUI() instanceof InspectorTreeUI && UIUtil.isUnderAquaBasedLookAndFeel()) {
      setMyBorder(null);
      setIpad(JBUI.insets(0, 2));
    }

    customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);

    return this;
  }

  public JTree getTree() {
    return myTree;
  }

  protected final boolean isFocused() {
    if (!myFocusedCalculated) {
      myFocused = calcFocusedState();
      myFocusedCalculated = true;
    }
    return myFocused;
  }

  private boolean calcFocusedState() {
    return myTree.hasFocus();
  }

  @Override
  public void setOpaque(boolean isOpaque) {
    myOpaque = isOpaque;
    super.setOpaque(isOpaque);
  }

  protected InspectorTreeUI getUI() {
    return (InspectorTreeUI)myTree.getUI();
  }

  @Override
  public Font getFont() {
    Font font = super.getFont();

    // Cell renderers could have no parent and no explicit set font.
    // Take tree font in this case.
    if (font != null) return font;
    JTree tree = getTree();
    return tree != null ? tree.getFont() : null;
  }

  /**
   * This method is invoked only for customization of component.
   * All component attributes are cleared when this method is being invoked.
   */
  public abstract void customizeCellRenderer(@NotNull JTree tree,
                                             Object value,
                                             boolean selected,
                                             boolean expanded,
                                             boolean leaf,
                                             int row,
                                             boolean hasFocus);
}
