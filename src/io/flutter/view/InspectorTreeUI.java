/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import icons.FlutterIcons;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.DiagnosticsTreeStyle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import static io.flutter.inspector.TreeUtils.maybeGetDiagnostic;

public class InspectorTreeUI extends BasicTreeUI {
  public static final String TREE_TABLE_TREE_KEY = "TreeTableTree";

  @NonNls public static final String SOURCE_LIST_CLIENT_PROPERTY = "mac.ui.source.list";
  @NonNls public static final String STRIPED_CLIENT_PROPERTY = "mac.ui.striped";

  private static final Border LIST_BACKGROUND_PAINTER = UIManager.getBorder("List.sourceListBackgroundPainter");
  private static final Border LIST_SELECTION_BACKGROUND_PAINTER = UIManager.getBorder("List.sourceListSelectionBackgroundPainter");
  private static final Border LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER =
    UIManager.getBorder("List.sourceListFocusedSelectionBackgroundPainter");

  @NotNull private final Condition<Integer> myWideSelectionCondition;
  private boolean myWideSelection;
  private boolean myOldRepaintAllRowValue;
  private boolean mySkinny = false;

  boolean leftToRight = true; // TODO(jacobr): actually support RTL mode.

  public InspectorTreeUI() {
    this(false, Conditions.<Integer>alwaysFalse());
  }

  /**
   * Creates new {@code InspectorTreeUI} object.
   *
   * @param wideSelection          flag that determines if wide selection should be used
   * @param wideSelectionCondition strategy that determine if wide selection should be used for a target row (it's zero-based index
   *                               is given to the condition as an argument)
   */
  public InspectorTreeUI(final boolean wideSelection, @NotNull Condition<Integer> wideSelectionCondition) {
    myWideSelection = wideSelection;
    myWideSelectionCondition = wideSelectionCondition;
  }

  @Override
  public int getRightChildIndent() {
    return isCustomIndent() ? getCustomIndent() : super.getRightChildIndent();
  }

  public boolean isCustomIndent() {
    return getCustomIndent() > 0;
  }

  protected int getCustomIndent() {
    return JBUI.scale(Registry.intValue("ide.ui.tree.indent"));
  }

  @Override
  protected MouseListener createMouseListener() {
    return new MouseEventAdapter<MouseListener>(super.createMouseListener()) {
      @Override
      public void mouseDragged(MouseEvent event) {
        JTree tree = (JTree)event.getSource();
        Object property = tree.getClientProperty("DnD Source"); // DnDManagerImpl.SOURCE_KEY
        if (property == null) {
          super.mouseDragged(event); // use Swing-based DnD only if custom DnD is not set
        }
      }

      @NotNull
      @Override
      protected MouseEvent convert(@NotNull MouseEvent event) {
        if (!event.isConsumed() && SwingUtilities.isLeftMouseButton(event)) {
          int x = event.getX();
          int y = event.getY();
          JTree tree = (JTree)event.getSource();
          if (tree.isEnabled()) {
            TreePath path = getClosestPathForLocation(tree, x, y);
            if (path != null && !isLocationInExpandControl(path, x, y)) {
              Rectangle bounds = getPathBounds(tree, path);
              if (bounds != null && bounds.y <= y && y <= (bounds.y + bounds.height)) {
                x = Math.max(bounds.x, Math.min(x, bounds.x + bounds.width - 1));
                if (x != event.getX()) {
                  event = convert(event, tree, x, y);
                }
              }
            }
          }
        }
        return event;
      }
    };
  }

  @Override
  protected void completeUIInstall() {
    super.completeUIInstall();

    myOldRepaintAllRowValue = UIManager.getBoolean("Tree.repaintWholeRow");
    UIManager.put("Tree.repaintWholeRow", true);

    tree.setShowsRootHandles(true);
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);

