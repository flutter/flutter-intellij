/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ColumnInfo;
import io.flutter.logging.tree.*;
import io.flutter.run.daemon.FlutterApp;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Timer;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.flutter.logging.FlutterLogConstants.LogColumns.*;
import static io.flutter.logging.tree.TimeCellRenderer.TIMESTAMP_FORMAT;

public class FlutterLogTree extends TreeTable {

  private static final Logger LOG = Logger.getInstance(FlutterLogTree.class);

  private static class ColumnModel {

    class Column extends ColumnInfo<DefaultMutableTreeNode, FlutterLogEntry> {
      boolean show = true;
      @NotNull
      private final TableCellRenderer renderer;

      Column(@NotNull String name, @NotNull TableCellRenderer renderer) {
        super(name);
        this.renderer = renderer;
      }

      boolean isShowing() {
        return show;
      }

      @Nullable
      @Override
      public FlutterLogEntry valueOf(DefaultMutableTreeNode node) {
        if (node instanceof FlutterEventNode) {
          return ((FlutterEventNode)node).entry;
        }
        return null;
      }

      @Override
      public TableCellRenderer getCustomizedRenderer(DefaultMutableTreeNode o, TableCellRenderer renderer) {
        return this.renderer;
      }
    }

    private final List<Column> columns = new ArrayList<>();

    private final FlutterApp app;
    boolean updateVisibility;
    private List<Column> visible;
    private ArrayList<TableColumn> tableColumns;
    private TreeTable treeTable;

    private static final TableCellRenderer DEFAULT_RENDERER = new DefaultTableCellRenderer();

    ColumnModel(@NotNull FlutterApp app, @NotNull EntryModel entryModel) {
      this.app = app;
      columns.addAll(Arrays.asList(
        new Column(TIME, new TimeCellRenderer(entryModel)),
        new Column(SEQUENCE, new SequenceCellRenderer(entryModel)),
        new Column(LEVEL, new LevelCellRenderer(entryModel)),
        new Column(CATEGORY, new CategoryCellRenderer(entryModel)),
        new Column(MESSAGE, new MessageCellRenderer(app, entryModel))
      ));
      // Cache for quick access.
      visible = columns.stream().filter(c -> c.show).collect(Collectors.toList());
    }

    void show(String column, boolean show) {
      columns.forEach(c -> {
        if (Objects.equals(column, c.getName())) {
          c.show = show;
        }
      });

      updateVisibility = true;

      // Cache for quick access.
      visible = columns.stream().filter(c -> c.show).collect(Collectors.toList());
    }

    void update() {
      if (updateVisibility) {
        // Clear all.
        Collections.list(treeTable.getColumnModel().getColumns()).forEach(c -> treeTable.removeColumn(c));

        // Add back what's appropriate.
        if (isShowing(TIME)) {
          treeTable.addColumn(tableColumns.get(0));
        }
        if (isShowing(SEQUENCE)) {
          treeTable.addColumn(tableColumns.get(1));
        }
        if (isShowing(LEVEL)) {
          treeTable.addColumn(tableColumns.get(2));
        }
        if (isShowing(CATEGORY)) {
          treeTable.addColumn(tableColumns.get(3));
        }

        tableColumns.subList(4, tableColumns.size()).forEach(c -> treeTable.addColumn(c));
      }

      updateVisibility = false;
    }

    ColumnInfo[] getInfos() {
      return columns.toArray(new ColumnInfo[0]);
    }

    public TableCellRenderer getRenderer(int column) {
      // To mitigate a race condition where invisible cells can ask for rendering (despite not
      // being visible) we fall back on a default.
      // (This has no noticeable impact but avoids an occasional range check exception on table init.)
      return column < visible.size() ? visible.get(column).renderer : DEFAULT_RENDERER;
    }

    public boolean isShowing(String column) {
      for (Column c : columns) {
        if (Objects.equals(column, c.getName())) {
          return c.show;
        }
      }
      return false;
    }

    public void init(TreeTable table) {
      treeTable = table;
      tableColumns = Collections.list(table.getColumnModel().getColumns());
    }
  }

  interface SelectionListener extends EventListener {
    void selectionChanged();
  }

