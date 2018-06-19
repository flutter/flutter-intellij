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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;

public class FlutterLogView extends JPanel implements ConsoleView, DataProvider, FlutterLog.Listener {

  private class EntryModel implements FlutterLogTree.EntryModel {
    boolean showColors;

    @Override
    public SimpleTextAttributes style(@Nullable FlutterLogEntry entry, int attributes) {
      if (showColors && entry != null) {
        final FlutterLog.Level level = FlutterLog.Level.forValue(entry.getLevel());
        if (level != null) {
          switch (level) {
            case FINER:
              return SimpleTextAttributes.GRAY_ATTRIBUTES;
            case WARNING:
              return ORANGE_ATTRIBUTES;
            case SEVERE:
              return SimpleTextAttributes.ERROR_ATTRIBUTES;
          }
        }
      }
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  }

  class ConfigureAction extends AnAction implements RightAlignedToolbarAction {
    private final DefaultActionGroup actionGroup;

    public ConfigureAction() {
      super("Configure", null, AllIcons.General.Gear);

      actionGroup = createPopupActionGroup();
    }

    ActionButton getActionButton() {
      final Presentation presentation = getTemplatePresentation().clone();
      final ActionButton actionButton = new ActionButton(
        this,
        presentation,
        ActionPlaces.UNKNOWN,
        ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      );
      presentation.putClientProperty("button", actionButton);
      return actionButton;
    }

    @Override
    public final void update(AnActionEvent e) {
      e.getPresentation().setEnabled(app.isSessionActive());
    }

    @Override
    @SuppressWarnings("Duplicates")
    public void actionPerformed(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      JComponent component = (JComponent)presentation.getClientProperty("button");
      if (component == null && e.getInputEvent().getSource() instanceof JComponent) {
        component = (JComponent)e.getInputEvent().getSource();
      }
      if (component == null) {
        return;
      }
      final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(
        ActionPlaces.UNKNOWN,
        actionGroup);
      popupMenu.getComponent().show(component, component.getWidth(), 0);
    }

    private DefaultActionGroup createPopupActionGroup() {
      final DefaultActionGroup group = new DefaultActionGroup();
      group.add(new ShowTimeStampsAction());
      group.add(new ShowSequenceNumbersAction());
      group.add(new ShowLevelAction());
      group.add(new Separator());
      group.add(new ShowColorsAction());
      return group;
    }
  }

  private class ShowTimeStampsAction extends ToggleAction {

    ShowTimeStampsAction() {
      super("Show timestamps");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return logModel.getShowTimestamps();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      logModel.setShowTimestamps(state);
      logModel.update();
    }
  }

  private class ShowSequenceNumbersAction extends ToggleAction {

    ShowSequenceNumbersAction() {
      super("Show sequence numbers");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return logModel.getShowSequenceNumbers();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      logModel.setShowSequenceNumbers(state);
      logModel.update();
    }
  }

  private class ShowLevelAction extends ToggleAction {

    ShowLevelAction() {
      super("Show log levels");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return logModel.getShowLogLevels();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      logModel.setShowLogLevels(state);
      logModel.update();
    }
  }

  private class ShowColorsAction extends ToggleAction {

    ShowColorsAction() {
      super("Color entries");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return entryModel.showColors;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      entryModel.showColors = state;
      logModel.update();
    }
  }

  private class ClearLogAction extends AnAction {
    ClearLogAction() {
      super("Clear All", "Clear the log", AllIcons.Actions.GC);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ApplicationManager.getApplication().invokeLater(() -> {
        logModel.clearEntries();
        logModel.update();
      });
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(logModel.getRoot().getChildCount() > 0);
    }
  }

  private class ScrollToEndAction extends ToggleAction {
    ScrollToEndAction() {
      super("Scroll to the end", "Scroll to the end", AllIcons.RunConfigurations.Scroll_down);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return logModel.autoScrollToEnd;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      ApplicationManager.getApplication().invokeLater(() -> {
        logModel.autoScrollToEnd = state;
        logModel.scrollToEnd();
      });
    }
  }

  private class CopyToClipboardAction extends AnAction {
    CopyToClipboardAction() {
      super("Copy as Text", "Copy selected entries to the clipboard", AllIcons.Actions.Copy);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ApplicationManager.getApplication().invokeLater(() -> {
        final int[] rows = logTree.getSelectedRows();
        final FlutterLogTree.LogRootTreeNode root = logModel.getRoot();
        final TreePath[] paths = logTree.getTree().getSelectionPaths();
        if (paths != null) {
          final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
          final StringBuilder sb = new StringBuilder();
          for (final TreePath path: paths) {
            final Object pathComponent = path.getLastPathComponent();
            if (pathComponent instanceof FlutterLogTree.FlutterEventNode) {
              ((FlutterLogTree.FlutterEventNode)pathComponent).describeTo(sb);
            }
          }

          final StringSelection selection = new StringSelection(sb.toString());
          clipboard.setContents(selection, selection);
        }
      });
    }
  }

