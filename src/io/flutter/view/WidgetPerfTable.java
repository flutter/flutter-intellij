/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.xdebugger.XSourcePosition;
import gnu.trove.TIntArrayList;
import io.flutter.inspector.InspectorActions;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.InspectorTree;
import io.flutter.perf.*;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashSet;

import static io.flutter.perf.Icons.getIconForCount;

class WidgetPerfTable extends TreeTable implements DataProvider, PerfModel {
  private final ColumnInfo[] modelColumns;
  private final CountColumnInfo countColumnInfo;
  private final WidgetNameColumnInfo widgetNameColumnInfo;
  private final FlutterApp app;
  private final FlutterWidgetPerfManager perfManager;
  private final ListTreeTableModelOnColumns model;
  private final DefaultMutableTreeNode root;
  private final PerfMetric metric;
  private final HashSet<String> openPaths = new HashSet<>();
  private final ArrayList<PerfMetric> metrics;
  private ArrayList<SlidingWindowStatsSummary> entries = new ArrayList<>();
  private boolean idle;
  private DefaultMutableTreeNode currentSelection;

  WidgetPerfTable(FlutterApp app, Disposable parentDisposable, PerfMetric metric) {
    super(new ListTreeTableModelOnColumns(
      new DefaultMutableTreeNode(),
      new ColumnInfo[]{
        new WidgetNameColumnInfo("Widget"),
        new LocationColumnInfo("Location"),
        new CountColumnInfo(metric),
        new CountColumnInfo(PerfMetric.totalSinceRouteChange)
      }
    ));

    setSurrendersFocusOnKeystroke(false);
    this.app = app;
    model = getTreeModel();
    modelColumns = model.getColumns();
    widgetNameColumnInfo = (WidgetNameColumnInfo)model.getColumns()[0];
    countColumnInfo = (CountColumnInfo)model.getColumns()[2];
    perfManager = FlutterWidgetPerfManager.getInstance(app.getProject());

    setRootVisible(false);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    getTree().addTreeSelectionListener(this::selectionListener);

    final MouseListener mouseListener = new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        final int rowIndex = getTree().getRowForLocation(e.getX(), e.getY());
        if (rowIndex != -1) {
          onClickRow(rowIndex);
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        perfManager.getCurrentStats().setAlwaysShowLineMarkersOverride(true);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        perfManager.getCurrentStats().setAlwaysShowLineMarkersOverride(false);
      }
    };
    addMouseListener(mouseListener);
    setStriped(true);

    final JTableHeader tableHeader = getTableHeader();
    tableHeader.setPreferredSize(new Dimension(0, getRowHeight()));

    getColumnModel().getColumn(0).setPreferredWidth(120);
    getColumnModel().getColumn(1).setPreferredWidth(200);
    getColumnModel().getColumn(2).setPreferredWidth(60);