  static class TreeModel extends ListTreeTableModelOnColumns {
    interface UpdateCallback {
      void updated();
    }

    @NotNull
    private final ColumnModel columns;
    @NotNull
    private final FlutterLog log;
    @NotNull
    private final Alarm uiThreadAlarm;
    @NotNull
    final Timer updateTimer;

    boolean autoScrollToEnd;
    // Cached for hide and restore.
    private List<TableColumn> tableColumns;
    private JScrollPane scrollPane;
    private TreeTable treeTable;
    private boolean color;
    private UpdateCallback updateCallback;

    @NotNull
    private final FlutterLogPreferences logPreferences;

    public TreeModel(@NotNull FlutterApp app,
                     @NotNull EntryModel entryModel,
                     @NotNull Disposable parent) {
      this(new ColumnModel(app, entryModel), parent, FlutterLogPreferences.getInstance(app.getProject()));
    }

    private TreeModel(@NotNull ColumnModel columns, @NotNull Disposable parent, @NotNull FlutterLogPreferences logPreferences) {
      super(new LogRootTreeNode(), columns.getInfos());
      this.logPreferences = logPreferences;

      this.log = columns.app.getFlutterLog();
      this.columns = columns;

      // Scroll to end by default.
      autoScrollToEnd = true;

      setShowSequenceNumbers(false);

      uiThreadAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
      updateTimer = new Timer(100, e -> uiExec(this::update, 0));
    }

    public void setUpdateCallback(UpdateCallback updateCallback) {
      this.updateCallback = updateCallback;
    }

    @NotNull
    public FlutterLogPreferences getLogPreferences() {
      return logPreferences;
    }

    void scrollToEnd() {
      if (scrollPane != null) {
        final JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setValue(vertical.getMaximum());
      }
    }

    void uiExec(@NotNull Runnable runnable, int delayMillis) {
      if (!uiThreadAlarm.isDisposed()) {
        uiThreadAlarm.addRequest(runnable, delayMillis);
      }
    }

    void update(@Nullable int[] selectedRows) {
      columns.update();

      reload(getRoot());
      treeTable.updateUI();

      // Preserve / restore selection state if unspecified.
      if (selectedRows == null) {
        selectedRows = treeTable.getSelectedRows();
      }
      treeTable.getTree().setSelectionRows(selectedRows);

      if (updateCallback != null) {
        updateCallback.updated();
      }

      if (autoScrollToEnd) {
        uiExec(this::scrollToEnd, 100);
      }
    }

    void update() {
      update(null);
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
      columns.init(treeTable);
    }

    public void clearEntries() {
      log.clear();
      getRoot().removeAllChildren();
      update();
    }

    public void appendNode(FlutterLogEntry entry, boolean selectNode) {
      if (treeTable == null || uiThreadAlarm.isDisposed()) {
        return;
      }

      getRoot().add(new FlutterEventNode(entry));

      if (updateTimer.isRunning()) {
        return;
      }

      updateTimer.restart();
    }

    public boolean shouldShowTimestamps() {
      return columns.isShowing(TIME);
    }

    public void setShowTimestamps(boolean show) {
      columns.show(TIME, show);
    }

    public boolean shouldShowSequenceNumbers() {
      return columns.isShowing(SEQUENCE);
    }

    public void setShowSequenceNumbers(boolean show) {
      columns.show(SEQUENCE, show);
    }

    public boolean shouldShowLogLevels() {
      return columns.isShowing(LEVEL);
    }

    public void setShowLogLevels(boolean show) {
      columns.show(LEVEL, show);
    }

    public void setShowCategories(boolean show) {
      columns.show(CATEGORY, show);
    }

    public boolean shouldShowCategories() {
      return columns.isShowing(CATEGORY);
    }

    void updateFromPreferences(@NotNull FlutterLogPreferences flutterLogPreferences) {
      setShowTimestamps(flutterLogPreferences.isShowTimestamp());
      setShowSequenceNumbers(flutterLogPreferences.isShowSequenceNumbers());
      setShowLogLevels(flutterLogPreferences.isShowLogLevel());
      setShowCategories(flutterLogPreferences.isShowLogCategory());
    }
  }

  static class LogRootTreeNode extends DefaultMutableTreeNode {

  }

