/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import icons.FlutterIcons;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.inspector.FlutterWidget;
import io.flutter.utils.CustomIconMaker;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@com.intellij.openapi.components.State(
  name = "FlutterPreviewView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class PreviewView implements PersistentStateComponent<PreviewView.State>, Disposable {
  public static final String TOOL_WINDOW_ID = "Flutter Preview";

  @NotNull
  private PreviewView.State state = new PreviewView.State();

  @NotNull
  private final Project project;

  @NotNull
  private final FlutterDartAnalysisServer flutterAnalysisServer;

  private SimpleToolWindowPanel windowPanel;
  private JComponent windowToolbar;

  private final Map<String, AnAction> messageToActionMap = new HashMap<>();
  private final Map<AnAction, SourceChange> actionToChangeMap = new HashMap<>();

  private JScrollPane scrollPane;
  private OutlineTree tree;
  private final Map<FlutterOutline, DefaultMutableTreeNode> outlineToNodeMap = Maps.newHashMap();
  private boolean isTreeClicking = false;

  private VirtualFile currentFile;
  private Editor currentEditor;
  private FlutterOutline currentOutline;

  private final FlutterOutlineListener outlineListener = new FlutterOutlineListener() {
    @Override
    public void outlineUpdated(@NotNull String filePath, @NotNull FlutterOutline outline) {
      if (currentFile != null && Objects.equals(currentFile.getPath(), filePath)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          currentOutline = outline;

          final DefaultMutableTreeNode rootNode = getRootNode();
          rootNode.removeAllChildren();

          outlineToNodeMap.clear();
          updateOutline(rootNode, outline.getChildren());

          getTreeModel().reload(rootNode);
          tree.expandAll();

          if (currentEditor != null) {
            final int offset = currentEditor.getCaretModel().getOffset();
            selectOutlineAtOffset(offset);
          }

          windowPanel.setToolbar(windowToolbar);
        });
      }
    }
  };

  private final CaretListener caretListener = new CaretListener() {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      final Caret caret = e.getCaret();
      if (caret != null && !isTreeClicking) {
        final int offset = caret.getOffset();
        selectOutlineAtOffset(offset);
      }
    }

    @Override
    public void caretAdded(CaretEvent e) {
    }

    @Override
    public void caretRemoved(CaretEvent e) {
    }
  };

  public PreviewView(@NotNull Project project) {
    this.project = project;
    flutterAnalysisServer = FlutterDartAnalysisServer.getInstance(project);

    // Show preview for the file selected when the view is being opened.
    {
      final VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
      if (selectedFiles.length != 0) {
        setSelectedFile(selectedFiles[0]);
      }

      final FileEditor[] selectedEditors = FileEditorManager.getInstance(project).getSelectedEditors();
      if (selectedEditors.length != 0) {
        setSelectedEditor(selectedEditors[0]);
      }
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
  public PreviewView.State getState() {
    return this.state;
  }

  @Override
  public void loadState(PreviewView.State state) {
    this.state = state;
  }

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final ContentManager contentManager = toolWindow.getContentManager();

    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();

    toolbarGroup.add(new QuickAssistAction(FlutterIcons.Center, "Wrap with Center"));
    toolbarGroup.add(new AnAction(FlutterIcons.Padding) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Messages.showErrorDialog("Not implemented yet.", "TODO");
      }
    });
    toolbarGroup.addSeparator();
    toolbarGroup.add(new AnAction(FlutterIcons.Up) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Messages.showErrorDialog("Not implemented yet.", "TODO");
      }
    });
    toolbarGroup.add(new AnAction(FlutterIcons.Down) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Messages.showErrorDialog("Not implemented yet.", "TODO");
      }
    });

    final Content content = contentFactory.createContent(null, null, false);
    content.setCloseable(false);

    windowPanel = new SimpleToolWindowPanel(true, true);
    content.setComponent(windowPanel);

    windowToolbar = ActionManager.getInstance().createActionToolbar("PreviewViewToolbar", toolbarGroup, true).getComponent();
    windowPanel.setToolbar(windowToolbar);

    final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

    tree = new OutlineTree(rootNode);
    tree.setCellRenderer(new OutlineTreeCellRenderer());
    tree.expandAll();

    // Add collapse all and expand all buttons.
    if (toolWindow instanceof ToolWindowEx) {
      final TreeExpander expander = new DefaultTreeExpander(tree);
      final CommonActionsManager actions = CommonActionsManager.getInstance();
      final AnAction expandAllAction = actions.createExpandAllAction(expander, tree);
      expandAllAction.getTemplatePresentation().setIcon(AllIcons.General.ExpandAll);
      final AnAction collapseAllAction = actions.createCollapseAllAction(expander, tree);
      collapseAllAction.getTemplatePresentation().setIcon(AllIcons.General.CollapseAll);
      ((ToolWindowEx)toolWindow).setTitleActions(expandAllAction, collapseAllAction);
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

    tree.addTreeSelectionListener(this::handleTreeSelectionEvent);

    scrollPane = ScrollPaneFactory.createScrollPane(tree);
    windowPanel.setContent(scrollPane);

    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
  }

  private void handleTreeSelectionEvent(TreeSelectionEvent e) {
    final TreePath selectionPath = e.getNewLeadSelectionPath();
    if (selectionPath != null) {
      ApplicationManager.getApplication().invokeLater(() -> selectPath(selectionPath, false));

      actionToChangeMap.clear();

      final VirtualFile selectionFile = this.currentFile;
      final FlutterOutline selectionOutline = getOutlineOfPath(selectionPath);
      if (selectionFile != null && selectionOutline != null) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          final int offset = selectionOutline.getOffset();
          final int length = selectionOutline.getLength();
          final List<SourceChange> changes = flutterAnalysisServer.edit_getAssists(selectionFile, offset, length);

          // If the current file or outline are different, ignore the changes.
          // We will eventually get new changes.
          final FlutterOutline newOutline = getOutlineOfPath(tree.getSelectionPath());
          if (!Objects.equals(this.currentFile, selectionFile) || newOutline != selectionOutline) {
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
        });
      }
    }
  }

  private void selectPath(TreePath selectionPath, boolean focusEditor) {
    final FlutterOutline outline = getOutlineOfPath(selectionPath);
    final int offset = outline.getDartElement() != null ? outline.getDartElement().getLocation().getOffset() : outline.getOffset();
    if (currentFile != null) {
      isTreeClicking = true;
      try {
        new OpenFileDescriptor(project, currentFile, offset).navigate(focusEditor);
      }
      finally {
        isTreeClicking = false;
      }
    }
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)tree.getModel();
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTreeModel().getRoot();
  }

  private void updateOutline(@NotNull DefaultMutableTreeNode parent, @NotNull List<FlutterOutline> outlines) {
    for (int i = 0; i < outlines.size(); i++) {
      final FlutterOutline outline = outlines.get(i);

      final OutlineObject object = new OutlineObject(outline);

      final DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);
      outlineToNodeMap.put(outline, node);
      getTreeModel().insertNodeInto(node, parent, i);
      if (outline.getChildren() != null) {
        updateOutline(node, outline.getChildren());
      }
    }
  }

  private FlutterOutline getOutlineOfPath(TreePath path) {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    final OutlineObject object = (OutlineObject)node.getUserObject();
    return object.outline;
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

  private void setSelectedFile(VirtualFile newFile) {
    if (currentFile != null) {
      flutterAnalysisServer.removeOutlineListener(currentFile.getPath(), outlineListener);
      currentFile = null;
    }

    // If no the new file, or it is not a Dart file, remove the toolbar.
    if (newFile == null || !FlutterUtils.isDartFile(newFile)) {
      if (windowPanel != null && windowPanel.isToolbarVisible()) {
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

  private void selectOutlineAtOffset(int offset) {
    final FlutterOutline outline = findOutlineAtOffset(currentOutline, offset);
    setSelectedOutline(outline);
  }

  private void setSelectedOutline(FlutterOutline outline) {
    if (outline != null) {
      final DefaultMutableTreeNode selectedNode = outlineToNodeMap.get(outline);
      if (selectedNode != null) {
        final TreeNode[] selectedNodesPath = selectedNode.getPath();
        final TreePath selectedPath = new TreePath(selectedNodesPath);

        // Ensure that all parent nodes are expected.
        tree.scrollPathToVisible(selectedPath);

        // Ensure that the top-level declaration (class) is on the top of the tree.
        if (selectedNodesPath.length >= 2) {
          scrollTreeToNodeOnTop(selectedNodesPath[1]);
        }

        // Ensure that the selected node is still visible, even if the top-level declaration is long.
        tree.scrollPathToVisible(selectedPath);

        // Now actually select the node.
        tree.setSelectionPath(selectedPath);

        // JTree attempts to show as much of the node as possible, so scrolls horizonally.
        // But we actually need to see the whole hierarchy, so we scroll back to zero.
        scrollPane.getHorizontalScrollBar().setValue(0);
      }
    }
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

  private class QuickAssistAction extends AnAction {
    QuickAssistAction(Icon icon, String assistMessage) {
      super(icon);
      messageToActionMap.put(assistMessage, this);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final SourceChange change;
      synchronized (actionToChangeMap) {
        change = actionToChangeMap.get(this);
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

  /**
   * State for the view.
   */
  class State {
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
        icon = getCustomIcon(className);
      }
    }
    return icon;
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

  private static Icon getCustomIcon(String text) {
    if (text == null) {
      return null;
    }

    final boolean isPrivate = text.startsWith("_");
    while (!text.isEmpty() && !Character.isAlphabetic(text.charAt(0))) {
      text = text.substring(1);
    }

    if (text.isEmpty()) {
      return null;
    }

    return iconMaker.getCustomIcon(text, isPrivate ? CustomIconMaker.IconKind.kMethod : CustomIconMaker.IconKind.kClass);
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

      final String text = DartElementPresentationUtil.getText(dartElement);
      appendSearch(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      return;
    }

    // Render the widget icon.
    final Icon icon = node.getIcon();
    if (icon != null) {
      setIcon(icon);
    }

    // Render the widget class.
    appendSearch(outline.getClassName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

    // Render the variable.
    if (outline.getVariableName() != null) {
      append(" ");
      appendSearch(outline.getVariableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
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

  private static boolean isAttributeElidable(String name) {
    return StringUtil.equals("text", name) || StringUtil.equals("icon", name);
  }

  private void appendSearch(@NotNull String text, @NotNull SimpleTextAttributes attributes) {
    SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, text, attributes, selected, this);
  }
}
