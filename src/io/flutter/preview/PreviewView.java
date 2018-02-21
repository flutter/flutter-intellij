/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.inspector.FlutterWidget;
import io.flutter.utils.CustomIconMaker;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

@com.intellij.openapi.components.State(
  name = "FlutterPreviewView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class PreviewView implements PersistentStateComponent<PreviewViewState>, Disposable {
  public static final String TOOL_WINDOW_ID = "Flutter Outline";

  public static final String FEEDBACK_URL = "https://goo.gl/forms/MbPU0kcPqBO6tunH3";

  private static final boolean SHOW_PREVIEW_AREA = false;

  @NotNull
  private final PreviewViewState state = new PreviewViewState();

  @NotNull
  private final Project project;

  @NotNull
  private final FlutterDartAnalysisServer flutterAnalysisServer;

  private SimpleToolWindowPanel windowPanel;
  private ActionToolbar windowToolbar;

  private final Map<String, AnAction> messageToActionMap = new HashMap<>();
  private final Map<AnAction, SourceChange> actionToChangeMap = new HashMap<>();

  private Splitter splitter;
  private JScrollPane scrollPane;
  private OutlineTree tree;
  private PreviewAreaPanel previewAreaPanel;

  private final Set<FlutterOutline> outlinesWithWidgets = Sets.newHashSet();
  private final Map<FlutterOutline, DefaultMutableTreeNode> outlineToNodeMap = Maps.newHashMap();

  private VirtualFile currentFile;
  private Editor currentEditor;
  private FlutterOutline currentOutline;

  private final FlutterOutlineListener outlineListener = new FlutterOutlineListener() {
    @Override
    public void outlineUpdated(@NotNull String filePath, @NotNull FlutterOutline outline) {
      if (currentFile != null && Objects.equals(currentFile.getPath(), filePath)) {
        ApplicationManager.getApplication().invokeLater(() -> updateOutline(outline));
      }
    }
  };

  private final CaretListener caretListener = new CaretListener() {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      final Caret caret = e.getCaret();
      if (caret != null) {
        ApplicationManager.getApplication().invokeLater(() -> applyEditorSelectionToTree(caret));
      }
    }

    @Override
    public void caretAdded(CaretEvent e) {
    }

    @Override
    public void caretRemoved(CaretEvent e) {
    }
  };

  private final TreeSelectionListener treeSelectionListener = this::handleTreeSelectionEvent;

  public PreviewView(@NotNull Project project) {
    this.project = project;
    flutterAnalysisServer = FlutterDartAnalysisServer.getInstance(project);

    // Show preview for the file selected when the view is being opened.
    final VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    if (selectedFiles.length != 0) {
      setSelectedFile(selectedFiles[0]);
    }

    final FileEditor[] selectedEditors = FileEditorManager.getInstance(project).getSelectedEditors();
    if (selectedEditors.length != 0) {
      setSelectedEditor(selectedEditors[0]);
    }

    // Listen for selecting files.
    final MessageBusConnection bus = project.getMessageBus().connect(project);
    bus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        setSelectedFile(event.getNewFile());
        setSelectedEditor(event.getNewEditor());
      }
    });
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public PreviewViewState getState() {
    return this.state;
  }

  @Override
  public void loadState(PreviewViewState state) {
    this.state.copyFrom(state);
  }

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final ContentManager contentManager = toolWindow.getContentManager();

    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();

    toolbarGroup.add(new QuickAssistAction("dart.assist.flutter.wrap.center", FlutterIcons.Center, "Center widget"));
    toolbarGroup.add(new QuickAssistAction("dart.assist.flutter.wrap.padding", FlutterIcons.Padding, "Add padding"));
    toolbarGroup.add(new QuickAssistAction("dart.assist.flutter.wrap.column", FlutterIcons.Column, "Wrap with Column"));
    toolbarGroup.add(new QuickAssistAction("dart.assist.flutter.wrap.row", FlutterIcons.Row, "Wrap with Row"));
    toolbarGroup.addSeparator();
    toolbarGroup.add(new QuickAssistAction("dart.assist.flutter.move.up", FlutterIcons.Up, "Move widget up"));
    toolbarGroup.add(new QuickAssistAction("dart.assist.flutter.move.down", FlutterIcons.Down, "Move widget down"));
    toolbarGroup.addSeparator();
    toolbarGroup.add(new QuickAssistAction("dart.assist.flutter.removeWidget", FlutterIcons.RemoveWidget, "Remove widget"));
    toolbarGroup.add(new ShowOnlyWidgetsAction(FlutterIcons.Filter, "Show only widgets"));

    final Content content = contentFactory.createContent(null, null, false);
    content.setCloseable(false);

    windowPanel = new SimpleToolWindowPanel(true, true);
    content.setComponent(windowPanel);

    windowToolbar = ActionManager.getInstance().createActionToolbar("PreviewViewToolbar", toolbarGroup, true);
    windowPanel.setToolbar(windowToolbar.getComponent());

    final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

    tree = new OutlineTree(rootNode);
    tree.setCellRenderer(new OutlineTreeCellRenderer());
    tree.expandAll();

    // Add collapse all, expand all, and feedback buttons.
    if (toolWindow instanceof ToolWindowEx) {
      final AnAction sendFeedbackAction = new AnAction("Send Feedback", "Send Feedback", FlutterIcons.Feedback) {
        @Override
        public void actionPerformed(AnActionEvent event) {
          BrowserUtil.browse(FEEDBACK_URL);
        }
      };

      final AnAction separator = new AnAction(AllIcons.General.Divider) {
        @Override
        public void actionPerformed(AnActionEvent event) {
        }
      };

      final TreeExpander expander = new DefaultTreeExpander(tree);
      final CommonActionsManager actions = CommonActionsManager.getInstance();

      final AnAction expandAllAction = actions.createExpandAllAction(expander, tree);
      expandAllAction.getTemplatePresentation().setIcon(AllIcons.General.ExpandAll);

      final AnAction collapseAllAction = actions.createCollapseAllAction(expander, tree);
      collapseAllAction.getTemplatePresentation().setIcon(AllIcons.General.CollapseAll);

      ((ToolWindowEx)toolWindow).setTitleActions(sendFeedbackAction, separator, expandAllAction, collapseAllAction);
    }

    new TreeSpeedSearch(tree) {
      @Override
      protected String getElementText(Object element) {
        final TreePath path = (TreePath)element;
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        final Object object = node.getUserObject();
        if (object instanceof OutlineObject) {
          return ((OutlineObject)object).getSpeedSearchString();
        }
        return null;
      }
    };

    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() > 1) {
          final TreePath selectionPath = tree.getSelectionPath();
          if (selectionPath != null) {
            selectPath(selectionPath, true);
          }
        }
      }
    });

    tree.addTreeSelectionListener(treeSelectionListener);

    scrollPane = ScrollPaneFactory.createScrollPane(tree);

    previewAreaPanel = new PreviewAreaPanel();

    splitter = new Splitter(true);
    splitter.setProportion(getState().getSplitterProportion());
    getState().addListener(e -> {
      final float newProportion = getState().getSplitterProportion();
      if (splitter.getProportion() != newProportion) {
        splitter.setProportion(newProportion);
      }
    });
    //noinspection Convert2Lambda
    splitter.addPropertyChangeListener("proportion", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        getState().setSplitterProportion(splitter.getProportion());
      }
    });
    splitter.setFirstComponent(scrollPane);
    windowPanel.setContent(splitter);

    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
  }

  private void handleTreeSelectionEvent(TreeSelectionEvent e) {
    final TreePath selectionPath = e.getNewLeadSelectionPath();
    if (selectionPath != null) {
      ApplicationManager.getApplication().invokeLater(() -> selectPath(selectionPath, false));
    }

    final List<FlutterOutline> selectedOutlines = getOutlinesSelectedInTree();
    updateActionsForOutlines(selectedOutlines);
  }

  private void selectPath(TreePath selectionPath, boolean focusEditor) {
    final FlutterOutline outline = getOutlineOfPath(selectionPath);
    if (outline == null) {
      return;
    }

    sendAnalyticEvent("jumpToSource");

    final int offset = outline.getDartElement() != null ? outline.getDartElement().getLocation().getOffset() : outline.getOffset();
    if (currentFile != null) {
      currentEditor.getCaretModel().removeCaretListener(caretListener);
      try {
        new OpenFileDescriptor(project, currentFile, offset).navigate(focusEditor);
      }
      finally {
        currentEditor.getCaretModel().addCaretListener(caretListener);
      }
    }

    if (SHOW_PREVIEW_AREA) {
      final Element buildMethodElement = getBuildMethodElement(selectionPath);
      previewAreaPanel.updatePreviewElement(getElementParentFor(buildMethodElement), buildMethodElement);
    }
  }

  private void updateActionsForOutlines(List<FlutterOutline> outlines) {
    synchronized (actionToChangeMap) {
      actionToChangeMap.clear();
    }

    final VirtualFile selectionFile = this.currentFile;
    if (selectionFile != null && !outlines.isEmpty()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        final FlutterOutline firstOutline = outlines.get(0);
        final FlutterOutline lastOutline = outlines.get(outlines.size() - 1);
        final int offset = firstOutline.getOffset();
        final int length = lastOutline.getOffset() + lastOutline.getLength() - offset;
        final List<SourceChange> changes = flutterAnalysisServer.edit_getAssists(selectionFile, offset, length);

        // If the current file or outline are different, ignore the changes.
        // We will eventually get new changes.
        final List<FlutterOutline> newOutlines = getOutlinesSelectedInTree();
        if (!Objects.equals(this.currentFile, selectionFile) || !outlines.equals(newOutlines)) {
          return;
        }

        // Associate changes with actions.
        // Actions will be enabled / disabled in background.
        for (SourceChange change : changes) {
          final AnAction action = messageToActionMap.get(change.getMessage());
          if (action != null) {
            actionToChangeMap.put(action, change);
          }
        }

        // Update actions immediately.
        if (windowToolbar != null) {
          ApplicationManager.getApplication().invokeLater(() -> windowToolbar.updateActionsImmediately());
        }
      });
    }
  }

  // TODO: Add parent relationship info to FlutterOutline instead of this O(n^2) traversal.
  private Element getElementParentFor(@Nullable Element element) {
    if (element == null) {
      return null;
    }

    for (FlutterOutline outline : outlineToNodeMap.keySet()) {
      final List<FlutterOutline> children = outline.getChildren();
      if (children != null) {
        for (FlutterOutline child : children) {
          if (child.getDartElement() == element) {
            return outline.getDartElement();
          }
        }
      }
    }

    return null;
  }

  private Element getBuildMethodElement(TreePath path) {
    for (Object n : path.getPath()) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)n;
      final OutlineObject outlineElement = (OutlineObject)node.getUserObject();
      if (outlineElement == null) {
        continue;
      }

      final FlutterOutline flutterOutline = outlineElement.outline;
      final Element element = flutterOutline.getDartElement();

      if (element != null && ModelUtils.isBuildMethod(element)) {
        return element;
      }
    }

    return null;
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)tree.getModel();
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTreeModel().getRoot();
  }

  private void updateOutline(@NotNull FlutterOutline outline) {
    currentOutline = outline;

    final DefaultMutableTreeNode rootNode = getRootNode();
    rootNode.removeAllChildren();

    outlinesWithWidgets.clear();
    outlineToNodeMap.clear();
    if (outline.getChildren() != null) {
      computeOutlinesWithWidgets(outline);
      updateOutlineImpl(rootNode, outline.getChildren());
    }

    getTreeModel().reload(rootNode);
    tree.expandAll();

    if (currentEditor != null) {
      final Caret caret = currentEditor.getCaretModel().getPrimaryCaret();
      applyEditorSelectionToTree(caret);
    }

    if (SHOW_PREVIEW_AREA) {
      if (ModelUtils.containsBuildMethod(outline)) {
        splitter.setSecondComponent(previewAreaPanel);

        final Element buildMethodElement = getBuildMethodElement(tree.getSelectionPath());
        previewAreaPanel.updatePreviewElement(getElementParentFor(buildMethodElement), buildMethodElement);
      }
      else {
        splitter.setSecondComponent(null);
      }
    }
  }

  private boolean computeOutlinesWithWidgets(FlutterOutline outline) {
    boolean hasWidget = false;
    if (outline.getDartElement() == null) {
      outlinesWithWidgets.add(outline);
      hasWidget = true;
    }

    final List<FlutterOutline> children = outline.getChildren();
    if (children != null) {
      for (final FlutterOutline child : children) {
        if (computeOutlinesWithWidgets(child)) {
          outlinesWithWidgets.add(outline);
          hasWidget = true;
        }
      }
    }
    return hasWidget;
  }

  private void updateOutlineImpl(@NotNull DefaultMutableTreeNode parent, @NotNull List<FlutterOutline> outlines) {
    int index = 0;
    for (final FlutterOutline outline : outlines) {
      if (getState().getShowOnlyWidgets() && !outlinesWithWidgets.contains(outline)) {
        continue;
      }

      final OutlineObject object = new OutlineObject(outline);
      final DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);
      outlineToNodeMap.put(outline, node);

      getTreeModel().insertNodeInto(node, parent, index++);

      if (outline.getChildren() != null) {
        updateOutlineImpl(node, outline.getChildren());
      }
    }
  }

  @NotNull
  private List<FlutterOutline> getOutlinesSelectedInTree() {
    final List<FlutterOutline> selectedOutlines = new ArrayList<>();
    final DefaultMutableTreeNode[] selectedNodes = tree.getSelectedNodes(DefaultMutableTreeNode.class, null);
    for (DefaultMutableTreeNode selectedNode : selectedNodes) {
      final FlutterOutline outline = getOutlineOfNode(selectedNode);
      selectedOutlines.add(outline);
    }
    return selectedOutlines;
  }

  static private FlutterOutline getOutlineOfNode(DefaultMutableTreeNode node) {
    final OutlineObject object = (OutlineObject)node.getUserObject();
    return object.outline;
  }

  @Nullable
  private FlutterOutline getOutlineOfPath(@Nullable TreePath path) {
    if (path == null) {
      return null;
    }

    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    return getOutlineOfNode(node);
  }

  private FlutterOutline findOutlineAtOffset(FlutterOutline outline, int offset) {
    if (outline == null) {
      return null;
    }
    if (outline.getOffset() <= offset && offset <= outline.getOffset() + outline.getLength()) {
      if (outline.getChildren() != null) {
        for (FlutterOutline child : outline.getChildren()) {
          final FlutterOutline foundChild = findOutlineAtOffset(child, offset);
          if (foundChild != null) {
            return foundChild;
          }
        }
      }
      return outline;
    }
    return null;
  }

  private void addOutlinesCoveredByRange(List<FlutterOutline> covered, int start, int end, @Nullable FlutterOutline outline) {
    if (outline == null) {
      return;
    }

    final int outlineStart = outline.getOffset();
    final int outlineEnd = outlineStart + outline.getLength();
    // The outline ends before, or starts after the selection.
    if (outlineEnd < start || outlineStart > end) {
      return;
    }
    // The outline is covered by the selection.
    if (outlineStart >= start && outlineEnd <= end) {
      covered.add(outline);
      return;
    }
    // The outline covers the selection.
    if (outlineStart <= start && end <= outlineEnd) {
      if (outline.getChildren() != null) {
        for (FlutterOutline child : outline.getChildren()) {
          addOutlinesCoveredByRange(covered, start, end, child);
        }
      }
    }
  }

  private void setSelectedFile(VirtualFile newFile) {
    if (currentFile != null) {
      flutterAnalysisServer.removeOutlineListener(currentFile.getPath(), outlineListener);
      currentFile = null;
    }

    // Show the toolbar if the new file is a Dart file, or hide otherwise.
    if (windowPanel != null) {
      if (newFile != null && FlutterUtils.isDartFile(newFile)) {
        windowPanel.setToolbar(windowToolbar.getComponent());
      }
      else if (windowPanel.isToolbarVisible()) {
        windowPanel.setToolbar(null);
      }
    }

    // If the tree is already created, clear it now, until the outline for the new file is received.
    if (tree != null) {
      final DefaultMutableTreeNode rootNode = getRootNode();
      rootNode.removeAllChildren();
      getTreeModel().reload(rootNode);
    }

    // Subscribe for the outline for the new file.
    if (newFile != null) {
      currentFile = newFile;
      flutterAnalysisServer.addOutlineListener(currentFile.getPath(), outlineListener);
    }
  }

  private void setSelectedEditor(FileEditor newEditor) {
    if (currentEditor != null) {
      currentEditor.getCaretModel().removeCaretListener(caretListener);
    }
    if (newEditor instanceof TextEditor) {
      currentEditor = ((TextEditor)newEditor).getEditor();
      currentEditor.getCaretModel().addCaretListener(caretListener);
    }
  }

  private void applyEditorSelectionToTree(Caret caret) {
    final List<FlutterOutline> selectedOutlines = new ArrayList<>();

    // Try to find outlines covered by the selection.
    addOutlinesCoveredByRange(selectedOutlines, caret.getSelectionStart(), caret.getSelectionEnd(), currentOutline);

    // If no covered outlines, try to find the outline under the caret.
    if (selectedOutlines.isEmpty()) {
      final FlutterOutline outline = findOutlineAtOffset(currentOutline, caret.getOffset());
      if (outline != null) {
        selectedOutlines.add(outline);
      }
    }

    updateActionsForOutlines(selectedOutlines);
    applyOutlinesSelectionToTree(selectedOutlines);
  }

  private void applyOutlinesSelectionToTree(List<FlutterOutline> outlines) {
    final List<TreePath> selectedPaths = new ArrayList<>();
    TreeNode[] lastNodePath = null;
    TreePath lastTreePath = null;
    for (FlutterOutline outline : outlines) {
      final DefaultMutableTreeNode selectedNode = outlineToNodeMap.get(outline);
      if (selectedNode != null) {
        lastNodePath = selectedNode.getPath();
        lastTreePath = new TreePath(lastNodePath);
        selectedPaths.add(lastTreePath);
      }
    }

    if (lastNodePath != null) {
      // Ensure that all parent nodes are expected.
      tree.scrollPathToVisible(lastTreePath);

      // Ensure that the top-level declaration (class) is on the top of the tree.
      if (lastNodePath.length >= 2) {
        scrollTreeToNodeOnTop(lastNodePath[1]);
      }

      // Ensure that the selected node is still visible, even if the top-level declaration is long.
      tree.scrollPathToVisible(lastTreePath);
    }

    // Now actually select the node.
    tree.removeTreeSelectionListener(treeSelectionListener);
    tree.setSelectionPaths(selectedPaths.toArray(new TreePath[selectedPaths.size()]));
    tree.addTreeSelectionListener(treeSelectionListener);

    // JTree attempts to show as much of the node as possible, so scrolls horizonally.
    // But we actually need to see the whole hierarchy, so we scroll back to zero.
    scrollPane.getHorizontalScrollBar().setValue(0);
  }

  private void scrollTreeToNodeOnTop(TreeNode node) {
    if (node instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode defaultNode = (DefaultMutableTreeNode)node;
      final Rectangle bounds = tree.getPathBounds(new TreePath(defaultNode.getPath()));
      // Set the height to the visible tree height to force the node to top.
      if (bounds != null) {
        bounds.height = tree.getVisibleRect().height;
        tree.scrollRectToVisible(bounds);
      }
    }
  }

  private void sendAnalyticEvent(@NotNull String name) {
    FlutterInitializer.getAnalytics().sendEvent("preview", name);
  }

  private class QuickAssistAction extends AnAction {
    private final String id;

    QuickAssistAction(@NotNull String id, Icon icon, String assistMessage) {
      super(assistMessage, null, icon);
      this.id = id;
      messageToActionMap.put(assistMessage, this);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      sendAnalyticEvent(id);
      final SourceChange change;
      synchronized (actionToChangeMap) {
        change = actionToChangeMap.get(this);
        actionToChangeMap.clear();
      }
      if (change != null) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            AssistUtils.applySourceChange(project, change, false);
          }
          catch (DartSourceEditException exception) {
            FlutterMessages.showError("Error applying change", exception.getMessage());
          }
        });
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean hasChange = actionToChangeMap.containsKey(this);
      e.getPresentation().setEnabled(hasChange);
    }
  }

  private class ShowOnlyWidgetsAction extends AnAction implements Toggleable, RightAlignedToolbarAction {
    ShowOnlyWidgetsAction(@NotNull Icon icon, @NotNull String text) {
      super(text, null, icon);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      getState().setShowOnlyWidgets(!getState().getShowOnlyWidgets());
      if (currentOutline != null) {
        updateOutline(currentOutline);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.putClientProperty(SELECTED_PROPERTY, getState().getShowOnlyWidgets());
      presentation.setEnabled(currentOutline != null);
    }
  }
}