  public static class FlutterEventNode extends DefaultMutableTreeNode {
    final FlutterLogEntry entry;

    FlutterEventNode(FlutterLogEntry entry) {
      this.entry = entry;
    }

    public void describeTo(@NotNull StringBuilder buffer) {
      buffer
        .append(TIMESTAMP_FORMAT.format(entry.getTimestamp()))
        .append(" ").append(entry.getSequenceNumber())
        .append(" ").append(entry.getLevelName())
        .append(" ").append(entry.getCategory())
        .append(" ").append(entry.getMessage());
      if (!entry.getMessage().endsWith("\n")) {
        buffer.append("\n");
      }
    }
  }

  private static final RowFilter<TableModel, Object> EMPTY_FILTER = new RowFilter<TableModel, Object>() {
    @Override
    public boolean include(Entry<? extends TableModel, ?> entry) {
      return true;
    }
  };

  public interface EntryModel {
    SimpleTextAttributes style(@Nullable FlutterLogEntry entry, int attributes);
  }

  public interface EventCountListener extends EventListener {
    void updated(int total, int filtered);
  }

  private final EventDispatcher<EventCountListener> countDispatcher = EventDispatcher.create(EventCountListener.class);
  private final EventDispatcher<SelectionListener> selectionEventDispatcher = EventDispatcher.create(SelectionListener.class);

  private final TreeModel model;
  private FlutterLogFilterPanel.FilterParam filter;
  @NotNull
  private final TableRowSorter<TableModel> rowSorter;
  @NotNull
  private final FlutterLogEntryPopup flutterLogPopup;

  public FlutterLogTree(@NotNull FlutterApp app,
                        @NotNull EntryModel entryModel,
                        @NotNull Disposable parent) {
    this(new TreeModel(app, entryModel, parent));
  }

