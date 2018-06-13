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
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ColumnInfo;
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class FlutterLogTree extends TreeTable {

  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
  private static final DefaultEventCellRenderer DEFAULT_EVENT_CELL_RENDERER = new DefaultEventCellRenderer();

  static class LogTreeModel extends ListTreeTableModelOnColumns {
    @NotNull
    private final FlutterLog log;
    @NotNull
    private final Alarm uiThreadAlarm;
    boolean autoScrollToEnd;
    // Cached for hide and restore (because *sigh* Swing).
    private List<TableColumn> tableColumns;
    private boolean showTimestamps;
    private boolean showLogLevels;
    private boolean showSequenceNumbers;
    private boolean updateColumns;
    private JScrollPane scrollPane;
    private TreeTable treeTable;

    public LogTreeModel(@NotNull FlutterApp app, @NotNull Disposable parent) {
      super(new LogRootTreeNode(), new ColumnInfo[]{
        new TimeColumnInfo(),
        new SequenceColumnInfo(),
        new LevelColumnInfo(),
        new CategoryColumnInfo(),
        new MessageColumnInfo(app)
      });
      this.log = app.getFlutterLog();

      // Scroll to end by default.
      autoScrollToEnd = true;

      // Show timestamps by default.
      showTimestamps = true;
      showSequenceNumbers = false;
      showLogLevels = true;

      // Hide columns as needed.
      updateColumns = true;

      uiThreadAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
    }

    void scrollToEnd() {
      if (scrollPane != null) {
        final JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setValue(vertical.getMaximum());
      }
    }

    void update() {
      if (updateColumns) {
        // Clear all.
        Collections.list(treeTable.getColumnModel().getColumns()).forEach(c -> treeTable.removeColumn(c));

        // Add back what's appropriate.
        if (showTimestamps) {
          treeTable.addColumn(tableColumns.get(0));
        }
        if (showSequenceNumbers) {
          treeTable.addColumn(tableColumns.get(1));
        }
        if (showLogLevels) {
          treeTable.addColumn(tableColumns.get(2));
        }

        tableColumns.subList(3, tableColumns.size()).forEach(c -> treeTable.addColumn(c));
      }

      reload(getRoot());
      treeTable.updateUI();

      if (autoScrollToEnd) {
        scrollToEnd();
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

      tableColumns = Collections.list(treeTable.getColumnModel().getColumns());
    }

    public void clearEntries() {
      log.clear();
      getRoot().removeAllChildren();
      update();
    }

    public void appendNodes(List<FlutterLogEntry> entries) {
      ApplicationManager.getApplication().invokeLater(() -> {
        final MutableTreeNode root = getRoot();
        entries.forEach(entry -> insertNodeInto(new FlutterEventNode(entry), root, root.getChildCount()));
        update();
      });

      // Schedule an update to scroll after the model has had time to re-render.
      uiThreadAlarm.addRequest(() -> {
        if (autoScrollToEnd) {
          scrollToEnd();
        }
        // A simple delay should suffice, given our mantra of eventual consistency.
        // If not, we can investigate a proper condition.
      }, 100);
    }

    public boolean getShowTimestamps() {
      return showTimestamps;
    }

    public void setShowTimestamps(boolean showTimestamps) {
      this.showTimestamps = showTimestamps;
      updateColumns = true;
    }

    public boolean getShowSequenceNumbers() {
      return showSequenceNumbers;
    }

    public void setShowSequenceNumbers(boolean state) {
      this.showSequenceNumbers = state;
      updateColumns = true;
    }

    public boolean getShowLogLevels() {
      return showLogLevels;
    }

    public void setShowLogLevels(boolean showLogLevels) {
      this.showLogLevels = showLogLevels;
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

  private static abstract class AbstractColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    AbstractColumnInfo(@NotNull String name) {
      super(name);
    }

    @Nullable
    @Override
    public final String valueOf(DefaultMutableTreeNode node) {
      if (node instanceof FlutterEventNode) {
        return valueOf(((FlutterEventNode)node).entry);
      }
      return null;
    }

    @Nullable
    public abstract String valueOf(FlutterLogEntry entry);

    @Nullable
    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode node) {
      return DEFAULT_EVENT_CELL_RENDERER;
    }
  }

  static class TimeColumnInfo extends AbstractColumnInfo {
    public TimeColumnInfo() {
      super("Time");
    }

    @Nullable
    @Override
    public String valueOf(FlutterLogEntry entry) {
      return TIMESTAMP_FORMAT.format(entry.getTimestamp());
    }
  }

  static class SequenceColumnInfo extends AbstractColumnInfo {
    public SequenceColumnInfo() {
      super("Sequence");
    }

    @Nullable
    @Override
    public String valueOf(FlutterLogEntry entry) {
      return Integer.toString(entry.getSequenceNumber());
    }
  }

  static class LevelColumnInfo extends AbstractColumnInfo {
    public LevelColumnInfo() {
      super("Level");
    }

    @Nullable
    @Override
    public String valueOf(FlutterLogEntry entry) {
      final FlutterLog.Level level = FlutterLog.Level.forValue(entry.getLevel());
      return level != null ? level.name() : Integer.toString(entry.getLevel());
    }
  }

  static class CategoryColumnInfo extends AbstractColumnInfo {
    public CategoryColumnInfo() {
      super("Category");
    }

    @Nullable
    @Override
    public String valueOf(FlutterLogEntry entry) {
      return entry.getCategory();
    }
  }

  static class MessageColumnInfo extends AbstractColumnInfo {
    private final LogMessageCellRenderer messageCellRenderer;

    public MessageColumnInfo(FlutterApp app) {
      super("Message");
      messageCellRenderer = new LogMessageCellRenderer(app.getModule());
    }

    @Nullable
    @Override
    public String valueOf(FlutterLogEntry entry) {
      return entry.getMessage();
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

  public static class EntryFilter {
    @Nullable
    private final String text;
    private final boolean isRegex;
    private final boolean isMatchCase;

    public EntryFilter(@Nullable String text) {
      this(text, false, false);
    }

    public EntryFilter(@Nullable String text, boolean isMatchCase, boolean isRegex) {
      this.text = text;
      this.isMatchCase = isMatchCase;
      this.isRegex = isRegex;
    }

    @Nullable
    public String getText() {
      return text;
    }

    public boolean accept(@NotNull FlutterLogEntry entry) {
      if (text == null) {
        return true;
      }
      final String standardText = isMatchCase ? text : text.toLowerCase();
      final String standardMessage = isMatchCase ? entry.getMessage() : entry.getMessage().toLowerCase();
      final String standardCategory = isMatchCase ? entry.getCategory() : entry.getCategory().toLowerCase();
      if (acceptByCheckingRegexOption(standardCategory, standardText)) {
        return true;
      }
      return acceptByCheckingRegexOption(standardMessage, standardText);
    }

    private boolean acceptByCheckingRegexOption(@NotNull String message, @NotNull String text) {
      if (isRegex) {
        return message.matches("(?s).*" + text + ".*");
      }
      return message.contains(text);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final EntryFilter filter = (EntryFilter)o;
      return isRegex == filter.isRegex &&
             isMatchCase == filter.isMatchCase &&
             Objects.equals(text, filter.text);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, isRegex, isMatchCase);
    }
  }

  public interface EventCountListener extends EventListener {
    void updated(int filtered, int total);
  }

  private final EventDispatcher<EventCountListener> countDispatcher = EventDispatcher.create(EventCountListener.class);

  private final LogTreeModel model;
  private EntryFilter filter;

  public FlutterLogTree(@NotNull FlutterApp app, @NotNull Disposable parent) {
    this(new LogTreeModel(app, parent));
  }

  FlutterLogTree(@NotNull LogTreeModel model) {
    super(model);
    model.setTree(this.getTree());
    this.model = model;
  }

  public void addListener(@NotNull EventCountListener listener, @NotNull Disposable parent) {
    countDispatcher.addListener(listener, parent);
  }

  public void removeListener(@NotNull EventCountListener listener) {
    countDispatcher.removeListener(listener);
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

  public void setFilter(@Nullable EntryFilter filter) {
    // Only set and reload if the filter has changed.
    if (!Objects.equals(this.filter, filter)) {
      this.filter = filter;
      reload();
    }
  }

  void reload() {
    ApplicationManager.getApplication().invokeLater(() -> {
      model.getRoot().removeAllChildren();

      final List<FlutterLogEntry> entries = model.log.getEntries();
      final List<FlutterLogEntry> matched = entries.stream()
        .filter(entry -> filter == null || filter.accept(entry)).collect(Collectors.toList());

      model.appendNodes(matched);
      model.update();

      countDispatcher.getMulticaster().updated(entries.size() - matched.size(), entries.size());
    });
  }
}
