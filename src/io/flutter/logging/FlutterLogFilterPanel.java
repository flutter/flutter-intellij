/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


public class FlutterLogFilterPanel {
  private static final Logger LOG = Logger.getInstance(FlutterLogFilterPanel.class);

  static class FilterParam {
    @Nullable
    private final String expression;
    private final boolean isMatchCase;
    private final boolean isRegex;
    @NotNull
    private final FlutterLog.Level logLevel;

    public FilterParam(@Nullable String expression, boolean isMatchCase, boolean isRegex, @NotNull FlutterLog.Level logLevel) {
      this.expression = expression;
      this.isMatchCase = isMatchCase;
      this.isRegex = isRegex;
      this.logLevel = logLevel;
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

    @NotNull
    public FlutterLog.Level getLogLevel() {
      return logLevel;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final FilterParam param = (FilterParam)o;
      return isMatchCase == param.isMatchCase &&
             isRegex == param.isRegex &&
             Objects.equals(expression, param.expression) &&
             logLevel == param.logLevel;
    }

    @Override
    public int hashCode() {
      return Objects.hash(expression, isMatchCase, isRegex, logLevel);
    }

    @Override
    public String toString() {
      return "FilterParam{" +
             "expression='" + expression + '\'' +
             ", isMatchCase=" + isMatchCase +
             ", isRegex=" + isRegex +
             ", logLevel=" + logLevel +
             '}';
    }
  }

  public interface OnFilterListener {
    void onFilter(@NotNull FilterParam param);
  }

  private static final OnFilterListener DEFAULT_ON_FILTER_LISTENER = param -> LOG.info("Requests filter: " + param);

  @NotNull
  private final OnFilterListener onFilterListener;
  private JPanel root;
  private JCheckBox matchCaseCheckBox;
  private JCheckBox regexCheckBox;
  private SearchTextField textExpression;

  public FlutterLogFilterPanel(@NotNull OnFilterListener onFilterListener) {
    this.onFilterListener = onFilterListener;
    matchCaseCheckBox.addItemListener(e -> onFilterListener.onFilter(getCurrentFilterParam()));
    regexCheckBox.addItemListener(e -> onFilterListener.onFilter(getCurrentFilterParam()));
    final List<FlutterLog.Level> logLevels = Arrays.stream(FlutterLog.Level.values())
      .collect(Collectors.toList());
  }

  @NotNull
  public FilterParam getCurrentFilterParam() {
    return new FilterParam(textExpression.getText(), matchCaseCheckBox.isSelected(), regexCheckBox.isSelected(), FlutterLog.Level.NONE);
  }

  @NotNull
  public JPanel getRoot() {
    return root;
  }

  public void initFromPreferences(@NotNull FlutterLogPreferences flutterLogPreferences) {
    regexCheckBox.setSelected(flutterLogPreferences.isToolWindowRegex());
    matchCaseCheckBox.setSelected(flutterLogPreferences.isToolWindowMatchCase());
  }

  @Nullable
  public String getExpression() {
    return textExpression.getText();
  }

  public void setTextFieldFg(boolean inactive) {
    textExpression.getTextEditor().setForeground(inactive ? UIUtil.getInactiveTextColor() : UIUtil.getActiveTextColor());
  }

  public boolean isRegex() {
    return regexCheckBox.isSelected();
  }

  public boolean isMatchCase() {
    return regexCheckBox.isSelected();
  }

  private void createUIComponents() {
    textExpression = createSearchTextField();
  }

  @NotNull
  private SearchTextField createSearchTextField() {
    final LogFilterTextField logFilterTextField = new LogFilterTextField();
    logFilterTextField.setOnFilterListener(() -> onFilterListener.onFilter(getCurrentFilterParam()));
    return logFilterTextField;
  }
}