class OutlineTree extends Tree {
  OutlineTree(DefaultMutableTreeNode model) {
    super(model);

    setRootVisible(false);
    setToggleClickCount(0);
  }

  void expandAll() {
    for (int row = 0; row < getRowCount(); row++) {
      expandRow(row);
    }
  }
}

class OutlineObject {
  private static final CustomIconMaker iconMaker = new CustomIconMaker();
  private static final Map<Icon, LayeredIcon> flutterDecoratedIcons = new HashMap<>();

  final FlutterOutline outline;
  private Icon icon;

  OutlineObject(FlutterOutline outline) {
    this.outline = outline;
  }

  Icon getIcon() {
    if (outline.getKind().equals(FlutterOutlineKind.DART_ELEMENT)) {
      return null;
    }

    if (icon == null) {
      final String className = outline.getClassName();
      final FlutterWidget widget = FlutterWidget.getCatalog().getWidget(className);
      if (widget != null) {
        icon = widget.getIcon();
      }
      if (icon == null) {
        icon = iconMaker.fromWidgetName(className);
      }
    }

    return icon;
  }

  Icon getFlutterDecoratedIcon() {
    final Icon icon = getIcon();
    if (icon == null) return null;

    LayeredIcon decorated = flutterDecoratedIcons.get(icon);
    if (decorated == null) {
      final Icon prefix = FlutterIcons.Flutter;

      decorated = new LayeredIcon(2);
      decorated.setIcon(prefix, 0, 0, 0);
      decorated.setIcon(icon, 1, prefix.getIconWidth(), 0);

      flutterDecoratedIcons.put(icon, decorated);
    }
    return decorated;
  }

