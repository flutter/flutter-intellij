/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import io.flutter.inspector.*;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InspectorPanel extends JPanel implements Disposable, InspectorService.InspectorServiceClient {
  private final MyTree myRootsTree;
  private final PropertiesPanel myPropertiesPanel;
  private FlutterView view;
  private final Computable<Boolean> isApplicable;
  private final InspectorService.FlutterTreeType treeType;
  private final FlutterView flutterView;
  private CompletableFuture<DiagnosticsNode> rootFuture;

  private static final DataKey<Tree> INSPECTOR_TREE_KEY = DataKey.create("Flutter.InspectorTree");

  private FlutterApp getFlutterApp() {
    return flutterView.getFlutterApp();
  }

  private DefaultMutableTreeNode selectedNode;

  private CompletableFuture<DiagnosticsNode> pendingSelectionFuture;
  private boolean myIsListening = false;
  private boolean isActive = false;

  private static final Logger LOG = Logger.getInstance(InspectorPanel.class);

  public InspectorPanel(FlutterView flutterView,
                        Computable<Boolean> isApplicable,
                        InspectorService.FlutterTreeType treeType) {
    super(new BorderLayout());
    this.treeType = treeType;
    this.flutterView = flutterView;
    this.isApplicable = isApplicable;

    myRootsTree = new MyTree(new DefaultMutableTreeNode(null));
    myRootsTree.addTreeExpansionListener(new MyTreeExpansionListener());
    myPropertiesPanel = new PropertiesPanel();

    initTree(myRootsTree);
    myRootsTree.getSelectionModel().addTreeSelectionListener(e -> selectionChanged());

    final Splitter treeSplitter = new Splitter(true);
    treeSplitter.setProportion(0.8f);
    // TODO(jacobr): surely there is more we should be disposing.
    Disposer.register(this, treeSplitter::dispose);
    treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myRootsTree));
    treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myPropertiesPanel));
    add(treeSplitter);
  }

  static DiagnosticsNode getDiagnosticNode(TreeNode treeNode) {
    if (!(treeNode instanceof DefaultMutableTreeNode)) {
      return null;
    }
    final Object userData = ((DefaultMutableTreeNode)treeNode).getUserObject();
    return userData instanceof DiagnosticsNode ? (DiagnosticsNode)userData : null;
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)myRootsTree.getModel();
  }

  public void onIsolateStopped() {
    if (rootFuture != null && !rootFuture.isDone()) {
      // Already running.
      rootFuture.cancel(true);
      rootFuture = null;
    }
    getTreeModel().setRoot(new DefaultMutableTreeNode());
    myPropertiesPanel.showProperties(null);
  }

  public void onAppChanged() {
    setActivate(isApplicable.compute());
  }

  @Nullable
  private InspectorService getInspectorService() {
    final FlutterApp app = getFlutterApp();
    return (app != null) ? app.getInspectorService() : null;
  }

  void setActivate(boolean enabled) {
    if (!enabled) {
      onIsolateStopped();
      isActive = false;
      return;
    }

    isActive = true;
    assert (getInspectorService() != null);
    getInspectorService().addClient(this);
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTreeModel().getRoot();
  }

  void recomputeTreeRoot() {
    if (getRootNode().getUserObject() instanceof DiagnosticsNode) {
      // May be stale but at least we have something.
      // TODO(jacobr): actually recompute the tree root in this case.
      return;
    }
    if (rootFuture != null && !rootFuture.isDone()) {
      return;
    }
    rootFuture = getInspectorService().getRoot(treeType);

    whenCompleteUiThread(rootFuture, (final DiagnosticsNode n, Throwable error) -> {
      if (error != null) {
        return;
      }
      final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(n);
      // TODO(jacobr): be more judicious about nuking the whole tree.
      setupTreeNode(rootNode, n);
      maybeLoadChildren(rootNode);
      getTreeModel().setRoot(rootNode);
    });
  }

  void setupTreeNode(DefaultMutableTreeNode node, DiagnosticsNode diagnosticsNode) {
    node.setUserObject(diagnosticsNode);
    node.setAllowsChildren(diagnosticsNode.hasChildren());
    if (diagnosticsNode.hasChildren()) {
      if (diagnosticsNode.childrenReady()) {
        final CompletableFuture<ArrayList<DiagnosticsNode>> childrenFuture = diagnosticsNode.getChildren();
        assert (childrenFuture.isDone());
        setupChildren(node, childrenFuture.getNow(null));
      }
      else {
        node.removeAllChildren();
        node.add(new DefaultMutableTreeNode("Loading..."));
      }
    }
  }

  void setupChildren(DefaultMutableTreeNode treeNode, ArrayList<DiagnosticsNode> children) {
    treeNode.removeAllChildren();
    treeNode.setAllowsChildren(!children.isEmpty());
    for (DiagnosticsNode child : children) {
      final DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode();
      setupTreeNode(childTreeNode, child);
      treeNode.add(childTreeNode);
    }
  }

  void maybeLoadChildren(DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof DiagnosticsNode)) {
      return;
    }
    final DiagnosticsNode diagonsticsNode = (DiagnosticsNode)node.getUserObject();
    if (diagonsticsNode.hasChildren()) {
      if (placeholderChildren(node)) {
        whenCompleteUiThread(diagonsticsNode.getChildren(), (ArrayList<DiagnosticsNode> children, Throwable throwable) -> {
          if (throwable != null) {
            // Display that children failed to load.
            return;
          }
          if (node.getUserObject() != diagonsticsNode) {
            // Node changed, this data is stale.
            return;
          }
          setupChildren(node, children);
          getTreeModel().nodeStructureChanged(node);
        });
      }
    }
  }

  /**
   * Helper to get the value of a future on the UI thread.
   */
  public static <T> void whenCompleteUiThread(CompletableFuture<T> future, BiConsumer<? super T, ? super Throwable> action) {
    future.whenCompleteAsync(
      (T value, Throwable throwable) -> ApplicationManager.getApplication().invokeLater(() -> action.accept(value, throwable)));
  }

  public void onFlutterFrame() {
    recomputeTreeRoot();
  }

  private boolean identicalDiagnosticsNodes(DiagnosticsNode a, DiagnosticsNode b) {
    return a.getDartDiagnosticRef().equals(b.getDartDiagnosticRef());
  }

  public void onInspectorSelectionChanged() {
    if (pendingSelectionFuture != null) {
      // Pending selection changed is obsolete.
      pendingSelectionFuture.cancel(true);
      pendingSelectionFuture = null;
    }
    pendingSelectionFuture = getInspectorService().getSelection(getSelectedDiagnostic(), treeType);
    whenCompleteUiThread(pendingSelectionFuture, (DiagnosticsNode newSelection, Throwable error) -> {
      if (error != null) {
        LOG.error(error);
        return;
      }
      if (newSelection != getSelectedDiagnostic()) {
        whenCompleteUiThread(getInspectorService().getParentChain(newSelection), (ArrayList<DiagnosticsPathNode> path, Throwable ex) -> {
          if (ex != null) {
            LOG.error(ex);
            return;
          }
          DefaultMutableTreeNode treeNode = getRootNode();
          final DefaultTreeModel model = getTreeModel();
          final DefaultMutableTreeNode[] treePath = new DefaultMutableTreeNode[path.size()];
          for (int i = 0; i < path.size(); ++i) {
            treePath[i] = treeNode;
            final DiagnosticsPathNode pathNode = path.get(i);
            final DiagnosticsNode pathDiagnosticNode = pathNode.getNode();
            final ArrayList<DiagnosticsNode> newChildren = pathNode.getChildren();
            final DiagnosticsNode existingNode = getDiagnosticNode(treeNode);
            boolean nodeChanged = false;
            if (!identicalDiagnosticsNodes(pathDiagnosticNode, existingNode)) {
              treeNode.setUserObject(pathDiagnosticNode);
              // Clear children to force an update on this subtree. Not neccessarily required.
              nodeChanged = true;
            }
            treeNode.setAllowsChildren(!newChildren.isEmpty());
            for (int j = 0; j < newChildren.size(); ++j) {
              final DiagnosticsNode newChild = newChildren.get(j);
              if (j >= treeNode.getChildCount() || !identicalDiagnosticsNodes(newChild, getDiagnosticNode(treeNode.getChildAt(j)))) {
                final DefaultMutableTreeNode child;
                if (j >= treeNode.getChildCount()) {
                  child = new DefaultMutableTreeNode();
                  treeNode.add(child);
                  nodeChanged = true;
                }
                else {
                  child = (DefaultMutableTreeNode)treeNode.getChildAt(j);
                }
                if (j != pathNode.getChildIndex()) {
                  setupTreeNode(child, newChild);
                  model.reload(child);
                }
                else {
                  child.setUserObject(newChild);
                  child.setAllowsChildren(newChild.hasChildren());
                  child.removeAllChildren();
                }

                // TODO(jacobr): this is wrong. We shouldn't always be setting the node as changed.
                nodeChanged = true;
                // TODO(jacobr): we are likely calling the wrong node structure changed APIs.
                // For example, we should be getting these change notifications for free if we
                // switched to call methods on the model object directly to manipulate the tree.
                model.nodeChanged(child);
                model.nodeStructureChanged(child);
              }
              model.reload(treeNode);
            }
            if (i != path.size() - 1) {
              treeNode = (DefaultMutableTreeNode)treeNode.getChildAt(pathNode.getChildIndex());
            }
          }
          final TreePath selectionPath = new TreePath(treePath);
          myRootsTree.setSelectionPath(selectionPath);
          myRootsTree.scrollPathToVisible(selectionPath);
        });
      }
    });
  }

  private DiagnosticsNode getSelectedDiagnostic() {
    if (selectedNode == null) {
      return null;
    }
    final Object userObject = selectedNode.getUserObject();
    return (userObject instanceof DiagnosticsNode) ? (DiagnosticsNode)userObject : null;
  }

  private void maybePopulateChildren(DefaultMutableTreeNode treeNode) {
    final Object userObject = treeNode.getUserObject();
    if (userObject instanceof DiagnosticsNode) {
      final DiagnosticsNode diagnostic = (DiagnosticsNode)userObject;
      if (diagnostic.hasChildren() && treeNode.getChildCount() == 0) {
        final CompletableFuture<ArrayList<DiagnosticsNode>> childrenFuture = diagnostic.getChildren();
        whenCompleteUiThread(childrenFuture, (ArrayList<DiagnosticsNode> children, Throwable throwable) -> {
          if (throwable != null) {
            // TODO(jacobr): show an error in the UI that we could not load children.
            return;
          }
          if (treeNode.getChildCount() == 0) {
            setupChildren(treeNode, children);
          }
          getTreeModel().nodeStructureChanged(treeNode);
          // TODO(jacobr): do we need to do anything else to mark the tree as dirty?
        });
      }
    }
  }

  private void selectionChanged() {
    final DefaultMutableTreeNode[] selectedNodes = myRootsTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
    for (DefaultMutableTreeNode node : selectedNodes) {
      maybePopulateChildren(node);
    }

    if (selectedNodes.length > 0) {
      selectedNode = selectedNodes[0];
      final Object userObject = selectedNodes[0].getUserObject();
      if (userObject instanceof DiagnosticsNode) {
        final DiagnosticsNode diagnostic = (DiagnosticsNode)userObject;
        myPropertiesPanel.showProperties(diagnostic);
        if (getInspectorService() != null) {
          getInspectorService().maybeSetSelection(diagnostic.getValueRef(), false);
        }
      }
    }
  }

  private void initTree(final Tree tree) {
    final DiagnosticsTreeCellRenderer rootsTreeCellRenderer = new DiagnosticsTreeCellRenderer();
    tree.setCellRenderer(rootsTreeCellRenderer);
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeUtil.installActions(tree);

    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(), ActionManager.getInstance());
  }

  private ActionGroup createTreePopupActions() {
    final DefaultActionGroup group = new DefaultActionGroup();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction(InspectorActions.JUMP_TO_TYPE_SOURCE));
    // TODO(jacobr): add JUMP_TO_SOURCE once we have actual source locations
    // as well as type source locations. This will require at minimum adding
    // a Dart kernel code transformer to track creation locations for widgets.
    /// group.add(actionManager.getAction(InspectorActions.JUMP_TO_SOURCE));
    return group;
  }

  @Override
  public void dispose() {
    // TODO(jacobr): actually implement.
  }

  private static class MyTree extends Tree implements DataProvider, Disposable {
    private MyTree(final DefaultMutableTreeNode treemodel) {
      super(treemodel);
      registerShortcuts();
    }

    void registerShortcuts() {
      DebuggerUIUtil.registerActionOnComponent(InspectorActions.JUMP_TO_TYPE_SOURCE, this, this);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      // TOOD(jacobr): actually perform some custom painting.
      // For example, we should consider custom painting to display offstage objects differently.
      super.paintComponent(g);
    }

    @Override
    public void dispose() {
      // TODO(jacobr): do we have anything to dispose?
    }

    @Nullable
    @Override
    public Object getData(String dataId) {
      if (INSPECTOR_TREE_KEY.is(dataId)) {
        return this;
      }
      if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
        final XValueNodeImpl[] selectedNodes = getSelectedNodes(XValueNodeImpl.class, null);
        if (selectedNodes.length == 1 && selectedNodes[0].getFullValueEvaluator() == null) {
          return DebuggerUIUtil.getNodeRawValue(selectedNodes[0]);
        }
      }
      return null;
    }
  }

  static class PropertyNameColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    private final DiagnosticsTreeCellRenderer renderer;

    public PropertyNameColumnInfo(String name) {
      super(name);
      renderer = new DiagnosticsTreeCellRenderer();
    }

    @Nullable
    @Override
    public String valueOf(DefaultMutableTreeNode node) {
      final DiagnosticsNode userObject = (DiagnosticsNode)node.getUserObject();
      String name = userObject.getName();
      if (name == null) {
        name = "";
      }
      return name;
    }

    @Override
    public int getWidth(JTable table) {
      return 220; // TODO(jacobr): determine a better default.
    }

    /*
    public TableCellRenderer getRenderer(DefaultMutableTreeNode item) {
      return renderer;
    }
    */
  }

  static class PropertyValueColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    public PropertyValueColumnInfo(String name) {
      super(name);
    }

    @Nullable
    @Override
    public String valueOf(DefaultMutableTreeNode node) {
      final DiagnosticsNode diagnostic = (DiagnosticsNode)node.getUserObject();
      return diagnostic.getDescription();
    }
  }

  private static class PropertiesPanel extends TreeTableView {
    PropertiesPanel() {
      super(new ListTreeTableModelOnColumns(
        new DefaultMutableTreeNode(),
        new ColumnInfo[]{new PropertyNameColumnInfo("Property"), new PropertyValueColumnInfo("Value")}
      ));
      setRootVisible(false);
    }

    ListTreeTableModelOnColumns getTreeModel() {
      return (ListTreeTableModelOnColumns)getTableModel();
    }

    public void showProperties(DiagnosticsNode diagnostic) {
      // Temporarily clear.
      getTreeModel().setRoot(new DefaultMutableTreeNode());
      if (diagnostic == null) {
        return;
      }
      whenCompleteUiThread(diagnostic.getProperties(), (ArrayList<DiagnosticsNode> properties, Throwable throwable) -> {
        if (throwable != null) {
          // TODO(jacobr): show error message explaining properties could not
          // be loaded.
          return;
        }
        final ListTreeTableModelOnColumns model = getTreeModel();
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        properties.sort(Comparator.comparing(DiagnosticsNode::getLevel).reversed());
        for (DiagnosticsNode property : properties) {
          if (property.getLevel() != DiagnosticLevel.hidden) {
            root.add(new DefaultMutableTreeNode(property));
          }
        }
        model.setRoot(root);
      });
    }
  }

  private static class DiagnosticsTreeCellRenderer extends ColoredTreeCellRenderer {
    /**
     * Split text into two groups, word characters at the start of a string
     * and all other chracters.
     * This splits t
     */
    private final Pattern primaryDescriptionPattern = Pattern.compile("^(\\w+)(.*)$");

    public void customizeCellRenderer(@NotNull final JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof String) {
        append((String)userObject, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        return;
      }
      if (!(userObject instanceof DiagnosticsNode)) return;
      final DiagnosticsNode node = (DiagnosticsNode)userObject;
      final String name = node.getName();
      SimpleTextAttributes textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      switch (node.getLevel()) {
        case hidden:
        case fine:
          textAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES;
          break;
        case debug:
        case info:
          textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
          break;
        case warning:
          // TODO(jacobr): would be nice to use a yellow color for level warning.
        case error:
          textAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
          break;
      }
      // TODO(jacobr): color based on node level
      if (name != null && !name.isEmpty() && node.getShowName()) {
        // color in name?
        if (textAttributes == SimpleTextAttributes.REGULAR_ATTRIBUTES && name.equals("child")) {
          append(name, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(name, textAttributes);
        }
        if (node.getShowSeparator()) {
          // Is this good?
          append(node.getSeparator(), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        append(" ");
      }
      // TODO(jacobr): custom display for units, colors, iterables, and icons.
      final String description = node.getDescription();
      if (textAttributes == SimpleTextAttributes.REGULAR_ATTRIBUTES) {
        final Matcher match = primaryDescriptionPattern.matcher(description);
        if (match.matches()) {
          append(match.group(1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          append(match.group(2), textAttributes);
        }
        else {
          append(node.getDescription(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
      }
      else {
        append(node.getDescription(), textAttributes);
      }

      if (node.hasTooltip()) {
        setToolTipText(node.getTooltip());
      }

      // TODO(jacobr): set icons for most nodes.
      // Consider icons to cluster logic vs visual nodes, etc.
      // setIcon(node.getIcon());
    }
  }

  boolean placeholderChildren(DefaultMutableTreeNode node) {
    return node.getChildCount() == 0 ||
           (node.getChildCount() == 1 && ((DefaultMutableTreeNode)node.getFirstChild()).getUserObject() instanceof String);
  }

  private class MyTreeExpansionListener implements TreeExpansionListener {
    @Override
    public void treeExpanded(TreeExpansionEvent event) {
      maybeLoadChildren((DefaultMutableTreeNode)event.getPath().getLastPathComponent());
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
    }
  }

  @Nullable
  public static Tree getTree(final DataContext e) {
    return e.getData(INSPECTOR_TREE_KEY);
  }
}