    this.metric = metric;
    this.metrics = new ArrayList<>();
    metrics.add(metric);
    metrics.add(PerfMetric.totalSinceRouteChange);
    root = new DefaultMutableTreeNode();
    model.setRoot(root);
  }

  void sortByMetric(ArrayList<SlidingWindowStatsSummary> entries) {
    openPaths.clear();
    for (TextEditor editor : perfManager.getSelectedEditors()) {
      final VirtualFile file = editor.getFile();
      if (file != null) {
        openPaths.add(InspectorService.toSourceLocationUri(file.getPath()));
      }
    }

    if (entries != null) {
      entries.sort((a, b) -> {
        final int comparison = Integer.compare(b.getValue(metric), a.getValue(metric));
        if (comparison != 0) {
          return comparison;
        }
        return Boolean.compare(isOpenLocation(b.getLocation()), isOpenLocation(a.getLocation()));
      });
    }
  }

  public ArrayList<PerfMetric> getMetrics() {
    return metrics;
  }

  private boolean isOpenLocation(Location location) {
    if (location == null) {
      return false;
    }
    return openPaths.contains(location.path);
  }

  public void clear() {
    showStats(new ArrayList<>());
  }

  @Override
  public boolean isAnimationActive() {
    if (idle) {
      return false;
    }
    // If any rows will be animating, the first row will be animating.
    return entries.size() > 0 && entries.get(0).getValue(metric) > 0;
  }

  @Override
  public void onFrame() {
    if (app.isReloading() || entries.isEmpty() || !isAnimationActive()) {
      return;
    }
    updateIconUIAnimations();
  }

  private void updateIconUIAnimations() {
    int lastActive = -1;
    // TODO(devoncarew): We've seen NPEs from here.
    for (int i = 0; i < entries.size() && entries.get(i).getValue(metric) > 0; ++i) {
      lastActive = i;
    }
    if (lastActive >= 0) {
      final int[] active = new int[lastActive + 1];
      for (int i = 0; i <= lastActive; i++) {
        active[i] = i;
      }
      model.nodesChanged(root, active);
    }
  }

  private void onClickRow(int index) {
    if (index < 0 || index >= entries.size()) {
      return;
    }
    final SlidingWindowStatsSummary stats = entries.get(index);
    // If the selection is changed by this click there is no need to trigger
    // the navigation event here as it will be triggered by the selectionListener.
    final boolean selectionChanged = currentSelection == null || currentSelection.getUserObject() == stats;
    if (!selectionChanged) {
      navigateToStatsEntry(stats);
    }
  }

  private void selectionListener(TreeSelectionEvent event) {
    final DefaultMutableTreeNode selection = (DefaultMutableTreeNode)event.getPath().getLastPathComponent();
    if (!event.isAddedPath()) {
      // We only care about selection events not deselection events.
      return;
    }
    if (currentSelection == selection) {
      // Selection didn't really change. Unfortunately, events about modifying
      // nodes result in spurious selection changed events.
      return;
    }
    currentSelection = selection;

    if (selection != null) {
      final SlidingWindowStatsSummary stats = (SlidingWindowStatsSummary)selection.getUserObject();
      navigateToStatsEntry(stats);
    }
  }

  private void navigateToStatsEntry(SlidingWindowStatsSummary stats) {
    if (stats == null) {
      return;
    }
    final Location location = stats.getLocation();
    final XSourcePosition position = location.getXSourcePosition();
    if (position != null) {
      AsyncUtils.invokeLater(() -> {
        position.createNavigatable(app.getProject()).navigate(false);
      });
    }
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    final ColumnInfo m = modelColumns[column];
    return m.getRenderer(null);
  }

  private ActionGroup createTreePopupActions() {
    final DefaultActionGroup group = new DefaultActionGroup();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction(InspectorActions.JUMP_TO_SOURCE));
    return group;
  }

  ListTreeTableModelOnColumns getTreeModel() {
    return (ListTreeTableModelOnColumns)getTableModel();
  }

  public void markAppIdle() {
    idle = true;
    widgetNameColumnInfo.setIdle(true);
    updateIconUIAnimations();
  }

  public void showStats(ArrayList<SlidingWindowStatsSummary> entries) {
    if (entries == null) {
      entries = new ArrayList<>();
    }
    idle = false;
    widgetNameColumnInfo.setIdle(false);
    final ArrayList<SlidingWindowStatsSummary> oldEntries = this.entries;
    sortByMetric(entries);
    this.entries = entries;
    int selectionIndex = -1;
    Location lastSelectedLocation = null;

    if (statsChanged(oldEntries, entries)) {
      final int selectedRowIndex = getSelectedRow();
      if (selectedRowIndex != -1) {
        final Object selectedRow = getTreeModel().getRowValue(selectedRowIndex);
        if (selectedRow instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode selectedRowNode = (DefaultMutableTreeNode)selectedRow;
          final SlidingWindowStatsSummary selectedStats = (SlidingWindowStatsSummary)selectedRowNode.getUserObject();
          if (selectedStats != null) {
            lastSelectedLocation = selectedStats.getLocation();
          }
        }
      }
      final boolean previouslyEmpty = root.getChildCount() == 0;
      int childIndex = 0;
      final TIntArrayList indicesChanged = new TIntArrayList();
      final TIntArrayList indicesInserted = new TIntArrayList();
      for (SlidingWindowStatsSummary entry : entries) {
        if (entry.getLocation().equals(lastSelectedLocation)) {
          selectionIndex = childIndex;
        }
        if (childIndex >= root.getChildCount()) {
          root.add(new DefaultMutableTreeNode(entry, false));
          indicesInserted.add(childIndex);
        }
        else {
          final DefaultMutableTreeNode existing = (DefaultMutableTreeNode)root.getChildAt(childIndex);
          final SlidingWindowStatsSummary existingEntry = (SlidingWindowStatsSummary)existing.getUserObject();
          if (displayChanged(entry, existingEntry)) {
            model.nodeChanged(existing);
            indicesChanged.add(childIndex);
          }
          existing.setUserObject(entry);
        }
        childIndex++;
      }
      final int endChildIndex = childIndex;
      final ArrayList<TreeNode> nodesRemoved = new ArrayList<>();
      final TIntArrayList indicesRemoved = new TIntArrayList();
      // Gather nodes to remove.
      for (int j = endChildIndex; j < root.getChildCount(); j++) {
        nodesRemoved.add(root.getChildAt(j));
        indicesRemoved.add(j);
      }
      // Actuallly remove nodes.
      while (endChildIndex < root.getChildCount()) {
        // Removing the last element is slightly more efficient.
        final int lastChild = root.getChildCount() - 1;
        root.remove(lastChild);
      }

      if (previouslyEmpty) {
        // TODO(jacobr): I'm not clear why this event is needed in this case.
        model.nodeStructureChanged(root);
      }
      else {
        // Report events for all the changes made to the table.
        if (indicesChanged.size() > 0) {
          model.nodesChanged(root, indicesChanged.toNativeArray());
        }
        if (indicesInserted.size() > 0) {
          model.nodesWereInserted(root, indicesInserted.toNativeArray());
        }
        if (indicesRemoved.size() > 0) {
          model.nodesWereRemoved(root, indicesRemoved.toNativeArray(), nodesRemoved.toArray());
        }
      }

      if (selectionIndex >= 0) {
        getSelectionModel().setSelectionInterval(selectionIndex, selectionIndex);
        currentSelection = (DefaultMutableTreeNode)root.getChildAt(selectionIndex);
      }
      else {
        getSelectionModel().clearSelection();
      }
    }
  }

  private boolean statsChanged(ArrayList<SlidingWindowStatsSummary> previous, ArrayList<SlidingWindowStatsSummary> current) {
    if (previous == current) {
      return false;
    }
    if (previous == null || current == null) {
      return true;
    }
    if (previous.size() != current.size()) {
      return true;
    }
    for (int i = 0; i < previous.size(); ++i) {
      if (displayChanged(previous.get(i), current.get(i))) {
        return true;
      }
    }
    return false;
  }

  private boolean displayChanged(SlidingWindowStatsSummary previous, SlidingWindowStatsSummary current) {
    // We only care about updating if the value for the current metric being displayed has changed.
    if (!previous.getLocation().equals(current.getLocation())) {
      return true;
    }

    for (PerfMetric metric : getMetrics()) {
      if (previous.getValue(metric) != current.getValue(metric)) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    return InspectorTree.INSPECTOR_KEY.is(dataId) ? getTree() : null;
  }

  private static class WidgetNameRenderer extends SimpleColoredRenderer implements TableCellRenderer {

    private boolean idle = false;

    @Override
    public final Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                         boolean isSelected, boolean hasFocus, int row, int col) {
      final JPanel panel = new JPanel();
      if (value == null) return panel;
      panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

      if (value instanceof SlidingWindowStatsSummary) {
        final SlidingWindowStatsSummary stats = (SlidingWindowStatsSummary)value;
        final SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;

        final JBLabel label = new JBLabel(stats.getLocation().name);
        if (isSelected) {
          label.setForeground(table.getSelectionForeground());
        }
        final int count = stats.getValue(PerfMetric.lastFrame);
        label.setIcon(getIconForCount(idle ? 0 : count, true));
        panel.add(Box.createHorizontalStrut(16));
        panel.add(label);
      }

      clear();
      setPaintFocusBorder(hasFocus && table.getCellSelectionEnabled());
      acquireState(table, isSelected, hasFocus, row, col);
      getCellState().updateRenderer(this);

      if (isSelected) {
        panel.setBackground(table.getSelectionBackground());
      }

      return panel;
    }

    public void setIdle(boolean idle) {
      this.idle = idle;
    }
  }

  private static class WidgetLocationRenderer extends SimpleColoredRenderer implements TableCellRenderer {
    @Override
    public final Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                         boolean isSelected, boolean hasFocus, int row, int col) {
      final JPanel panel = new JPanel();
      if (value == null) return panel;
      panel.setLayout(new BorderLayout());

      if (value instanceof SlidingWindowStatsSummary) {
        final SlidingWindowStatsSummary stats = (SlidingWindowStatsSummary)value;
        final SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        final Location location = stats.getLocation();
        final String path = location.path;
        final String filename = PathUtil.getFileName(path);
        append(filename, attributes);

        final JBLabel label = new JBLabel(filename);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(Box.createHorizontalGlue());
        panel.add(label, BorderLayout.CENTER);

        final JBLabel lineLabel = new JBLabel(":" + location.line);
        panel.add(lineLabel, BorderLayout.EAST);

        if (isSelected) {
          label.setForeground(table.getSelectionForeground());
          lineLabel.setForeground(table.getSelectionForeground());
        }
      }

      clear();
      setPaintFocusBorder(hasFocus && table.getCellSelectionEnabled());
      acquireState(table, isSelected, hasFocus, row, col);
      getCellState().updateRenderer(this);

      if (isSelected) {
        panel.setBackground(table.getSelectionBackground());
      }

      return panel;
    }
  }

  private static class CountRenderer extends SimpleColoredRenderer implements TableCellRenderer {
    private final PerfMetric metric;
    private boolean idle = false;

    CountRenderer(PerfMetric metric) {
      this.metric = metric;
    }

    @Override
    public final Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                         boolean isSelected, boolean hasFocus, int row, int col) {
      final JPanel panel = new JPanel();
      if (value == null) return panel;
      panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

      if (value instanceof SlidingWindowStatsSummary) {
        final SlidingWindowStatsSummary stats = (SlidingWindowStatsSummary)value;
        final SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        final int count = stats.getValue(metric);

        final JBLabel label = new JBLabel(Integer.toString(count));
        if (metric.timeIntervalMetric) {
          label.setIcon(getIconForCount(idle ? 0 : count, false));
        }
        panel.add(Box.createHorizontalGlue());
        panel.add(label);
        panel.add(Box.createHorizontalStrut(8));

        if (isSelected) {
          label.setForeground(table.getSelectionForeground());
        }
      }

      clear();
      setPaintFocusBorder(hasFocus && table.getCellSelectionEnabled());
      acquireState(table, isSelected, hasFocus, row, col);
      getCellState().updateRenderer(this);

      if (isSelected) {
        panel.setBackground(table.getSelectionBackground());
      }

      return panel;
    }
  }

  static class WidgetNameColumnInfo extends ColumnInfo<DefaultMutableTreeNode, SlidingWindowStatsSummary> {
    private final WidgetNameRenderer renderer = new WidgetNameRenderer();

    public WidgetNameColumnInfo(String name) {
      super(name);
    }

    @Nullable
    @Override
    public SlidingWindowStatsSummary valueOf(DefaultMutableTreeNode node) {
      return (SlidingWindowStatsSummary)node.getUserObject();
    }

    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode item) {
      return renderer;
    }

    public void setIdle(boolean idle) {
      renderer.setIdle(idle);
    }
  }

  static class LocationColumnInfo extends ColumnInfo<DefaultMutableTreeNode, SlidingWindowStatsSummary> {
    private final TableCellRenderer renderer = new WidgetLocationRenderer();

    public LocationColumnInfo(String name) {
      super(name);
    }

    @Nullable
    @Override
    public SlidingWindowStatsSummary valueOf(DefaultMutableTreeNode node) {
      return (SlidingWindowStatsSummary)node.getUserObject();
    }

    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode item) {
      return renderer;
    }
  }

  static class CountColumnInfo extends ColumnInfo<DefaultMutableTreeNode, SlidingWindowStatsSummary> {
    private final CountRenderer defaultRenderer;
    private final PerfMetric metric;

    public CountColumnInfo(PerfMetric metric) {
      super(metric.name);
      this.metric = metric;
      defaultRenderer = new CountRenderer(metric);
    }

    @Nullable
    @Override
    public SlidingWindowStatsSummary valueOf(DefaultMutableTreeNode node) {
      return (SlidingWindowStatsSummary)node.getUserObject();
    }

    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode item) {
      return defaultRenderer;
    }
  }
}
