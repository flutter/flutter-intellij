/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.android.tools.idea.npw.assetstudio.wizard;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link FlowLayout} that supports wrapping of its components when contained in a container too small
 * to display all components in a single row.  Although {@link FlowLayout} supports wrapping components,
 * the default implementation does not return a preferred size reflecting the wrapping behavior. This results
 * in containers typically not growing appropriately when they contain a {@link FlowLayout} based panel.
 *
 * <p>The code below illustrates the issue: when the frame is resized, the buttons in the panel are wrapped,
 * but the {@link GridBagLayout} panel does not grow, resulting in buttons disappearing.
 *
 * <blockquote><pre>
 * public class Main {
 *  public static void main(String[] args) {
 *   JPanel panel = new JPanel();
 *   panel.add(new JButton("Button"));
 *   panel.add(new JButton("Button"));
 *   panel.add(new JButton("Button"));
 *   panel.add(new JButton("Button"));
 *   panel.add(new JButton("Button"));
 *   panel.add(new JButton("Button"));
 *   panel.add(new JButton("Button"));
 *
 *   JPanel rootPanel = new JPanel(new GridBagLayout());
 *   rootPanel.add(panel);
 *
 *   JFrame frame = new JFrame("Test");
 *   frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
 *   frame.setContentPane(rootPanel);
 *   frame.pack();
 *   frame.setVisible(true);
 *  }
 * }
 * </pre></blockquote>
 *
 * <p>Note: This is a modified copy of {@code com.intellij.vcs.log.ui.frame.WrappedFlowLayout} with
 * the following modification:
 * <ul>
 *  <li>Made constructors to be compatible with {@link FlowLayout}</li>
 *  <li>Fixed support of minimum size (instead of always preferred)</li>
 *  <li>Fixed so that wrapping works when {@link #getAlignOnBaseline()} is {@code true}</li>
 * </ul>
 */
public class WrappedFlowLayout extends FlowLayout {
  @SuppressWarnings("unused")
  public WrappedFlowLayout() {
  }

  public WrappedFlowLayout(@MagicConstant(intValues = {FlowLayout.LEFT, FlowLayout.CENTER, FlowLayout.RIGHT,
    FlowLayout.LEADING, FlowLayout.TRAILING}) int align) {
    super(align);
  }

  @SuppressWarnings("unused")
  public WrappedFlowLayout(@MagicConstant(intValues = {FlowLayout.LEFT, FlowLayout.CENTER, FlowLayout.RIGHT,
    FlowLayout.LEADING, FlowLayout.TRAILING}) int align, int hgap, int vgap) {
    super(align, hgap, vgap);
  }

  @NotNull
  @Override
  public Dimension preferredLayoutSize(@NotNull Container target) {
    return getWrappedSize(target, Component::getPreferredSize);
  }

  @NotNull
  @Override
  public Dimension minimumLayoutSize(@NotNull Container target) {
    return getWrappedSize(target, Component::getMinimumSize);
  }

  @NotNull
  private Dimension getWrappedSize(@NotNull Container target, @NotNull Function<Component, Dimension> sizeGetter) {
    int maxWidth = getParentMaxWidth(target);
    Insets insets = target.getInsets();
    int height = insets.top + insets.bottom;
    int width = insets.left + insets.right;

    int rowHeight = 0;
    int rowWidth = insets.left + insets.right;

    boolean isVisible = false;
    boolean start = true;

    synchronized (target.getTreeLock()) {
      for (int i = 0; i < target.getComponentCount(); i++) {
        Component component = target.getComponent(i);
        if (component.isVisible()) {
          isVisible = true;
          Dimension size = sizeGetter.apply(component);

          if (rowWidth + getHgap() + size.width > maxWidth && !start) {
            height += getVgap() + rowHeight;
            width = Math.max(width, rowWidth);

            rowWidth = insets.left + insets.right;
            rowHeight = 0;
          }

          rowWidth += getHgap() + size.width;
          rowHeight = Math.max(rowHeight, size.height);

          start = false;
        }
      }
      height += getVgap() + rowHeight;
      width = Math.max(width, rowWidth);

      if (!isVisible) {
        return super.preferredLayoutSize(target);
      }
      else {
        return new Dimension(width, height);
      }
    }
  }

  private static int getParentMaxWidth(@NotNull Container target) {
    Container parent = SwingUtilities.getUnwrappedParent(target);
    if (parent == null) {
      return 0;
    }

    return parent.getWidth() - (parent.getInsets().left + parent.getInsets().right);
  }
}