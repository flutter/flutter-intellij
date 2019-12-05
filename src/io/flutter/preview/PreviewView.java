/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
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
import com.intellij.openapi.util.io.FileUtil;
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
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.editor.PropertyEditorPanel;
import io.flutter.inspector.*;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.CustomIconMaker;
import io.flutter.utils.EventStream;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;

@com.intellij.openapi.components.State(
  name = "FlutterPreviewView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class PreviewView implements PersistentStateComponent<PreviewViewState> {
  public static final String TOOL_WINDOW_ID = "Flutter Outline";

  @NotNull
  private final PreviewViewState state = new PreviewViewState();

  @NotNull
  private final Project project;

  @NotNull
  private final FlutterDartAnalysisServer flutterAnalysisServer;

  private final InspectorGroupManagerService inspectorGroupManagerService;

  private SimpleToolWindowPanel windowPanel;

  private boolean isSettingSplitterProportion = false;
  private Splitter splitter;
  private Splitter propertyEditSplitter;
  private JScrollPane scrollPane;
  private JScrollPane propertyScrollPane;
  private OutlineTree tree;
  private @Nullable PreviewArea previewArea;

  private final Set<FlutterOutline> outlinesWithWidgets = Sets.newHashSet();
  private final Map<FlutterOutline, DefaultMutableTreeNode> outlineToNodeMap = Maps.newHashMap();

  private final EventStream<VirtualFile> currentFile;
  private String currentFilePath;
  FileEditor currentFileEditor;
  private Editor currentEditor;
  private FlutterOutline currentOutline;
  private final EventStream<List<FlutterOutline>> activeOutlines;

  private final WidgetEditToolbar widgetEditToolbar;
  private final FlutterOutlineListener outlineListener = new FlutterOutlineListener() {
    @Override
    public void outlineUpdated(@NotNull String filePath, @NotNull FlutterOutline outline, @Nullable String instrumentedCode) {
      if (Objects.equals(currentFilePath, filePath)) {
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
    public void caretAdded(@NotNull CaretEvent e) {
    }

    @Override
    public void caretRemoved(@NotNull CaretEvent e) {
    }
  };

  private final TreeSelectionListener treeSelectionListener = this::handleTreeSelectionEvent;
  private PropertyEditorPanel propertyEditPanel;
  private DefaultActionGroup propertyEditToolbarGroup;

  public PreviewView(@NotNull Project project) {
    this.project = project;
    currentFile = new EventStream<>();
    activeOutlines = new EventStream<>(ImmutableList.of());
    flutterAnalysisServer = FlutterDartAnalysisServer.getInstance(project);

    inspectorGroupManagerService = InspectorGroupManagerService.getInstance(project);

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

    widgetEditToolbar = new WidgetEditToolbar(
      FlutterSettings.getInstance().isEnableHotUi(),
      activeOutlines,
      currentFile,
      project,
      flutterAnalysisServer
    );
  }

  @NotNull
  @Override
  public PreviewViewState getState() {
    return this.state;
  }

  @Override
  public void loadState(@NotNull PreviewViewState state) {
    this.state.copyFrom(state);
  }

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final ContentManager contentManager = toolWindow.getContentManager();

    final Content content = contentFactory.createContent(null, null, false);
    content.setCloseable(false);

    windowPanel = new OutlineComponent(this);
    content.setComponent(windowPanel);

    windowPanel.setToolbar(widgetEditToolbar.getToolbar().getComponent());

    final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

    tree = new OutlineTree(rootNode);
    tree.setCellRenderer(new OutlineTreeCellRenderer());
    tree.expandAll();

    initTreePopup();

    // Add collapse all, expand all, and show only widgets buttons.
    if (toolWindow instanceof ToolWindowEx) {
      final ToolWindowEx toolWindowEx = (ToolWindowEx)toolWindow;

      final CommonActionsManager actions = CommonActionsManager.getInstance();
      final TreeExpander expander = new DefaultTreeExpander(tree);

      final AnAction expandAllAction = actions.createExpandAllAction(expander, tree);
      expandAllAction.getTemplatePresentation().setIcon(AllIcons.Actions.Expandall);

      final AnAction collapseAllAction = actions.createCollapseAllAction(expander, tree);
      collapseAllAction.getTemplatePresentation().setIcon(AllIcons.Actions.Collapseall);

      final ShowOnlyWidgetsAction showOnlyWidgetsAction = new ShowOnlyWidgetsAction();

      toolWindowEx.setTitleActions(expandAllAction, collapseAllAction, showOnlyWidgetsAction);
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
    content.setPreferredFocusableComponent(tree);

    contentManager.addContent(content);
    contentManager.setSelectedContent(content);

    splitter = new Splitter(true);
    setSplitterProportion(getState().getSplitterProportion());
    getState().addListener(e -> {
      final float newProportion = getState().getSplitterProportion();
      if (splitter.getProportion() != newProportion) {
        setSplitterProportion(newProportion);
      }
    });
    //noinspection Convert2Lambda
    splitter.addPropertyChangeListener("proportion", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (!isSettingSplitterProportion) {
          getState().setSplitterProportion(splitter.getProportion());
        }
      }
    });
    scrollPane.setMinimumSize(new Dimension(1, 1));
    splitter.setFirstComponent(scrollPane);
    windowPanel.setContent(splitter);
  }

  private void initTreePopup() {
    tree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        // Ensure that at least one Widget item is selected.
        final List<FlutterOutline> selectedOutlines = getOutlinesSelectedInTree();
        if (selectedOutlines.isEmpty()) {
          return;
        }
        for (FlutterOutline outline : selectedOutlines) {
          if (outline.getDartElement() != null) {
            return;
          }
        }

        widgetEditToolbar.createPopupMenu(comp, x, y);
      }
    });
  }

  private OutlineOffsetConverter getOutlineOffsetConverter() {
    return new OutlineOffsetConverter(project, currentFile.getValue());
  }

  private void handleTreeSelectionEvent(TreeSelectionEvent e) {
    final TreePath selectionPath = e.getNewLeadSelectionPath();
    if (selectionPath != null) {
      ApplicationManager.getApplication().invokeLater(() -> selectPath(selectionPath, false));
    }

    activeOutlines.setValue(getOutlinesSelectedInTree());
  }

  private void selectPath(TreePath selectionPath, boolean focusEditor) {
    final FlutterOutline outline = getOutlineOfPath(selectionPath);
    jumpToOutlineInEditor(outline, focusEditor);
  }

  private void jumpToOutlineInEditor(FlutterOutline outline, boolean focusEditor) {
    if (outline == null) {
      return;
    }
    final int offset = outline.getDartElement() != null ? outline.getDartElement().getLocation().getOffset() : outline.getOffset();
    final int editorOffset = getOutlineOffsetConverter().getConvertedFileOffset(offset);

    sendAnalyticEvent("jumpToSource");

    if (currentFile != null) {
      currentEditor.getCaretModel().removeCaretListener(caretListener);
      try {
        new OpenFileDescriptor(project, currentFile.getValue(), editorOffset).navigate(focusEditor);
      }
      finally {
        currentEditor.getCaretModel().addCaretListener(caretListener);
      }
    }

    // TODO(jacobr): refactor the previewArea to listen on the stream of
    // selected outlines instead.
    if (previewArea != null) {
      previewArea.select(ImmutableList.of(outline), currentEditor);
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

    if (FlutterSettings.getInstance().isEnableHotUi() && propertyEditPanel == null) {
      propertyEditSplitter = new Splitter(false, 0.75f);
      propertyEditPanel = new PropertyEditorPanel(inspectorGroupManagerService, project, flutterAnalysisServer, false, false, project);
      propertyEditPanel.initalize(null, activeOutlines, currentFile);
      propertyEditToolbarGroup = new DefaultActionGroup();
      final ActionToolbar windowToolbar = ActionManager.getInstance().createActionToolbar("PropertyArea", propertyEditToolbarGroup, true);

      final SimpleToolWindowPanel window = new SimpleToolWindowPanel(true, true);
      window.setToolbar(windowToolbar.getComponent());
      propertyScrollPane = ScrollPaneFactory.createScrollPane(propertyEditPanel);
      window.setContent(propertyScrollPane);
      propertyEditSplitter.setFirstComponent(window.getComponent());
      final InspectorGroupManagerService.Client inspectorStateServiceClient = new InspectorGroupManagerService.Client(project) {
        @Override
        public void onInspectorAvailabilityChanged() {
          super.onInspectorAvailabilityChanged();
          // Only show the screen mirror if there is not a running device and
          // the inspector supports the neccessary apis.
          if (getInspectorService() != null && getInspectorService().isHotUiScreenMirrorSupported()) {
            // Wait to create the preview area until it is needed.
            if (previewArea == null) {
              previewArea = new PreviewArea(project, outlinesWithWidgets, project);
            }
            propertyEditSplitter.setSecondComponent(previewArea.getComponent());
          }
          else {
            propertyEditSplitter.setSecondComponent(null);
          }
        }
      };
      inspectorGroupManagerService.addListener(inspectorStateServiceClient, project);

      splitter.setSecondComponent(propertyEditSplitter);
    }

    // TODO(jacobr): this is the wrong spot.
    if (propertyEditToolbarGroup != null) {
      TitleAction propertyTitleAction;
      propertyTitleAction = new TitleAction("Properties");
      propertyEditToolbarGroup.removeAll();
      propertyEditToolbarGroup.add(propertyTitleAction);
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
      if (FlutterSettings.getInstance().isShowOnlyWidgets() && !outlinesWithWidgets.contains(outline)) {
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

  private void addOutlinesCoveredByRange(List<FlutterOutline> covered, int start, int end, @Nullable FlutterOutline outline) {
    if (outline == null) {
      return;
    }
    final OutlineOffsetConverter converter = getOutlineOffsetConverter();
    final int outlineStart = converter.getConvertedOutlineOffset(outline);
    final int outlineEnd = converter.getConvertedOutlineEnd(outline);
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
    if (currentFile.getValue() != null) {
      flutterAnalysisServer.removeOutlineListener(currentFilePath, outlineListener);
      currentFile.setValue(null);
      currentFilePath = null;
    }

    // If not a Dart file, ignore it.
    if (newFile != null && !FlutterUtils.isDartFile(newFile)) {
      newFile = null;
    }

    // Show the toolbar if the new file is a Dart file, or hide otherwise.
    if (windowPanel != null) {
      if (newFile != null) {
        windowPanel.setToolbar(widgetEditToolbar.getToolbar().getComponent());
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
      currentFile.setValue(newFile);
      currentFilePath = FileUtil.toSystemDependentName(newFile.getPath());
      flutterAnalysisServer.addOutlineListener(currentFilePath, outlineListener);
    }
  }

  private void setSelectedEditor(FileEditor newEditor) {
    if (currentEditor != null) {
      currentEditor.getCaretModel().removeCaretListener(caretListener);
    }
    if (newEditor instanceof TextEditor) {
      currentFileEditor = newEditor;
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
      final FlutterOutline outline = getOutlineOffsetConverter().findOutlineAtOffset(currentOutline, caret.getOffset());
      if (outline != null) {
        selectedOutlines.add(outline);
      }
    }

    activeOutlines.setValue(selectedOutlines);

    applyOutlinesSelectionToTree(selectedOutlines);

    // TODO(jacobr): refactor the previewArea to listen on the stream of
    // selected outlines instead.
    if (previewArea != null) {
      previewArea.select(selectedOutlines, currentEditor);
    }
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
    tree.setSelectionPaths(selectedPaths.toArray(new TreePath[0]));
    tree.addTreeSelectionListener(treeSelectionListener);

    // JTree attempts to show as much of the node as possible, so scrolls horizontally.
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

  private void setSplitterProportion(float value) {
    isSettingSplitterProportion = true;
    try {
      splitter.setProportion(value);
    }
    finally {
      isSettingSplitterProportion = false;
    }
    doLayoutRecursively(splitter);
  }

  // TODO(jacobr): is this method really useful? This is from re-applying the
  // PreviewArea but it doesn't really seem that useful.
  private static void doLayoutRecursively(Component component) {
    if (component != null) {
      component.doLayout();
      if (component instanceof Container) {
        final Container container = (Container)component;
        for (Component child : container.getComponents()) {
          doLayoutRecursively(child);
        }
      }
    }
  }

  private void sendAnalyticEvent(@NotNull String name) {
    FlutterInitializer.getAnalytics().sendEvent("preview", name);
  }

  private class ShowOnlyWidgetsAction extends AnAction implements Toggleable, RightAlignedToolbarAction {
    ShowOnlyWidgetsAction() {
      super("Show Only Widgets", "Show only widgets", AllIcons.General.Filter);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final FlutterSettings flutterSettings = FlutterSettings.getInstance();
      flutterSettings.setShowOnlyWidgets(!flutterSettings.isShowOnlyWidgets());
      if (currentOutline != null) {
        updateOutline(currentOutline);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.putClientProperty(SELECTED_PROPERTY, FlutterSettings.getInstance().isShowOnlyWidgets());
      presentation.setEnabled(currentOutline != null);
    }
  }
}

/**
 * We subclass {@link SimpleToolWindowPanel} to implement "getData" and return {@link PlatformDataKeys#FILE_EDITOR},
 * so that Undo/Redo actions work.
 */
class OutlineComponent extends SimpleToolWindowPanel {
  private final PreviewView myView;

  OutlineComponent(PreviewView view) {
    super(true, true);
    myView = view;
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
      return myView.currentFileEditor;
    }
    return super.getData(dataId);
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

  /**
   * Return the string that is suitable for speed search. It has every name part separated so that we search only inside individual name
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

    final Icon icon = node.getIcon();
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

/**
 * Delegate to the given action, but do not render the action's icon.
 */
class TextOnlyActionWrapper extends AnAction {
  private final AnAction action;

  public TextOnlyActionWrapper(AnAction action) {
    super(action.getTemplatePresentation().getText());

    this.action = action;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    action.actionPerformed(event);
  }
}