  /**
   * Return the string that is suitable for speed search. It has every name part separted so that we search only inside individual name
   * parts, but not in their accidential concatenation.
   */
  @NotNull
  String getSpeedSearchString() {
    final StringBuilder builder = new StringBuilder();
    final Element dartElement = outline.getDartElement();
    if (dartElement != null) {
      builder.append(dartElement.getName());
    }
    else {
      builder.append(outline.getClassName());
    }
    if (outline.getVariableName() != null) {
      builder.append('|');
      builder.append(outline.getVariableName());
    }

    final List<FlutterOutlineAttribute> attributes = outline.getAttributes();
    if (attributes != null) {
      for (FlutterOutlineAttribute attribute : attributes) {
        builder.append(attribute.getName());
        builder.append(':');
        builder.append(attribute.getLabel());
      }
    }

    return builder.toString();
  }
}

class OutlineTreeCellRenderer extends ColoredTreeCellRenderer {
  private JTree tree;
  private boolean selected;

  public void customizeCellRenderer(
    @NotNull final JTree tree,
    final Object value,
    final boolean selected,
    final boolean expanded,
    final boolean leaf,
    final int row,
    final boolean hasFocus
  ) {
    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    if (!(userObject instanceof OutlineObject)) {
      return;
    }
    final OutlineObject node = (OutlineObject)userObject;
    final FlutterOutline outline = node.outline;

    this.tree = tree;
    this.selected = selected;

    // Render a Dart element.
    final Element dartElement = outline.getDartElement();
    if (dartElement != null) {
      final Icon icon = DartElementPresentationUtil.getIcon(dartElement);
      setIcon(icon);

      final boolean renderInBold = hasWidgetChild(outline) && ModelUtils.isBuildMethod(dartElement);
      DartElementPresentationUtil.renderElement(dartElement, this, renderInBold);
      return;
    }

    // Render the widget icon.
    final Icon icon = node.getFlutterDecoratedIcon();
    if (icon != null) {
      setIcon(icon);
    }

    // Render the widget class.
    appendSearch(outline.getClassName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

    // Render the variable.
    if (outline.getVariableName() != null) {
      append(" ");
      appendSearch(outline.getVariableName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    // Render a generic label.
    if (outline.getKind().equals(FlutterOutlineKind.GENERIC) && outline.getLabel() != null) {
      append(" ");
      final String label = outline.getLabel();
      appendSearch(label, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    // Append all attributes.
    final List<FlutterOutlineAttribute> attributes = outline.getAttributes();
    if (attributes != null) {
      if (attributes.size() == 1 && isAttributeElidable(attributes.get(0).getName())) {
        final FlutterOutlineAttribute attribute = attributes.get(0);
        append(" ");
        appendSearch(attribute.getLabel(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else {
        for (int i = 0; i < attributes.size(); i++) {
          final FlutterOutlineAttribute attribute = attributes.get(i);

          if (i > 0) {
            append(",");
          }
          append(" ");

          if (!StringUtil.equals("data", attribute.getName())) {
            appendSearch(attribute.getName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append(": ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }

          appendSearch(attribute.getLabel(), SimpleTextAttributes.GRAYED_ATTRIBUTES);

          // TODO(scheglov): custom display for units, colors, iterables, and icons?
        }
      }
    }
  }

  private boolean hasWidgetChild(FlutterOutline outline) {
    if (outline.getChildren() == null) {
      return false;
    }

    for (FlutterOutline child : outline.getChildren()) {
      if (child.getDartElement() == null) {
        return true;
      }
    }

    return false;
  }

  private static boolean isAttributeElidable(String name) {
    return StringUtil.equals("text", name) || StringUtil.equals("icon", name);
  }

  void appendSearch(@NotNull String text, @NotNull SimpleTextAttributes attributes) {
    SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, text, attributes, selected, this);
  }
}
