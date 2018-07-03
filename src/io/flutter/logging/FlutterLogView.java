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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.logging.FlutterLog.Level;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.openapi.editor.markup.EffectType.*;
import static io.flutter.logging.FlutterLogConstants.LogColumns.*;

public class FlutterLogView extends JPanel implements ConsoleView, DataProvider, FlutterLog.Listener {
  @NotNull
  private static final Logger LOG = Logger.getInstance(FlutterLogView.class);

  @NotNull
  private static final Map<FlutterLog.Level, TextAttributesKey> LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP;
  @NotNull
  private static final Map<EffectType, Integer> EFFECT_TYPE_TEXT_STYLE_MAP;
  @NotNull
  private static final SimpleTextAttributes REGULAR_ATTRIBUTES = SimpleTextAttributes.GRAY_ATTRIBUTES;

  static {
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP = new HashMap<>();
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.NONE, FlutterLogConstants.NONE_OUTPUT_KEY);
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.FINEST, FlutterLogConstants.FINEST_OUTPUT_KEY);
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.FINER, FlutterLogConstants.FINER_OUTPUT_KEY);
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.FINE, FlutterLogConstants.FINE_OUTPUT_KEY);
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.CONFIG, FlutterLogConstants.CONFIG_OUTPUT_KEY);
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.INFO, FlutterLogConstants.INFO_OUTPUT_KEY);
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.WARNING, FlutterLogConstants.WARNING_OUTPUT_KEY);
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.SEVERE, FlutterLogConstants.SEVERE_OUTPUT_KEY);
    LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.put(Level.SHOUT, FlutterLogConstants.SHOUT_OUTPUT_KEY);

    EFFECT_TYPE_TEXT_STYLE_MAP = new HashMap<>();
    EFFECT_TYPE_TEXT_STYLE_MAP.put(LINE_UNDERSCORE, SimpleTextAttributes.STYLE_UNDERLINE);
    EFFECT_TYPE_TEXT_STYLE_MAP.put(WAVE_UNDERSCORE, SimpleTextAttributes.STYLE_UNDERLINE | SimpleTextAttributes.STYLE_WAVED);
    EFFECT_TYPE_TEXT_STYLE_MAP.put(BOLD_LINE_UNDERSCORE, SimpleTextAttributes.STYLE_UNDERLINE | SimpleTextAttributes.STYLE_BOLD);
    EFFECT_TYPE_TEXT_STYLE_MAP.put(STRIKEOUT, SimpleTextAttributes.STYLE_STRIKEOUT);
    EFFECT_TYPE_TEXT_STYLE_MAP.put(BOLD_DOTTED_LINE, SimpleTextAttributes.STYLE_BOLD_DOTTED_LINE);
    EFFECT_TYPE_TEXT_STYLE_MAP.put(SEARCH_MATCH, SimpleTextAttributes.STYLE_SEARCH_MATCH);

    // TODO(quangson91): Figure out how to map style for these settings.
    EFFECT_TYPE_TEXT_STYLE_MAP.put(BOXED, null);
    EFFECT_TYPE_TEXT_STYLE_MAP.put(ROUNDED_BOX, null);
  }

  @NotNull
  private final Map<Level, SimpleTextAttributes> textAttributesByLogLevelCache = new ConcurrentHashMap<>();

  private class EntryModel implements FlutterLogTree.EntryModel {
    boolean showColors;

    @Override
    public SimpleTextAttributes style(@Nullable FlutterLogEntry entry, int attributes) {
      if (showColors && entry != null) {
        final FlutterLog.Level level = FlutterLog.Level.forValue(entry.getLevel());
        return getTextAttributesByLogLevel(level);
      }
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    @NotNull
    private SimpleTextAttributes getTextAttributesByLogLevel(@NotNull Level level) {
      if (textAttributesByLogLevelCache.containsKey(level)) {
        return textAttributesByLogLevelCache.get(level);
      }
      return REGULAR_ATTRIBUTES;
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
      group.add(new ShowCategoryAction());
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
      return flutterLogPreferences.isShowTimestamp();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      flutterLogPreferences.setShowTimestamp(state);
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
      return flutterLogPreferences.isShowSequenceNumbers();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      flutterLogPreferences.setShowSequenceNumbers(state);
      logModel.setShowSequenceNumbers(state);
      logModel.update();
    }
  }

  private class ShowCategoryAction extends ToggleAction {

    ShowCategoryAction() {
      super("Show categories");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return flutterLogPreferences.isShowLogCategory();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      flutterLogPreferences.setShowLogCategory(state);
      logModel.setShowCategories(state);
      logModel.update();
    }
  }

  private class ShowLevelAction extends ToggleAction {

    ShowLevelAction() {
      super("Show log levels");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return flutterLogPreferences.isShowLogLevel();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      flutterLogPreferences.setShowLogLevel(state);
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
      return flutterLogPreferences.isShowColor();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      flutterLogPreferences.setShowColor(state);
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
      ApplicationManager.getApplication().invokeLater(logTree::clearEntries);
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
          for (final TreePath path : paths) {
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

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation) {
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
  // TODO(pq): make user configurable.
  private final EntryModel entryModel = new EntryModel();
  private final SimpleToolWindowPanel toolWindowPanel;
  @NotNull
  private final FlutterLogTree.TreeModel logModel;
  private final FlutterLogTree logTree;
  @NotNull
  private final FlutterLogFilterPanel filterPanel;
  private SimpleTreeBuilder builder;
  @NotNull
  private final FlutterLogPreferences flutterLogPreferences;

  public FlutterLogView(@NotNull FlutterApp app) {
    this.app = app;
    flutterLogPreferences = FlutterLogPreferences.getInstance(app.getProject());
    filterPanel = createFilterPanel();

    computeTextAttributesByLogLevelCache();
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(EditorColorsManager.TOPIC, scheme -> computeTextAttributesByLogLevelCache());
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
    logModel.updateFromPreferences(flutterLogPreferences);
    entryModel.showColors = flutterLogPreferences.isShowColor();

    // TODO(pq): add speed search
    //new TreeTableSpeedSearch(logTree).setComparator(new SpeedSearchComparator(false));

    logTree.setTableHeader(null);
    logTree.setRootVisible(false);

    logTree.setExpandableItemsEnabled(true);
    logTree.getTree().setScrollsOnExpand(true);

    final PopupHandler popupHandler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(
          ActionPlaces.UNKNOWN,
          getTreePopupActions());
        popupMenu.getComponent().show(comp, x, y);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        final Cursor cursor = getTagForPosition(e) instanceof OpenFileHyperlinkInfo
                              ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                              : Cursor.getDefaultCursor();
        logTree.setCursor(cursor);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        final Object tag = getTagForPosition(e);
        // TODO(pq): consider generalizing to a runnable and wrapping the hyperlinkinfo
        if (tag instanceof OpenFileHyperlinkInfo) {
          ((OpenFileHyperlinkInfo)tag).navigate(app.getProject());
        }
      }

      private Object getTagForPosition(MouseEvent e) {
        final JTable table = (JTable)e.getSource();
        final int row = table.rowAtPoint(e.getPoint());
        final int column = table.columnAtPoint(e.getPoint());
        if (row == -1 || column == -1) return null;
        final TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
        if (cellRenderer instanceof ColoredTableCellRenderer) {
          final ColoredTableCellRenderer renderer = (ColoredTableCellRenderer)cellRenderer;
          final Rectangle rc = table.getCellRect(row, column, false);
          return renderer.getFragmentTagAt(e.getX() - rc.x);
        }
        return null;
      }
    };
    logTree.addMouseListener(popupHandler);
    logTree.addMouseMotionListener(popupHandler);

    // Set bounds.
    // TODO(pq): consider re-sizing dynamically, as needed.
    logTree.getColumn(TIME).setMinWidth(100);
    logTree.getColumn(TIME).setMaxWidth(100);
    logTree.getColumn(SEQUENCE).setMinWidth(50);
    logTree.getColumn(SEQUENCE).setMaxWidth(50);
    logTree.getColumn(LEVEL).setMinWidth(70);
    logTree.getColumn(LEVEL).setMaxWidth(70);
    logTree.getColumn(CATEGORY).setMinWidth(110);
    logTree.getColumn(CATEGORY).setMaxWidth(110);
    logTree.getColumn(MESSAGE).setMinWidth(100);

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(logTree,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    logModel.setScrollPane(pane);
    toolWindowPanel.setContent(pane);
  }

  @NotNull
  private FlutterLogFilterPanel createFilterPanel() {
    final FlutterLogFilterPanel panel = new FlutterLogFilterPanel();
    panel.updateFromPreferences(flutterLogPreferences);
    panel.setOnFilterListener(param -> doFilter());
    return panel;
  }

  private void computeTextAttributesByLogLevelCache() {
    final EditorColorsScheme globalEditorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    textAttributesByLogLevelCache.clear();
    for (Level level : FlutterLog.Level.values()) {
      try {
        final TextAttributesKey key = LOG_LEVEL_TEXT_ATTRIBUTES_KEY_MAP.get(level);
        final TextAttributes attributes = globalEditorColorsScheme.getAttributes(key);
        int fontType = attributes.getFontType();
        final Color effectColor = attributes.getEffectColor();
        final Integer textStyle = EFFECT_TYPE_TEXT_STYLE_MAP.get(attributes.getEffectType());
        // TextStyle can exist even when unchecked in settings page.
        // only effectColor is null when setting effect is unchecked in setting page.
        // So, we have to check that both effectColor & textStyle are not null.
        if (effectColor != null && textStyle != null) {
          fontType = fontType | textStyle;
        }

        final SimpleTextAttributes textAttributes = new SimpleTextAttributes(
          attributes.getBackgroundColor(),
          attributes.getForegroundColor(),
          effectColor,
          fontType
        );

        textAttributesByLogLevelCache.put(level, textAttributes);
      }
      catch (Exception e) {
        // Should never go here.
        LOG.warn("Error when get text attributes by log level", e);
      }
    }
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
    flutterLogPreferences.setToolWindowRegex(param.isRegex());
    flutterLogPreferences.setToolWindowMatchCase(param.isMatchCase());
    flutterLogPreferences.setToolWindowLogLevel(param.getLogLevel().value);
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
