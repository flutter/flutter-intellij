/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view.preview;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import icons.FlutterIcons;
import io.flutter.dart.DartAnalysisServerServiceEx;
import io.flutter.dart.DartPlugin;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.inspector.FlutterWidget;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.CustomIconMaker;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.FlutterOutlineKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static io.flutter.dart.FlutterDartAnalysisServer.*;

@com.intellij.openapi.components.State(
  name = "FlutterPreviewView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class PreviewView implements PersistentStateComponent<PreviewView.State>, Disposable {
  public static final String TOOL_WINDOW_ID = "Flutter Preview";

  @NotNull
  private PreviewView.State state = new PreviewView.State();

  @NotNull final FlutterDartAnalysisServer flutterAnalysisServer;

  private Project project;
  private VirtualFile currentFile;
  private TextEditor currentEditor;

  @Nullable
  FlutterApp app;

  private MyTree tree;

  final OutlineListener outlineListener = new OutlineListener() {
    @Override
    public void outlineUpdated(@NotNull String filePath, @NotNull FlutterOutline outline) {
      System.out.println("outlineUpdated: " + filePath + "   " + outline);
      final DefaultMutableTreeNode rootNode = getRootNode();
      rootNode.removeAllChildren();
      showOutline(rootNode, ImmutableList.of(outline));
      getTreeModel().reload(rootNode);
      tree.expandAll();
    }
  };

  private void showOutline(@NotNull DefaultMutableTreeNode parent, @NotNull List<FlutterOutline> outlines) {
    for (int i = 0; i < outlines.size(); i++) {
      final FlutterOutline outline = outlines.get(i);

      OutlineObject object = new OutlineObject(outline);

      final DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);
      getTreeModel().insertNodeInto(node, parent, i);
      if (outline.getChildren() != null) {
        showOutline(node, outline.getChildren());
      }
    }
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)tree.getModel();
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTreeModel().getRoot();
  }

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

    tree = new MyTree(rootNode);
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.expandAll();

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
              //currentEditor.getEditor().set
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


    //toolbarGroup.add(new DebugDrawAction(this));
    //toolbarGroup.add(new ToggleInspectModeAction(this));
    //toolbarGroup.add(new TogglePlatformAction(this));
    //toolbarGroup.addSeparator();
    //toolbarGroup.add(new OpenObservatoryAction(this));
    //toolbarGroup.addSeparator();
    //toolbarGroup.add(new OverflowActionsAction(this));
    //
    //addInspectorPanel("Widgets", InspectorService.FlutterTreeType.widget, toolWindow, toolbarGroup, true);
    //addInspectorPanel("Render Tree", InspectorService.FlutterTreeType.renderObject, toolWindow, toolbarGroup, false);
  }

  /**
   * State for the view.
   */
  class State {
  }
}


class MyTree extends Tree {
  MyTree(DefaultMutableTreeNode model) {
    super(model);

    setRootVisible(false);
    setToggleClickCount(0);

    //// Decrease indent, scaled for different display types.
    //final BasicTreeUI ui = (BasicTreeUI)getUI();
    //ui.setRightChildIndent(JBUI.scale(4));
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

  String getName() {
    if (outline.getKind().equals(FlutterOutlineKind.DART_ELEMENT)) {
      return outline.getDartElement().getName();
    }
    return outline.getClassName();
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


class MyTreeCellRenderer extends ColoredTreeCellRenderer {
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
    if (!(userObject instanceof OutlineObject)) return;
    final OutlineObject node = (OutlineObject)userObject;

    final Icon icon = node.getIcon();
    if (icon != null) {
      setIcon(icon);
    }

    final String name = node.getName();
    if (name != null) {
      append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    // TODO(scheglov): custom display for units, colors, iterables, and icons.
    if (name != null && name.equals("Text")) {
      append(" ");
      append("data: 'Foo bar'", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }
}
