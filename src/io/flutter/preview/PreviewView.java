/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
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
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import icons.FlutterIcons;
import io.flutter.dart.DartAnalysisServerServiceEx;
import io.flutter.dart.DartPlugin;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.inspector.FlutterWidget;
import io.flutter.utils.CustomIconMaker;
import org.dartlang.analysis.server.protocol.Element;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.FlutterOutlineAttribute;
import org.dartlang.analysis.server.protocol.FlutterOutlineKind;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
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

  private OutlineTree tree;

  private VirtualFile currentFile;
  private TextEditor currentEditor;

  final FlutterOutlineListener outlineListener = new FlutterOutlineListener() {
    @Override
    public void outlineUpdated(@NotNull String filePath, @NotNull FlutterOutline outline) {
      if (currentFile != null && Objects.equals(currentFile.getPath(), filePath)) {
        final DefaultMutableTreeNode rootNode = getRootNode();
        rootNode.removeAllChildren();
        showOutline(rootNode, ImmutableList.of(outline));
        getTreeModel().reload(rootNode);
        tree.expandAll();
      }
    }
  };

  public PreviewView(@NotNull Project project) {
    this.project = project;
    final DartAnalysisServerService analysisService = DartPlugin.getInstance().getAnalysisService(project);
    final DartAnalysisServerServiceEx analysisServiceEx = DartAnalysisServerServiceEx.get(analysisService);
    flutterAnalysisServer = new FlutterDartAnalysisServer(analysisServiceEx);

    final MessageBusConnection bus = project.getMessageBus().connect(project);
    bus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (currentFile != null) {
          flutterAnalysisServer.removeOutlineListener(currentFile.getPath(), outlineListener);
          currentFile = null;
          currentEditor = null;
        }
        if (event.getNewFile() != null && event.getNewEditor() instanceof TextEditor) {
          currentFile = event.getNewFile();
          currentEditor = (TextEditor)event.getNewEditor();
          flutterAnalysisServer.addOutlineListener(currentFile.getPath(), outlineListener);
        }
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

    toolbarGroup.add(new AnAction(FlutterIcons.PreviewSurroundCenter) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Messages.showErrorDialog("Not implemented yet.", "TODO");
      }
    });

    toolbarGroup.add(new AnAction(FlutterIcons.PreviewSurroundColumn) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Messages.showErrorDialog("Not implemented yet.", "TODO");
      }
    });

    toolbarGroup.add(new AnAction(FlutterIcons.PreviewSurroundRow) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Messages.showErrorDialog("Not implemented yet.", "TODO");
      }
    });


    final Content content = contentFactory.createContent(null, null, false);
    content.setCloseable(false);

    final SimpleToolWindowPanel windowPanel = new SimpleToolWindowPanel(true, true);
    content.setComponent(windowPanel);

    windowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("PreviewViewToolbar", toolbarGroup, true).getComponent());

    final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

    tree = new OutlineTree(rootNode);
    tree.setCellRenderer(new OutlineTreeCellRenderer());
    tree.expandAll();

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
        if (e.getClickCount() == 2) {
          final TreePath selectionPath = tree.getSelectionPath();
          if (selectionPath != null) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
            final OutlineObject object = (OutlineObject)node.getUserObject();
            System.out.println(object.outline.getClassName() + "  @" + object.outline.getOffset());
            if (currentEditor != null) {
              new OpenFileDescriptor(project, currentFile, object.outline.getOffset()).navigate(true);
              currentEditor.getEditor().getCaretModel().moveToOffset(object.outline.getOffset());
            }
          }
        }
      }
    });

    windowPanel.setContent(ScrollPaneFactory.createScrollPane(tree));

    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)tree.getModel();
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTreeModel().getRoot();
  }

  private void showOutline(@NotNull DefaultMutableTreeNode parent, @NotNull List<FlutterOutline> outlines) {
    for (int i = 0; i < outlines.size(); i++) {
      final FlutterOutline outline = outlines.get(i);

      final OutlineObject object = new OutlineObject(outline);

      final DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);
      getTreeModel().insertNodeInto(node, parent, i);
      if (outline.getChildren() != null) {
        showOutline(node, outline.getChildren());
      }
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
    if (outline.getParentAssociationLabel() != null) {
      builder.append('|');
      builder.append(outline.getParentAssociationLabel());
      builder.append(": ");
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

    // Render the parent/child association.
    if (outline.getParentAssociationLabel() != null) {
      appendSearch(outline.getParentAssociationLabel() + ": ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    // Render the widget class.
    appendSearch(outline.getClassName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

    // Render the variable.
    if (outline.getVariableName() != null) {
      appendSearch(outline.getVariableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    // Append all attributes.
    final List<FlutterOutlineAttribute> attributes = outline.getAttributes();
    if (attributes != null) {
      for (FlutterOutlineAttribute attribute : attributes) {
        append(" ");
        append("[", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        appendSearch(attribute.getName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        append(": ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        appendSearch(attribute.getLabel(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        append("]", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        // TODO(scheglov): custom display for units, colors, iterables, and icons?
      }
    }
  }

  private void appendSearch(@NotNull String text, @NotNull SimpleTextAttributes attributes) {
    SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, text, attributes, selected, this);
  }
}
