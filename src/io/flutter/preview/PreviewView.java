/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.JsonObject;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
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
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.console.FlutterConsoles;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.inspector.FlutterWidget;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.CustomIconMaker;
import net.miginfocom.swing.MigLayout;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@com.intellij.openapi.components.State(
  name = "FlutterPreviewView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class PreviewView implements PersistentStateComponent<PreviewViewState>, Disposable {
  public static final String TOOL_WINDOW_ID = "Flutter Outline";

  public static final String FEEDBACK_URL = "https://goo.gl/forms/MbPU0kcPqBO6tunH3";

  @NotNull
  private final PreviewViewState state = new PreviewViewState();

  @NotNull
  private final Project project;

  @NotNull
  private final FlutterDartAnalysisServer flutterAnalysisServer;

  final QuickAssistAction actionCenter;
  final QuickAssistAction actionPadding;
  final QuickAssistAction actionColumn;
  final QuickAssistAction actionRow;
  final QuickAssistAction actionMoveUp;
  final QuickAssistAction actionMoveDown;
  final QuickAssistAction actionRemove;
  final ExtractMethodAction actionExtractMethod;

  private SimpleToolWindowPanel windowPanel;
  private ActionToolbar windowToolbar;

  private final Map<String, AnAction> messageToActionMap = new HashMap<>();
  private final Map<AnAction, SourceChange> actionToChangeMap = new HashMap<>();

  private boolean isSettingSplitterProportion = false;
  private Splitter splitter;
  private JScrollPane scrollPane;
  private OutlineTree tree;
  private PreviewArea previewArea;

  private final Set<FlutterOutline> outlinesWithWidgets = Sets.newHashSet();
  private final Map<FlutterOutline, DefaultMutableTreeNode> outlineToNodeMap = Maps.newHashMap();

  private VirtualFile currentFile;
  private String currentFilePath;
  FileEditor currentFileEditor;
  private Editor currentEditor;
  private FlutterOutline currentOutline;

  private final RenderHelper myRenderHelper;

  private final FlutterOutlineListener outlineListener = new FlutterOutlineListener() {
    @Override
    public void outlineUpdated(@NotNull String filePath, @NotNull FlutterOutline outline, @Nullable String instrumentedCode) {
      if (Objects.equals(currentFilePath, filePath)) {
        ApplicationManager.getApplication().invokeLater(() -> updateOutline(outline));
        if (myRenderHelper != null && previewArea != null) {
          previewArea.renderingStarted();
          myRenderHelper.setFile(currentFile, outline, instrumentedCode);
          ApplicationManager.getApplication().invokeLater(() -> {
            final Caret caret = currentEditor.getCaretModel().getPrimaryCaret();
            myRenderHelper.setOffset(caret.getOffset());
          });
        }
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

  final RenderHelper.Listener renderListener = new RenderHelper.Listener() {
    @Override
    public void onResponse(@NotNull FlutterOutline widget, @NotNull JsonObject response) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (previewArea != null) {
          previewArea.show(currentOutline, widget, response);

          final Caret caret = currentEditor.getCaretModel().getPrimaryCaret();
          final FlutterOutline outline = findOutlineAtOffset(currentOutline, caret.getOffset());
          if (outline != null) {
            previewArea.select(ImmutableList.of(outline));
          }
        }
      });
    }

    @Override
    public void onFailure(@NotNull RenderProblemKind kind, @Nullable FlutterOutline widget) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (previewArea != null) {
          switch (kind) {
            case NO_WIDGET:
              previewArea.clear(PreviewArea.NO_WIDGET_MESSAGE);
              setSplitterProportion(0.9f);
              break;
            case NOT_RENDERABLE_WIDGET:
              assert widget != null;
              showNotRenderableInPreviewArea(widget);
              break;
            case NO_TEMPORARY_DIRECTORY:
              previewArea.clear("Internal error");
              break;
            case TIMEOUT:
              previewArea.clear("Timeout during rendering");
              break;
            case INVALID_JSON:
              previewArea.clear("Invalid JSON response");
              break;
          }
        }
      });
    }

    @Override
    public void onRenderableWidget(@NotNull FlutterOutline widget) {
      setSplitterProportion(getState().getSplitterProportion());
      final Dimension renderSize = previewArea.getRenderSize();
      myRenderHelper.setSize(renderSize.width, renderSize.height);
    }

    @Override
    public void onLocalException(@NotNull FlutterOutline widget, @NotNull Throwable localException) {
      ApplicationManager.getApplication().invokeLater(() -> showLocalException(localException));
    }

    @Override
    public void onRemoteException(@NotNull FlutterOutline widget, @NotNull JsonObject remoteException) {
      ApplicationManager.getApplication().invokeLater(() -> showRemoteException(remoteException));
    }
  };

  public PreviewView(@NotNull Project project) {
    this.project = project;
    flutterAnalysisServer = FlutterDartAnalysisServer.getInstance(project);

    if (FlutterSettings.getInstance().isShowPreviewArea()) {
      myRenderHelper = new RenderHelper(project, renderListener);
    }
    else {
      myRenderHelper = null;
    }

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

    actionCenter = new QuickAssistAction("dart.assist.flutter.wrap.center", FlutterIcons.Center, "Center widget");
    actionPadding = new QuickAssistAction("dart.assist.flutter.wrap.padding", FlutterIcons.Padding, "Add padding");
    actionColumn = new QuickAssistAction("dart.assist.flutter.wrap.column", FlutterIcons.Column, "Wrap with Column");
    actionRow = new QuickAssistAction("dart.assist.flutter.wrap.row", FlutterIcons.Row, "Wrap with Row");
    actionMoveUp = new QuickAssistAction("dart.assist.flutter.move.up", FlutterIcons.Up, "Move widget up");
    actionMoveDown = new QuickAssistAction("dart.assist.flutter.move.down", FlutterIcons.Down, "Move widget down");
    actionRemove = new QuickAssistAction("dart.assist.flutter.removeWidget", FlutterIcons.RemoveWidget, "Remove widget");
    actionExtractMethod = new ExtractMethodAction();
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
    toolbarGroup.add(actionCenter);
    toolbarGroup.add(actionPadding);
    toolbarGroup.add(actionColumn);
    toolbarGroup.add(actionRow);
    toolbarGroup.addSeparator();
    toolbarGroup.add(actionExtractMethod);
    toolbarGroup.addSeparator();
    toolbarGroup.add(actionMoveUp);
    toolbarGroup.add(actionMoveDown);
    toolbarGroup.addSeparator();
    toolbarGroup.add(actionRemove);
    toolbarGroup.add(new ShowOnlyWidgetsAction(AllIcons.General.Filter, "Show only widgets"));

    final Content content = contentFactory.createContent(null, null, false);
    content.setCloseable(false);

    windowPanel = new OutlineComponent(this);
    content.setComponent(windowPanel);

    windowToolbar = ActionManager.getInstance().createActionToolbar("PreviewViewToolbar", toolbarGroup, true);
    windowPanel.setToolbar(windowToolbar.getComponent());

    final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

    tree = new OutlineTree(rootNode);
    tree.setCellRenderer(new OutlineTreeCellRenderer());
    tree.expandAll();

    initTreePopup();

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

    previewArea = new PreviewArea(new PreviewArea.Listener() {
      @Override
      public void clicked(FlutterOutline outline) {
        final ImmutableList<FlutterOutline> outlines = ImmutableList.of(outline);
        updateActionsForOutlines(outlines);
        applyOutlinesSelectionToTree(outlines);
        jumpToOutlineInEditor(outline, false);
      }

      @Override
      public void doubleClicked(FlutterOutline outline) {
        final ImmutableList<FlutterOutline> outlines = ImmutableList.of(outline);
        updateActionsForOutlines(outlines);
        applyOutlinesSelectionToTree(outlines);
        jumpToOutlineInEditor(outline, true);
      }

      @Override
      public void resized(int width, int height) {
        if (myRenderHelper != null) {
          myRenderHelper.setSize(width, height);
        }
      }
    });

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
    splitter.setFirstComponent(scrollPane);
    windowPanel.setContent(splitter);

    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
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

        // The corresponding tree item has just been selected.
        // Wait short time for receiving assists from the server.
        for (int i = 0; i < 20 && actionToChangeMap.isEmpty(); i++) {
          Uninterruptibles.sleepUninterruptibly(5, TimeUnit.MILLISECONDS);
        }

        final DefaultActionGroup group = new DefaultActionGroup();
        boolean hasAction = false;
        if (actionCenter.isEnabled()) {
          hasAction = true;
          group.add(new TextOnlyActionWrapper(actionCenter));
        }
        if (actionPadding.isEnabled()) {
          hasAction = true;
          group.add(new TextOnlyActionWrapper(actionPadding));
        }
        if (actionColumn.isEnabled()) {
          hasAction = true;
          group.add(new TextOnlyActionWrapper(actionColumn));
        }
        if (actionRow.isEnabled()) {
          hasAction = true;
          group.add(new TextOnlyActionWrapper(actionRow));
        }
        group.addSeparator();
        if (actionExtractMethod.isEnabled()) {
          hasAction = true;
          group.add(new TextOnlyActionWrapper(actionExtractMethod));
        }
        group.addSeparator();
        if (actionMoveUp.isEnabled()) {
          hasAction = true;
          group.add(new TextOnlyActionWrapper(actionMoveUp));
        }
        if (actionMoveDown.isEnabled()) {
          hasAction = true;
          group.add(new TextOnlyActionWrapper(actionMoveDown));
        }
        group.addSeparator();
        if (actionRemove.isEnabled()) {
          hasAction = true;
          group.add(new TextOnlyActionWrapper(actionRemove));
        }

        // Don't show the empty popup.
        if (!hasAction) {
          return;
        }

        final ActionManager actionManager = ActionManager.getInstance();
        final ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    });
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
    jumpToOutlineInEditor(outline, focusEditor);
  }

  private void jumpToOutlineInEditor(FlutterOutline outline, boolean focusEditor) {
    if (outline == null) {
      return;
    }
    final int offset = outline.getDartElement() != null ? outline.getDartElement().getLocation().getOffset() : outline.getOffset();
    final int editorOffset = getConvertedFileOffset(offset);

    sendAnalyticEvent("jumpToSource");

    if (currentFile != null) {
      currentEditor.getCaretModel().removeCaretListener(caretListener);
      try {
        new OpenFileDescriptor(project, currentFile, editorOffset).navigate(focusEditor);
      }
      finally {
        currentEditor.getCaretModel().addCaretListener(caretListener);
      }
    }

    if (myRenderHelper != null) {
      myRenderHelper.setOffset(editorOffset);
      previewArea.select(ImmutableList.of(outline));
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
        final int offset = getConvertedOutlineOffset(firstOutline);
        final int length = getConvertedOutlineEnd(lastOutline) - offset;
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

    if (FlutterSettings.getInstance().isShowPreviewArea()) {
      if (ModelUtils.containsBuildMethod(outline)) {
        splitter.setSecondComponent(previewArea.getComponent());
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

  private int getConvertedFileOffset(int offset) {
    return DartAnalysisServerService.getInstance(project).getConvertedOffset(currentFile, offset);
  }

  private int getConvertedOutlineOffset(FlutterOutline outline) {
    final int offset = outline.getOffset();
    return getConvertedFileOffset(offset);
  }

  private int getConvertedOutlineEnd(FlutterOutline outline) {
    final int end = outline.getOffset() + outline.getLength();
    return getConvertedFileOffset(end);
  }

  private FlutterOutline findOutlineAtOffset(FlutterOutline outline, int offset) {
    if (outline == null) {
      return null;
    }
    if (getConvertedOutlineOffset(outline) <= offset && offset <= getConvertedOutlineEnd(outline)) {
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

    final int outlineStart = getConvertedOutlineOffset(outline);
    final int outlineEnd = getConvertedOutlineEnd(outline);
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
      flutterAnalysisServer.removeOutlineListener(currentFilePath, outlineListener);
      currentFile = null;
      currentFilePath = null;
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

    // Set the new file, without outline.
    if (myRenderHelper != null) {
      myRenderHelper.setFile(newFile, null, null);
      if (newFile == null && previewArea != null) {
        previewArea.clear(PreviewArea.NOTHING_TO_SHOW);
      }
    }

    // Subscribe for the outline for the new file.
    if (newFile != null) {
      currentFile = newFile;
      currentFilePath = FileUtil.toSystemDependentName(currentFile.getPath());
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
      final FlutterOutline outline = findOutlineAtOffset(currentOutline, caret.getOffset());
      if (outline != null) {
        selectedOutlines.add(outline);
      }
    }

    updateActionsForOutlines(selectedOutlines);
    applyOutlinesSelectionToTree(selectedOutlines);

    if (myRenderHelper != null) {
      final int offset = caret.getOffset();
      myRenderHelper.setOffset(offset);
      previewArea.select(selectedOutlines);
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
    tree.setSelectionPaths(selectedPaths.toArray(new TreePath[selectedPaths.size()]));
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

  private void showNotRenderableInPreviewArea(@NotNull FlutterOutline widget) {
    final JPanel panel = new JPanel();
    panel.setLayout(new MigLayout("insets 0", "[grow, center]", "[grow, bottom][grow 200, top]"));

    panel.add(new JBLabel(PreviewArea.NOT_RENDERABLE), "cell 0 0");

    final LinkLabel linkLabel = LinkLabel.create("Add forDesignTime() constructor...", () -> {
      final SourceChange change = flutterAnalysisServer.flutter_getChangeAddForDesignTimeConstructor(currentFile, widget.getOffset());
      if (change != null) {
        applyChangeAndShowException(change);
      }
    });
    panel.add(linkLabel, "cell 0 1");

    previewArea.clear(panel);

    setSplitterProportion(0.9f);
  }

  private void showLocalException(@NotNull Throwable localException) {
    final JPanel panel = new JPanel();
    panel.setLayout(new MigLayout("insets 0", "[grow, center]", "[grow, bottom][grow 200, top]"));

    panel.add(new JBLabel("Encountered an exception during rendering"), "cell 0 0");

    final LinkLabel linkLabel = LinkLabel.create("Show exception...", () -> {
      final StringWriter stringWriter = new StringWriter();
      final PrintWriter printWriter = new PrintWriter(stringWriter);
      printWriter.println(localException.getMessage());
      localException.printStackTrace(printWriter);
      printWriter.println();
      printWriter.println();
      FlutterConsoles.displayMessage(project, null, stringWriter.toString());
    });
    panel.add(linkLabel, "cell 0 1");

    previewArea.clear(panel);
  }

  private void showRemoteException(@NotNull JsonObject remoteException) {
    final JPanel panel = new JPanel();
    panel.setLayout(new MigLayout("insets 0", "[grow, center]", "[grow, bottom][grow 200, top]"));

    panel.add(new JBLabel("Encountered an exception during rendering"), "cell 0 0");

    final LinkLabel linkLabel = LinkLabel.create("Show exception...", () -> {
      final StringWriter stringWriter = new StringWriter();
      final PrintWriter printWriter = new PrintWriter(stringWriter);
      printWriter.println(remoteException.get("exception").getAsString());
      printWriter.println(remoteException.get("stackTrace").getAsString());
      printWriter.println();
      printWriter.println();
      FlutterConsoles.displayMessage(project, null, stringWriter.toString());
    });
    panel.add(linkLabel, "cell 0 1");

    previewArea.clear(panel);
  }

  private void applyChangeAndShowException(SourceChange change) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        AssistUtils.applySourceChange(project, change, false);
      }
      catch (DartSourceEditException exception) {
        FlutterMessages.showError("Error applying change", exception.getMessage());
      }
    });
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
        applyChangeAndShowException(change);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean hasChange = actionToChangeMap.containsKey(this);
      e.getPresentation().setEnabled(hasChange);
    }

    boolean isEnabled() {
      return actionToChangeMap.containsKey(this);
    }
  }

  private class ExtractMethodAction extends AnAction {
    private final String id = "dart.assist.flutter.extractMethod";

    ExtractMethodAction() {
      super("Extract method...", null, FlutterIcons.ExtractMethod);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final AnAction action = ActionManager.getInstance().getAction("ExtractMethod");
      if (action != null) {
        final FlutterOutline outline = getWidgetOutline();
        if (outline != null) {
          TransactionGuard.submitTransaction(project, () -> {
            // Ideally we don't need this - just caret at the beginning should be enough.
            // Unfortunately this was implemented only recently.
            // So, we have to select the widget range.
            final int offset = getConvertedOutlineOffset(outline);
            final int end = getConvertedOutlineEnd(outline);
            currentEditor.getSelectionModel().setSelection(offset, end);

            final JComponent editorComponent = currentEditor.getComponent();
            final DataContext editorContext = DataManager.getInstance().getDataContext(editorComponent);
            final AnActionEvent editorEvent = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, editorContext);

            action.actionPerformed(editorEvent);
          });
        }
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean isEnabled = isEnabled();
      e.getPresentation().setEnabled(isEnabled);
    }

    boolean isEnabled() {
      return getWidgetOutline() != null;
    }

    private FlutterOutline getWidgetOutline() {
      final List<FlutterOutline> outlines = getOutlinesSelectedInTree();
      if (outlines.size() == 1) {
        final FlutterOutline outline = outlines.get(0);
        if (outline.getDartElement() == null) {
          return outline;
        }
      }
      return null;
    }
  }

  private class ShowOnlyWidgetsAction extends AnAction implements Toggleable, RightAlignedToolbarAction {
    ShowOnlyWidgetsAction(@NotNull Icon icon, @NotNull String text) {
      super(text, null, icon);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
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
  public Object getData(String dataId) {
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

  Icon getFlutterDecoratedIcon() {
    final Icon icon = getIcon();
    if (icon == null) return null;

    LayeredIcon decorated = flutterDecoratedIcons.get(icon);
    if (decorated == null) {
      final Icon badgeIcon = FlutterIcons.Flutter_badge;

      decorated = new LayeredIcon(2);
      decorated.setIcon(badgeIcon, 0, 0, 1 + (icon.getIconHeight() - badgeIcon.getIconHeight()) / 2);
      decorated.setIcon(icon, 1, badgeIcon.getIconWidth(), 0);

      flutterDecoratedIcons.put(icon, decorated);
    }

    return decorated;
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
  public void actionPerformed(AnActionEvent event) {
    action.actionPerformed(event);
  }
}
