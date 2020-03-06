/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npwOld.assetstudio.wizard;

import com.android.tools.adtui.util.GraphicsUtil;
import com.intellij.ui.Gray;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Objects;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link JPanel} with a checkered background painted using
 * {@link GraphicsUtil#paintCheckeredBackground(Graphics, Color, Color, Shape, int)}
 */
public class CheckeredBackgroundPanel extends JPanel {
  @NotNull public static final Gray DEFAULT_ODD_CELL_COLOR = Gray.xE0;
  @NotNull public static final Gray DEFAULT_EVEN_CELL_COLOR = Gray.xFF;
  private static final int DEFAULT_CELL_SIZE = 10;

  @NotNull private final Rectangle myRectangle = new Rectangle();
  @NotNull private Color myOddCellColor;
  @NotNull private Color myEvenCellColor;
  private int myCellSize;

  @SuppressWarnings("unused")
  public CheckeredBackgroundPanel() {
    this(DEFAULT_CELL_SIZE, DEFAULT_ODD_CELL_COLOR, DEFAULT_EVEN_CELL_COLOR);
  }

  @SuppressWarnings("unused")
  public CheckeredBackgroundPanel(int cellSize, @Nullable Color oddCellColor, @Nullable Color evenCellColor) {
    if (cellSize < 1) {
      throw new IllegalArgumentException("Invalid cell size");
    }
    myCellSize = cellSize;
    myOddCellColor = oddCellColor == null ? DEFAULT_ODD_CELL_COLOR : oddCellColor;
    myEvenCellColor = evenCellColor == null ? DEFAULT_EVEN_CELL_COLOR : evenCellColor;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myRectangle.setBounds(0, 0, getWidth(), getHeight());
    GraphicsUtil.paintCheckeredBackground(g,
                                          myOddCellColor,
                                          myEvenCellColor,
                                          myRectangle,
                                          myCellSize);
  }

  @SuppressWarnings("unused")
  public int getCellSize() {
    return myCellSize;
  }

  @SuppressWarnings("unused")
  public void setCellSize(int cellSize) {
    if (cellSize == myCellSize && cellSize >= 1) {
      return;
    }

    myCellSize = cellSize;
    repaint();
  }

  @NotNull
  @SuppressWarnings("unused")
  public Color getOddCellColor() {
    return myOddCellColor;
  }

  @SuppressWarnings("unused")
  public void setOddCellColor(@Nullable Color oddCellColor) {
    if (Objects.equals(myOddCellColor, oddCellColor)) {
      return;
    }
    myOddCellColor = oddCellColor == null ? DEFAULT_ODD_CELL_COLOR : oddCellColor;
    repaint();
  }

  @NotNull
  @SuppressWarnings("unused")
  public Color getEvenCellColor() {
    return myEvenCellColor;
  }

  @SuppressWarnings("unused")
  public void setEvenCellColor(@Nullable Color evenCellColor) {
    if (Objects.equals(myEvenCellColor, evenCellColor)) {
      return;
    }
    myEvenCellColor = evenCellColor == null ? DEFAULT_EVEN_CELL_COLOR : evenCellColor;
    repaint();
  }
}
