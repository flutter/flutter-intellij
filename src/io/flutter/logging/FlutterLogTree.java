/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.ColumnInfo;
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.text.SimpleDateFormat;

public class FlutterLogTree extends TreeTable {

  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
  private static final DefaultEventCellRenderer DEFAULT_EVENT_CELL_RENDERER = new DefaultEventCellRenderer();

  static class LogTreeModel extends ListTreeTableModelOnColumns {
    @NotNull
    private final Runnable updateRunnable;
    @NotNull
    private final FlutterLog log;
    @NotNull
    private final Alarm updateAlarm;
    boolean autoScrollToEnd;
    private JScrollPane scrollPane;
    private TreeTable treeTable;

    public LogTreeModel(@NotNull FlutterApp app, @NotNull Disposable parent) {
      super(new LogRootTreeNode(), new ColumnInfo[]{
        new TimeColumnInfo(),
        new CategoryColumnInfo(),
        new MessageColumnInfo(app)
      });
      this.log = app.getFlutterLog();

      // Scroll to end by default.
      autoScrollToEnd = true;

      updateRunnable = () -> {
        ((AbstractTableModel)treeTable.getModel()).fireTableDataChanged();
        reload(getRoot());
        treeTable.updateUI();

        if (autoScrollToEnd) {
          scrollToEnd();
        }
      };

      updateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
    }

    void scrollToEnd() {
      if (scrollPane != null) {
        final JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setValue(vertical.getMaximum());
      }
    }

    void update() {
      if (!updateAlarm.isDisposed()) {
        updateAlarm.cancelAllRequests();
        updateAlarm.addRequest(updateRunnable, 0, ModalityState.stateForComponent(treeTable));
      }
    }

    @Override
    public LogRootTreeNode getRoot() {
      return (LogRootTreeNode)super.getRoot();
    }

    public void setScrollPane(JScrollPane scrollPane) {
      this.scrollPane = scrollPane;
    }

    @Override
    public void setTree(JTree tree) {
      super.setTree(tree);
      treeTable = ((TreeTableTree)tree).getTreeTable();
    }

    public void onEvent(FlutterLogEntry entry) {
      final MutableTreeNode root = getRoot();
      final FlutterEventNode node = new FlutterEventNode(entry);
      ApplicationManager.getApplication().invokeLater(() -> {
        insertNodeInto(node, root, root.getChildCount());
        update();
      });
    }

    public void clearEntries() {
      log.clear();
      getRoot().removeAllChildren();
      update();
    }
  }

  static class LogRootTreeNode extends DefaultMutableTreeNode {

  }

  static class FlutterEventNode extends DefaultMutableTreeNode {
    final FlutterLogEntry entry;

    FlutterEventNode(FlutterLogEntry entry) {
      this.entry = entry;
    }

    public void describeTo(@NotNull StringBuilder buffer) {
      buffer
        .append(TIMESTAMP_FORMAT.format(entry.getTimestamp()))
        .append(" [")
        .append(entry.getCategory())
        .append("] ")
        .append(entry.getMessage());
      if (!entry.getMessage().endsWith("\n")) {
        buffer.append("\n");
      }
    }
  }

  static class TimeColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    public TimeColumnInfo() {
      super("Time");
    }

    @Nullable
    @Override
    public String valueOf(DefaultMutableTreeNode node) {
      if (node instanceof FlutterEventNode) {
        return TIMESTAMP_FORMAT.format(((FlutterEventNode)node).entry.getTimestamp());
      }
      return null;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode node) {
      return DEFAULT_EVENT_CELL_RENDERER;
    }
  }

  static class CategoryColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    public CategoryColumnInfo() {
      super("Category");
    }

    @Nullable
    @Override
    public String valueOf(DefaultMutableTreeNode node) {
      if (node instanceof FlutterEventNode) {
        return ((FlutterEventNode)node).entry.getCategory();
      }
      return null;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode node) {
      return DEFAULT_EVENT_CELL_RENDERER;
    }
  }

  static class MessageColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    private final LogMessageCellRenderer messageCellRenderer;

    public MessageColumnInfo(FlutterApp app) {
      super("Message");
      messageCellRenderer = new LogMessageCellRenderer(app.getModule());
    }

    @Nullable
    @Override
    public String valueOf(DefaultMutableTreeNode node) {
      if (node instanceof FlutterEventNode) {
        return ((FlutterEventNode)node).entry.getMessage();
      }
      return null;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode node) {
      return messageCellRenderer;
    }
  }

  static class DefaultEventCellRenderer extends ColoredTableCellRenderer {
    @Override
    public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
      // Prevent cell borders on selected cells.
      super.acquireState(table, isSelected, false, row, column);
    }

    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value instanceof String) {
        append((String)value);
      }
    }
  }

  static class LogMessageCellRenderer extends DefaultEventCellRenderer {
    private final FlutterConsoleFilter consoleFilter;

    public LogMessageCellRenderer(Module module) {
      consoleFilter = new FlutterConsoleFilter(module);
    }

    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      // TODO(pq): SpeedSearchUtil.applySpeedSearchHighlighting
      // TODO(pq): setTooltipText

      if (value instanceof String) {
        final String text = (String)value;
        int cursor = 0;

        // TODO(pq): add support for dart uris, etc.
        // TODO(pq): fix FlutterConsoleFilter to handle multiple links.
        final Filter.Result result = consoleFilter.applyFilter(text, text.length());
        if (result != null) {
          for (Filter.ResultItem item : result.getResultItems()) {
            final HyperlinkInfo hyperlinkInfo = item.getHyperlinkInfo();
            if (hyperlinkInfo != null) {
              final int start = item.getHighlightStartOffset();
              final int end = item.getHighlightEndOffset();
              // append leading text.
              if (cursor < start) {
                append(text.substring(cursor, start), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
              append(text.substring(start, end), SimpleTextAttributes.LINK_ATTRIBUTES, hyperlinkInfo);
              cursor = end;
            }
          }
        }

        // append trailing text
        if (cursor < text.length()) {
          append(text.substring(cursor), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    }
  }

  private final LogTreeModel model;

  public FlutterLogTree(@NotNull FlutterApp app, @NotNull Disposable parent) {
    this(new LogTreeModel(app, parent));
  }

  FlutterLogTree(@NotNull LogTreeModel model) {
    super(model);
    model.setTree(this.getTree());
    this.model = model;
  }

  @NotNull
  LogTreeModel getLogTreeModel() {
    return model;
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    // TODO(pq): figure out why this isn't happening on it's own
    @SuppressWarnings("unchecked") final TableCellRenderer renderer = model.getColumns()[column].getRenderer(null);
    return renderer != null ? renderer : super.getCellRenderer(row, column);
  }
}
