/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;

public class FlutterLogView extends JPanel implements ConsoleView, DataProvider, FlutterLog.Listener {

  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

  private final SimpleToolWindowPanel toolWindowPanel;

  private class ClearLogAction extends AnAction {
    ClearLogAction() {
      super("Clear All", "Clear the log", AllIcons.Actions.GC);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ApplicationManager.getApplication().invokeLater(() -> {
        model.getRoot().removeAllChildren();
        model.update();
      });
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(model.getRoot().getChildCount() > 0);
    }
  }

  private class ScrollToEndAction extends ToggleAction {
    ScrollToEndAction() {
      super("Scroll to the end", "Scroll to the end", AllIcons.RunConfigurations.Scroll_down);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return model.autoScrollToEnd;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      ApplicationManager.getApplication().invokeLater(() -> {
        model.autoScrollToEnd = state;
        model.scrollToEnd();
      });
    }
  }

  @NotNull final FlutterApp app;
  final FlutterLogTreeTableModel model;
  private final FlutterLogTreeTable treeTable;
  private SimpleTreeBuilder builder;

  public FlutterLogView(@NotNull FlutterApp app) {
    this.app = app;

    final FlutterLog flutterLog = app.getFlutterLog();
    flutterLog.addListener(this, this);

    final DefaultActionGroup toolbarGroup = createToolbar();

    final Content content = ContentFactory.SERVICE.getInstance().createContent(null, null, false);
    content.setCloseable(false);

    toolWindowPanel = new SimpleToolWindowPanel(true, true);
    content.setComponent(toolWindowPanel);

    final ActionToolbar windowToolbar = ActionManager.getInstance().createActionToolbar("FlutterLogViewToolbar", toolbarGroup, true);
    toolWindowPanel.setToolbar(windowToolbar.getComponent());

    model = new FlutterLogTreeTableModel(app, this);
    treeTable = new FlutterLogTreeTable(model);

    // TODO(pq): add speed search
    //new TreeTableSpeedSearch(treeTable).setComparator(new SpeedSearchComparator(false));

    treeTable.setTableHeader(null);
    treeTable.setRootVisible(false);

    treeTable.setExpandableItemsEnabled(true);
    treeTable.getTree().setScrollsOnExpand(true);

    treeTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final JTable table = (JTable)e.getSource();
        final int row = table.rowAtPoint(e.getPoint());
        final int column = table.columnAtPoint(e.getPoint());
        if (row == -1 || column == -1) return;
        final TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
        if (cellRenderer instanceof ColoredTableCellRenderer) {
          final ColoredTableCellRenderer renderer = (ColoredTableCellRenderer)cellRenderer;
          final Rectangle rc = table.getCellRect(row, column, false);
          final Object tag = renderer.getFragmentTagAt(e.getX() - rc.x);
          // TODO(pq): consider generalizing to a runnable and wrapping the hyperlinkinfo
          if (tag instanceof OpenFileHyperlinkInfo) {
            ((OpenFileHyperlinkInfo)tag).navigate(app.getProject());
          }
        }
      }
    });

    // Set bounds.
    treeTable.getColumn("Time").setMinWidth(100);
    treeTable.getColumn("Time").setMaxWidth(100);
    treeTable.getColumn("Category").setMinWidth(110);
    treeTable.getColumn("Category").setMaxWidth(110);
    treeTable.getColumn("Message").setMinWidth(100);

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(treeTable,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    model.setScrollPane(pane);
    toolWindowPanel.setContent(pane);
  }

  @NotNull
  private FlutterLog getFlutterLog() {
    return app.getFlutterLog();
  }

  private DefaultActionGroup createToolbar() {
    //noinspection UnnecessaryLocalVariable
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    // TODO(pq): add toolbar items.
    return toolbarGroup;
  }

  @Override
  public void onEvent(@NotNull FlutterLogEntry entry) {
    model.onEvent(entry);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    getFlutterLog().addConsoleEntry(text, contentType);
  }

  @Override
  public void clear() {
    // TODO(pq): called on restart; should (optionally) clear _or_ set a visible marker.
  }

  @Override
  public void scrollTo(int offset) {

  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    getFlutterLog().listenToProcess(processHandler, this);
  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public void setOutputPaused(boolean value) {

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
    return new AnAction[]{
      new ScrollToEndAction(),
      new ClearLogAction()
    };
  }

  @Override
  public void allowHeavyFilters() {

  }

  @Override
  public void dispose() {

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
    return treeTable;
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

  static class FlutterLogTreeTableModel extends ListTreeTableModelOnColumns {
    @NotNull
    private final Runnable updateRunnable;
    @NotNull
    private final FlutterLog log;
    @NotNull
    private final Alarm updateAlarm;

    private JScrollPane scrollPane;
    private TreeTable treeTable;
    private boolean autoScrollToEnd;

    public FlutterLogTreeTableModel(@NotNull FlutterApp app, @NotNull Disposable parent) {
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

    private void scrollToEnd() {
      if (scrollPane != null) {
        final JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setValue(vertical.getMaximum());
      }
    }

    private void update() {
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
  }

  static class LogRootTreeNode extends DefaultMutableTreeNode {

  }

  static class FlutterEventNode extends DefaultMutableTreeNode {
    final FlutterLogEntry entry;

    FlutterEventNode(FlutterLogEntry entry) {
      this.entry = entry;
    }
  }

  class FlutterLogTreeTable extends TreeTable {
    public FlutterLogTreeTable(@NotNull FlutterLogTreeTableModel model) {
      super(model);
      model.setTree(this.getTree());
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
      // TODO(pq): figure out why this isn't happening on it's own
      @SuppressWarnings("unchecked")
      final TableCellRenderer renderer = model.getColumns()[column].getRenderer(null);
      return renderer != null ? renderer : super.getCellRenderer(row, column);
    }
  }

  static class LogMessageCellRenderer extends ColoredTableCellRenderer {
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
}
