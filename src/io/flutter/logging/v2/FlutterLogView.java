/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.v2;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.flutter.logging.FlutterLog;
import io.flutter.logging.FlutterLogFilterPanel;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class FlutterLogView extends JPanel implements ConsoleView, DataProvider {
  @NotNull
  private final FlutterApp app;
  @NotNull
  private final FlutterLog flutterLog;
  @NotNull
  private final SimpleToolWindowPanel toolWindowPanel;
  @NotNull
  private final FlutterLogTree logTree;
  @NotNull
  private final FlutterLogFilterPanel filterPanel = new FlutterLogFilterPanel(param -> doFilter());

  public FlutterLogView(@NotNull FlutterApp app) {
    this.app = app;
    flutterLog = app.getFlutterLog();

    logTree = createFlutterLogTree();
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(
      logTree,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    );
    toolWindowPanel = createFlutterToolWindowPanel(scrollPane);

    final Content content = ContentFactory.SERVICE.getInstance().createContent(null, null, false);
    content.setCloseable(false);
    content.setComponent(toolWindowPanel);
  }

  @NotNull
  private SimpleToolWindowPanel createFlutterToolWindowPanel(@NotNull JComponent content) {
    final SimpleToolWindowPanel window = new SimpleToolWindowPanel(true, true);
    window.setToolbar(createToolbar());
    window.setContent(content);
    return window;
  }

  @NotNull
  private FlutterLogTree createFlutterLogTree() {
    final FlutterLogTree tree = new FlutterLogTree(flutterLog);
    tree.setTableHeader(null);
    tree.setExpandableItemsEnabled(true);
    return tree;
  }

  @NotNull
  private JPanel createToolbar() {
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();

    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("FlutterLogViewToolbar", toolbarGroup, true);
    actionToolbar.setMiniMode(false);
    final JPanel toolbar = new JPanel();
    toolbar.setLayout(new BorderLayout());
    toolbar.add(filterPanel.getRoot(), BorderLayout.WEST);
    toolbar.add(actionToolbar.getComponent(), BorderLayout.EAST);
    return toolbar;
  }

  private void doFilter() {

  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    flutterLog.addConsoleEntry(text, contentType);
  }

  @Override
  public void clear() {

  }

  @Override
  public void scrollTo(int offset) {

  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    flutterLog.listenToProcess(processHandler, this);
  }

  @Override
  public void setOutputPaused(boolean value) {

  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {

  }

  @Override
  public void setHelpId(@NotNull String helpId) {

  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {

  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {

  }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return new AnAction[0];
  }

  @Override
  public void allowHeavyFilters() {

  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    return null;
  }

  @Override
  public JComponent getComponent() {
    return toolWindowPanel;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return logTree;
  }

  @Override
  public void dispose() {

  }
}
