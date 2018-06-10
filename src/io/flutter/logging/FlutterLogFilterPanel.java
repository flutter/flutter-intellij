/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class FlutterLogFilterPanel {
  private JPanel root;
  private JCheckBox chbMatchCase;
  private JCheckBox chbRegex;
  private JPanel pnlExpression;
  private final LogFilterTextField txtExpression;

  public FlutterLogFilterPanel(
    @NotNull OnFilterListener onFilterListener
  ) {
    chbMatchCase.addItemListener(e -> onFilterListener.onFilter(getCurrentFilterParam()));
    chbRegex.addItemListener(e -> onFilterListener.onFilter(getCurrentFilterParam()));
    txtExpression = new LogFilterTextField();
    txtExpression.setOnFilterListener(() -> onFilterListener.onFilter(getCurrentFilterParam()));
    pnlExpression.add(txtExpression, new GridConstraints());
  }

  @NotNull
  public FilterParam getCurrentFilterParam() {
    return new FilterParam(txtExpression.getText(), chbMatchCase.isSelected(), chbRegex.isSelected());
  }

  @NotNull
  public JPanel getRoot() {
    return root;
  }

  @Nullable
  public String getExpression() {
    return txtExpression.getText();
  }

  public void setTextFieldFg(boolean inactive) {
    txtExpression.getTextEditor().setForeground(inactive ? UIUtil.getInactiveTextColor() : UIUtil.getActiveTextColor());
  }

  public boolean isRegex() {
    return chbRegex.isSelected();
  }

  public boolean isMatchCase() {
    return chbRegex.isSelected();
  }

  public interface OnFilterListener {
    void onFilter(@NotNull FilterParam param);
  }

  static class FilterParam {
    @Nullable
    private final String expression;
    private final boolean isMatchCase;
    private final boolean isRegex;

    public FilterParam(@Nullable String expression, boolean isMatchCase, boolean isRegex) {
      this.expression = expression;
      this.isMatchCase = isMatchCase;
      this.isRegex = isRegex;
    }

    @Nullable
    public String getExpression() {
      return expression;
    }

    public boolean isMatchCase() {
      return isMatchCase;
    }

    public boolean isRegex() {
      return isRegex;
    }
  }
}