  private class FilterStatusLabel extends AnAction implements CustomComponentAction, RightAlignedToolbarAction,
                                                              FlutterLogTree.EventCountListener {

    JBLabel label;
    JPanel panel;

    @Override
    public void actionPerformed(AnActionEvent e) {
      // None.  Just an info display.
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      panel = new JPanel();

      label = new JBLabel();
      label.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      label.setForeground(UIUtil.getInactiveTextColor());
      label.setBorder(JBUI.Borders.emptyRight(10));
      panel.add(label);

      logTree.addListener(this, FlutterLogView.this);

      return panel;
    }

    @Override
    public void updated(int filtered, int total) {
      if (label != null && label.isVisible()) {

        final StringBuilder sb = new StringBuilder();
        sb.append(total).append(" event");
        if (total != 1) {
          sb.append("s");
        }
        if (filtered > 0) {
          sb.append(" (").append(filtered).append(" filtered)");
        }

        label.setText(sb.toString());
        SwingUtilities.invokeLater(panel::repaint);
      }
    }


    String countString(int count) {
      if (count > 1000) {
        return "1000+";
      }
      return Integer.toString(count);
    }
  }

  @NotNull final FlutterApp app;
  @NotNull
  private final SimpleTextAttributes ORANGE_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.orange);
  // TODO(pq): make user configurable.
  private final EntryModel entryModel = new EntryModel();
  private final SimpleToolWindowPanel toolWindowPanel;
  @NotNull
  private final FlutterLogTree.TreeModel logModel;
  private final FlutterLogTree logTree;
  @NotNull
  private final FlutterLogFilterPanel filterPanel = new FlutterLogFilterPanel(param -> doFilter());
  private SimpleTreeBuilder builder;

  public FlutterLogView(@NotNull FlutterApp app) {
    this.app = app;

    final FlutterLog flutterLog = app.getFlutterLog();
    flutterLog.addListener(this, this);

    final Content content = ContentFactory.SERVICE.getInstance().createContent(null, null, false);
    content.setCloseable(false);

    toolWindowPanel = new SimpleToolWindowPanel(true, true);
    content.setComponent(toolWindowPanel);

    final JPanel toolbar = createToolbar();
    toolWindowPanel.setToolbar(toolbar);

    logTree = new FlutterLogTree(app, entryModel, this);
    logModel = logTree.getLogTreeModel();

    // TODO(pq): add speed search
    //new TreeTableSpeedSearch(logTree).setComparator(new SpeedSearchComparator(false));

    logTree.setTableHeader(null);
    logTree.setRootVisible(false);

    logTree.setExpandableItemsEnabled(true);
    logTree.getTree().setScrollsOnExpand(true);

    logTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(
          ActionPlaces.UNKNOWN,
          getTreePopupActions());
        popupMenu.getComponent().show(comp, x, y);
      }

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

      // TODO(pq): add keybindings
    });

    // Set bounds.
    // TODO(pq): consider re-sizing dynamically, as needed.
    logTree.getColumn("Time").setMinWidth(100);
    logTree.getColumn("Time").setMaxWidth(100);
    logTree.getColumn("Sequence").setMinWidth(50);
    logTree.getColumn("Sequence").setMaxWidth(50);
    logTree.getColumn("Level").setMinWidth(70);
    logTree.getColumn("Level").setMaxWidth(70);
    logTree.getColumn("Category").setMinWidth(110);
    logTree.getColumn("Category").setMaxWidth(110);
    logTree.getColumn("Message").setMinWidth(100);

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(logTree,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    logModel.setScrollPane(pane);
    toolWindowPanel.setContent(pane);
  }

  private ActionGroup getTreePopupActions() {
    return new ActionGroup() {
      @NotNull
      @Override
      public AnAction[] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[]{
          new CopyToClipboardAction(),
          new Separator(),
          new ClearLogAction()
        };
      }
    };
  }

  private void doFilter() {
    final FlutterLogFilterPanel.FilterParam param = filterPanel.getCurrentFilterParam();
    final String text = param.getExpression();
    final FlutterLogTree.EntryFilter filter = new FlutterLogTree.EntryFilter(param);
    ApplicationManager.getApplication().invokeLater(() -> logTree.setFilter(filter));
  }

  @NotNull
  private FlutterLog getFlutterLog() {
    return app.getFlutterLog();
  }

  @NotNull
  private JPanel createToolbar() {
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(new FilterStatusLabel());
    toolbarGroup.add(new ConfigureAction());

    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("FlutterLogViewToolbar", toolbarGroup, true);
    actionToolbar.setMiniMode(false);
    final JPanel toolbar = new JPanel();
    toolbar.setLayout(new BorderLayout());
    toolbar.add(filterPanel.getRoot(), BorderLayout.WEST);
    toolbar.add(actionToolbar.getComponent(), BorderLayout.EAST);
    return toolbar;
  }

  @Override
  public void onEvent(@NotNull FlutterLogEntry entry) {
    logTree.reload();
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
    return logTree;
  }
}