  FlutterLogTree(@NotNull TreeModel model) {
    super(model);
    model.setTree(this.getTree());
    this.model = model;
    final TableModel tableModel = getModel();
    rowSorter = new TableRowSorter<>(new FlutterTreeTableModel(this));
    setRowSorter(rowSorter);
    registerCopyHandler();
    flutterLogPopup = new FlutterLogEntryPopup();
    addMouseListener(new SimpleMouseListener() {
      @Override
      public void mouseDoublePressed(MouseEvent e) {
        final String selectedLog = getSelectedLog();
        if (StringUtils.isNotEmpty(selectedLog)) {
          flutterLogPopup.showLogDialog(selectedLog);
        }
      }
    });
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          getTree().setSelectionRow(getSelectedRow() + 1);
        }
        else if (e.getKeyCode() == KeyEvent.VK_UP) {
          getTree().setSelectionRow(getSelectedRow() - 1);
        }
      }
    });

    // The row sorter is our source of truth for row counts.
    // To get notifications of unfiltered counts, we need to set a no-op filter.
    rowSorter.setRowFilter(EMPTY_FILTER);

    rowSorter.addRowSorterListener(e -> updateCounter());
    getTree().addTreeSelectionListener(e -> selectionEventDispatcher.getMulticaster().selectionChanged());
  }

  List<FlutterEventNode> getSelectedNodes() {
    final List<FlutterEventNode> nodes = new ArrayList<>();
    for (int row : getSelectedRows()) {
      final int realRow = convertRowIndexToModel(row);
      final Object pathComponent = getTree().getPathForRow(realRow).getLastPathComponent();
      if (pathComponent instanceof FlutterLogTree.FlutterEventNode) {
        nodes.add((FlutterEventNode)pathComponent);
      }
    }
    return nodes;
  }

  @NotNull
  private String getSelectedLog() {
    final StringBuilder logBuilder = new StringBuilder();
    for (FlutterEventNode node : getSelectedNodes()) {
      node.describeTo(logBuilder);
    }
    return logBuilder.toString();
  }

  public void sendSelectedLogsToClipboard() {
    ApplicationManager.getApplication().invokeLater(() -> {
      final String log = getSelectedLog();
      if (StringUtils.isNotEmpty(log)) {
        final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        final StringSelection selection = new StringSelection(log);
        clipboard.setContents(selection, selection);
      }
    });
  }

  private void registerCopyHandler() {
    final AnAction actionCopy = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY);
    final ShortcutSet copyShortcutSet = actionCopy.getShortcutSet();
    final String copyCommand = "flutter.log.copyCommand";

    Arrays.stream(copyShortcutSet.getShortcuts())
      .filter(shortcut -> shortcut instanceof KeyboardShortcut)
      .map(shortcut -> (KeyboardShortcut)shortcut)
      .flatMap(shortcut -> Stream.of(shortcut.getFirstKeyStroke(), shortcut.getSecondKeyStroke()))
      .filter(Objects::nonNull)
      .forEach(stroke -> getInputMap().put(stroke, copyCommand));

    getActionMap().put(copyCommand, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        sendSelectedLogsToClipboard();
      }
    });
  }

  public void addSelectionListener(SelectionListener listener) {
    selectionEventDispatcher.addListener(listener);
  }

  public void removeSelectionListener(SelectionListener listener) {
    selectionEventDispatcher.removeListener(listener);
  }

  public void addListener(@NotNull EventCountListener listener, @NotNull Disposable parent) {
    countDispatcher.addListener(listener, parent);
  }

  public void removeListener(@NotNull EventCountListener listener) {
    countDispatcher.removeListener(listener);
  }

  @NotNull
  TreeModel getLogTreeModel() {
    return model;
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    return model.columns.getRenderer(column);
  }

  public void setFilter(@NotNull FlutterLogFilterPanel.FilterParam filter) {
    if (Objects.equals(this.filter, filter)) {
      return;
    }
    this.filter = filter;
    rowSorter.setRowFilter(getRowFilter(filter));
  }

  private RowFilter<TableModel, Object> getRowFilter(@NotNull FlutterLogFilterPanel.FilterParam filter) {
    final RowFilter<TableModel, Object> logLevelFilter = RowFilter
      .numberFilter(RowFilter.ComparisonType.AFTER, filter.getLogLevel().value - 1, FlutterTreeTableModel.ColumnIndex.LOG_LEVEL.index);
    final RowFilter<TableModel, Object> expressionFilter = getExpressionFilter(filter);
    return expressionFilter != null ? RowFilter.andFilter(Arrays.asList(logLevelFilter, expressionFilter)) : logLevelFilter;
  }

  private RowFilter<TableModel, Object> getExpressionFilter(@NotNull FlutterLogFilterPanel.FilterParam filter) {
    final String nonNullExpression = filter.getExpression() == null ? "" : filter.getExpression();
    if (!nonNullExpression.isEmpty() || filter.isMatchCase() || filter.isRegex()) {
      final String quoteRegex = filter.isRegex() ? nonNullExpression : Pattern.quote(nonNullExpression);
      final String matchCaseRegex = filter.isMatchCase() ? "" : "(?i)";
      final String standardRegex = matchCaseRegex + "(?s).*" + quoteRegex + ".*";
      try {
        return RowFilter.regexFilter(
          standardRegex,
          FlutterTreeTableModel.ColumnIndex.MESSAGE.index,
          FlutterTreeTableModel.ColumnIndex.CATEGORY.index
        );
      }
      catch (PatternSyntaxException e) {
        // TODO(pq): Notify user; in the meantime, just fall-through.
      }
    }

    return null;
  }

  void append(@NotNull FlutterLogEntry entry, boolean selectNode) {
    if (entry.getKind() == FlutterLogEntry.Kind.RELOAD && model.getLogPreferences().isClearOnReload() ||
        entry.getKind() == FlutterLogEntry.Kind.RESTART && model.getLogPreferences().isClearOnRestart()
    ) {
      clearEntries();
    }
    model.appendNode(entry, selectNode);
  }

  private void updateCounter() {
    final int total = rowSorter.getModelRowCount();
    final int filtered = total - rowSorter.getViewRowCount();
    countDispatcher.getMulticaster().updated(total, filtered);
  }

  public void clearEntries() {
    // Ensure selections are cleared on empty.
    getTree().clearSelection();
    model.clearEntries();
  }

  private static abstract class SimpleMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      if (e.getClickCount() == 2) {
        mouseDoublePressed(e);
      }
    }

    public abstract void mouseDoublePressed(MouseEvent e);
  }
}