    UIManager.put("Tree.repaintWholeRow", myOldRepaintAllRowValue);
  }

  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();

    if (Boolean.TRUE.equals(tree.getClientProperty("MacTreeUi.actionsInstalled"))) return;

    tree.putClientProperty("MacTreeUi.actionsInstalled", Boolean.TRUE);

    final InputMap inputMap = tree.getInputMap(JComponent.WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke("pressed LEFT"), "collapse_or_move_up");
    inputMap.put(KeyStroke.getKeyStroke("pressed RIGHT"), "expand");

    final ActionMap actionMap = tree.getActionMap();

    final Action expandAction = actionMap.get("expand");
    if (expandAction != null) {
      actionMap.put("expand", new TreeUIAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final Object source = e.getSource();
          if (source instanceof JTree) {
            final JTree tree = (JTree)source;
            final int selectionRow = tree.getLeadSelectionRow();
            if (selectionRow != -1) {
              final TreePath selectionPath = tree.getPathForRow(selectionRow);
              if (selectionPath != null) {
                final boolean leaf = tree.getModel().isLeaf(selectionPath.getLastPathComponent());
                int toSelect = -1;
                int toScroll = -1;
                if (leaf || tree.isExpanded(selectionRow)) {
                  if (selectionRow + 1 < tree.getRowCount()) {
                    toSelect = selectionRow + 1;
                    toScroll = toSelect;
                  }
                }
                //todo[kb]: make cycle scrolling

                if (toSelect != -1) {
                  tree.setSelectionInterval(toSelect, toSelect);
                }

                if (toScroll != -1) {
                  tree.scrollRowToVisible(toScroll);
                }

                if (toSelect != -1 || toScroll != -1) return;
              }
            }
          }


          expandAction.actionPerformed(e);
        }
      });
    }

    actionMap.put("collapse_or_move_up", new TreeUIAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final Object source = e.getSource();
        if (source instanceof JTree) {
          JTree tree = (JTree)source;
          int selectionRow = tree.getLeadSelectionRow();
          if (selectionRow == -1) return;

          TreePath selectionPath = tree.getPathForRow(selectionRow);
          if (selectionPath == null) return;

          if (tree.getModel().isLeaf(selectionPath.getLastPathComponent()) || tree.isCollapsed(selectionRow)) {
            final TreePath parentPath = tree.getPathForRow(selectionRow).getParentPath();
            if (parentPath != null) {
              if (parentPath.getParentPath() != null || tree.isRootVisible()) {
                final int parentRow = tree.getRowForPath(parentPath);
                tree.scrollRowToVisible(parentRow);
                tree.setSelectionInterval(parentRow, parentRow);
              }
            }
          }
          else {
            tree.collapseRow(selectionRow);
          }
        }
      }
    });
  }

  private abstract static class TreeUIAction extends AbstractAction implements UIResource {
  }

  @Override
  protected int getRowX(int row, int depth) {
    if (isCustomIndent()) {
      final int off = tree.isRootVisible() ? 8 : 0;
      return 8 * depth + 8 + off;
    }
    else {
      return super.getRowX(row, depth);
    }
  }

  @Override
  protected void paintHorizontalPartOfLeg(final Graphics g,
                                          final Rectangle clipBounds,
                                          final Insets insets,
                                          final Rectangle bounds,
                                          final TreePath path,
                                          final int row,
                                          final boolean isExpanded,
                                          final boolean hasBeenExpanded,
                                          final boolean isLeaf) {
    if (path.getPathCount() < 2) {
      // We could draw lines for nodes with a single child but we omit them
      // to more of an emphasis of lines for nodes with multiple children.
      return;
    }
    if (path.getPathCount() >= 2) {
      final Object treeNode = path.getPathComponent(path.getPathCount() - 2);
      if (treeNode instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)treeNode;
        if (node.getChildCount() < 2) {
          return;
        }
      }
    }
    boolean dashed = false;
    final DiagnosticsNode diagnosticsNode = maybeGetDiagnostic((DefaultMutableTreeNode)path.getLastPathComponent());
    if (diagnosticsNode != null) {
      if (diagnosticsNode.isProperty()) {
        // Intentionally avoid ever drawing lines for properties as we need to
        // distinguish them from other nodes. See the DiagnosticsNode class in
        // Flutter which applies the same concept rendering properties inline
        // as part of ascii art tree display.
        return;
      }
      // This also consistent with the ascii art tree display where offstage
      // nodes are rendered using dashed lines.
      if (diagnosticsNode.getStyle() == DiagnosticsTreeStyle.offstage) {
        dashed = true;
      }
    }

    final int depth = path.getPathCount() - 1;
    if ((depth == 0 || (depth == 1 && !isRootVisible())) &&
        !getShowsRootHandles()) {
      return;
    }

    final int lineY = bounds.y + bounds.height / 2;
    final int leafChildLineInset = 4;

    if (leftToRight) {
      int leftX = bounds.x - getRightChildIndent();
      int nodeX = bounds.x - getHorizontalLegBuffer();

      leftX = getRowX(row, depth - 1) - getRightChildIndent() + insets.left;
      nodeX = isLeaf ? getRowX(row, depth) - leafChildLineInset :
              getRowX(row, depth - 1);
      nodeX += insets.left;
      if (clipBounds.intersects(leftX, lineY, nodeX - leftX, 1)) {
        g.setColor(JBColor.GRAY);
        if (dashed) {
          drawDashedHorizontalLine(g, lineY, leftX, nodeX - 1);
        }
        else {
          paintHorizontalLine(g, tree, lineY, leftX, nodeX - 1);
        }
      }
    }
    // TODO(jacobr): implement RTL case.
  }

  @Override
  protected boolean isToggleSelectionEvent(MouseEvent e) {
    return UIUtil.isToggleListSelectionEvent(e);
  }

  @Override
  protected void paintVerticalPartOfLeg(final Graphics g, final Rectangle clipBounds, final Insets insets, final TreePath path) {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    if (node.getChildCount() < 2) {
      // We could draw lines for nodes with a single child but we omit them
      // to more of an emphasis of lines for nodes with multiple children.
      return;
    }
    final DiagnosticsNode diagnostic = maybeGetDiagnostic(node);
    if (diagnostic != null && !diagnostic.hasChildren()) {
      // This avoids drawing lines for nodes with only property children.
      return;
    }

    final int depth = path.getPathCount() - 1;
    if (depth == 0 && !getShowsRootHandles() && !isRootVisible()) {
      return;
    }

    int lineX = getRowX(-1, depth);
    if (leftToRight) {
      lineX = lineX - getRightChildIndent() + insets.left;
    }
    else {
      lineX = tree.getWidth() - lineX - insets.right +
              getRightChildIndent() - 1;
    }
    final int clipLeft = clipBounds.x;
    final int clipRight = clipBounds.x + (clipBounds.width - 1);

    if (lineX >= clipLeft && lineX <= clipRight) {
      final int clipTop = clipBounds.y;
      final int clipBottom = clipBounds.y + clipBounds.height;
      Rectangle parentBounds = getPathBounds(tree, path);
      boolean previousDashed = false;

      int top;
      if (parentBounds == null) {
        top = Math.max(insets.top + getVerticalLegBuffer(),
                       clipTop);
      }
      else {
        top = Math.max(parentBounds.y + parentBounds.height +
                       getVerticalLegBuffer(), clipTop);
      }

      if (depth == 0 && !isRootVisible()) {
        final TreeModel model = getModel();

        if (model != null) {
          final Object root = model.getRoot();

          if (model.getChildCount(root) > 0) {
            parentBounds = getPathBounds(tree, path.
              pathByAddingChild(model.getChild(root, 0)));
            if (parentBounds != null) {
              top = Math.max(insets.top + getVerticalLegBuffer(),
                             parentBounds.y +
                             parentBounds.height / 2);
            }
          }
        }
      }

      for (int i = 0; i < node.getChildCount(); ++i) {
        final DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
        final DiagnosticsNode childDiagnostic = maybeGetDiagnostic(child);
        boolean dashed = false;
        if (childDiagnostic != null) {
          dashed = childDiagnostic.getStyle() == DiagnosticsTreeStyle.offstage;
        }

        final Rectangle childBounds = getPathBounds(tree, path.pathByAddingChild(child));
        if (childBounds == null)
        // This shouldn't happen, but if the model is modified
        // in another thread it is possible for this to happen.
        // Swing isn't multithreaded, but I'll add this check in
        // anyway.
        {
          continue;
        }

        final int bottom = Math.min(childBounds.y +
                                    (childBounds.height / 2), clipBottom);

        if (top <= bottom && bottom >= clipTop && top <= clipBottom) {
          g.setColor(JBColor.GRAY);
          paintVerticalLine(g, tree, lineX, top, bottom, dashed);
        }
        top = bottom;
        previousDashed = dashed;
      }
    }
  }

  protected void paintVerticalLine(Graphics g, JComponent c, int x, int top, int bottom, boolean dashed) {
    if (dashed) {
      drawDashedVerticalLine(g, x, top, bottom);
    }
    else {
      g.drawLine(x, top, x, bottom);
    }
  }


  public @NotNull
  TreePath getLastExpandedDescendant(TreePath path) {
    while (tree.isExpanded(path)) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.isLeaf()) {
        break;
      }
      path = path.pathByAddingChild(node.getLastChild());
    }
    return path;
  }

  private Rectangle getSubtreeBounds(DefaultMutableTreeNode node, Rectangle clipBounds) {
    if (node == null) {
      return null;
    }
    final TreePath path = new TreePath(node.getPath());
    final int depth = path.getPathCount() - 1;
    final Rectangle rootBounds = tree.getPathBounds(path);
    if (rootBounds == null) {
      return null;
    }
    // We use getRowX instead of the value from rootBounds as we want to include
    // the down arrows
    final int minX = getRowX(-1, depth - 1);
    final int minY = rootBounds.y;

    Rectangle bounds;
    final Rectangle descendantBounds = tree.getPathBounds(getLastExpandedDescendant(path));
    if (descendantBounds != null) {
      final int maxY = (int)descendantBounds.getMaxY();
      final int maxX = (int)clipBounds.getMaxX();
      bounds = new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }
    else {
      // This case shouldn't really happen unless we have a bug but using just
      // the root node bounds is a safe fallback.
      bounds = rootBounds;
    }
    return bounds.intersection(clipBounds);
  }

  @Override
  protected Color getHashColor() {
    //if (invertLineColor && !ComparatorUtil.equalsNullable(UIUtil.getTreeSelectionForeground(), UIUtil.getTreeForeground())) {
    //  final Color c = UIUtil.getTreeSelectionForeground();
    //  if (c != null) {
    //    return c.darker();
    //  }
    //}
    return super.getHashColor();
  }

  public boolean isWideSelection() {
    return myWideSelection;
  }

  @Override
  protected void paintRow(final Graphics g,
                          final Rectangle clipBounds,
                          final Insets insets,
                          final Rectangle bounds,
                          final TreePath path,
                          final int row,
                          final boolean isExpanded,
                          final boolean hasBeenExpanded,
                          final boolean isLeaf) {
    final int containerWidth = tree.getParent() instanceof JViewport ? tree.getParent().getWidth() : tree.getWidth();
    final int xOffset = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewPosition().x : 0;

    if (path != null && myWideSelection) {
      boolean selected = tree.isPathSelected(path);
      Graphics2D rowGraphics = (Graphics2D)g.create();
      rowGraphics.setClip(clipBounds);

      final Object sourceList = tree.getClientProperty(SOURCE_LIST_CLIENT_PROPERTY);
      Color background = tree.getBackground();

      if ((row % 2) == 0 && Boolean.TRUE.equals(tree.getClientProperty(STRIPED_CLIENT_PROPERTY))) {
        background = UIUtil.getDecoratedRowColor();
      }

      if (sourceList != null && (Boolean)sourceList) {
        if (selected) {
          if (tree.hasFocus()) {
            LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER.paintBorder(tree, rowGraphics, xOffset, bounds.y, containerWidth, bounds.height);
          }
          else {
            LIST_SELECTION_BACKGROUND_PAINTER.paintBorder(tree, rowGraphics, xOffset, bounds.y, containerWidth, bounds.height);
          }
        }
        else if (myWideSelectionCondition.value(row)) {
          rowGraphics.setColor(background);
          rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height);
        }
      }
      else {
        if (selected && (UIUtil.isUnderAquaBasedLookAndFeel() || UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())) {
          Color bg = getSelectionBackground(tree, true);

          if (myWideSelectionCondition.value(row)) {
            rowGraphics.setColor(bg);
            rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height);
          }
        }
      }

      if (shouldPaintExpandControl(path, row, isExpanded, hasBeenExpanded, isLeaf)) {
        paintExpandControl(rowGraphics, bounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
      }

      super.paintRow(rowGraphics, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
      rowGraphics.dispose();
    }
    else {
      super.paintRow(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
    }
  }

  @Override
  protected void paintExpandControl(Graphics g,
                                    Rectangle clipBounds,
                                    Insets insets,
                                    Rectangle bounds,
                                    TreePath path,
                                    int row,
                                    boolean isExpanded,
                                    boolean hasBeenExpanded,
                                    boolean isLeaf) {
    final boolean isPathSelected = tree.getSelectionModel().isPathSelected(path);
    boolean isPropertyNode = false;
    if (!isLeaf(row)) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        final DiagnosticsNode diagnostic = maybeGetDiagnostic((DefaultMutableTreeNode)lastPathComponent);
        if (diagnostic != null) {
          isPropertyNode = diagnostic.isProperty();
        }
      }
      if (isPropertyNode) {
        setExpandedIcon(FlutterIcons.CollapseProperty);
        setCollapsedIcon(FlutterIcons.ExpandProperty);
      }
      else {
        setExpandedIcon(UIUtil.getTreeNodeIcon(true, false, false));
        setCollapsedIcon(UIUtil.getTreeNodeIcon(false, false, false));
      }
    }

    super.paintExpandControl(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
  }

  @Override
  protected CellRendererPane createCellRendererPane() {
    return new CellRendererPane() {
      @Override
      public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
        if (c instanceof JComponent && myWideSelection) {
          if (c.isOpaque()) {
            ((JComponent)c).setOpaque(false);
          }
        }

        super.paintComponent(g, c, p, x, y, w, h, shouldValidate);
      }
    };
  }

  @Nullable
  private static Color getSelectionBackground(@NotNull JTree tree, boolean checkProperty) {
    Object property = tree.getClientProperty(TREE_TABLE_TREE_KEY);
    if (property instanceof JTable) {
      return ((JTable)property).getSelectionBackground();
    }
    boolean selection = tree.hasFocus();
    if (!selection && checkProperty) {
      selection = Boolean.TRUE.equals(property);
    }
    return UIUtil.getTreeSelectionBackground(selection);
  }

  public void invalidateNodeSizes() {
    treeState.invalidateSizes();
  }
}
