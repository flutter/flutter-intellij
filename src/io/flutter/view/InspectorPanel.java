/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.google.common.base.Joiner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import io.flutter.FlutterBundle;
import io.flutter.editor.FlutterMaterialIcons;
import io.flutter.inspector.*;
import io.flutter.pub.PubRoot;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.AsyncRateLimiter;
import io.flutter.utils.ColorIconMaker;
import org.dartlang.vm.service.element.InstanceRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.flutter.utils.AsyncUtils.whenCompleteUiThread;

// TODO(devoncarew): Should we filter out the CheckedModeBanner node type?
// TODO(devoncarew): Should we filter out the WidgetInspector node type?

public class InspectorPanel extends JPanel implements Disposable, InspectorService.InspectorServiceClient {

  // TODO(jacobr): use a lower frame rate when the panel is hidden.
  /**
   * Maximum frame rate to refresh the inspector panel at to avoid taxing the
   * physical device with too many requests to recompute properties and trees.
   * <p>
   * A value up to around 30 frames per second could be reasonable for
   * debugging highly interactive cases particularly when the user is on a
   * simulator or high powered native device. The frame rate is set low
   * for now mainly to minimize the risk of unintended consequences.
   */
  public static final double REFRESH_FRAMES_PER_SECOND = 5.0;

  private final TreeDataProvider myRootsTree;
  private final PropertiesPanel myPropertiesPanel;
  private final Computable<Boolean> isApplicable;
  private final InspectorService.FlutterTreeType treeType;
  private final FlutterView flutterView;
  @NotNull
  private final FlutterApp flutterApp;
  private CompletableFuture<DiagnosticsNode> rootFuture;

  private static final DataKey<Tree> INSPECTOR_KEY = DataKey.create("Flutter.InspectorKey");

  // We have to define this because SimpleTextAttributes does not define a
  // value for warnings. This color looks reasonable for warnings both
  // with the Darculaand the default themes.
  private static final SimpleTextAttributes WARNING_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.ORANGE);

  @NotNull
  public FlutterApp getFlutterApp() {
    return flutterApp;
  }

  private DefaultMutableTreeNode selectedNode;

  private CompletableFuture<DiagnosticsNode> pendingSelectionFuture;
  private boolean myIsListening = false;
  private boolean isActive = false;

  private final AsyncRateLimiter refreshRateLimiter;

  private static final Logger LOG = Logger.getInstance(InspectorPanel.class);

  public InspectorPanel(FlutterView flutterView,
                        @NotNull FlutterApp flutterApp,
                        Computable<Boolean> isApplicable,
                        InspectorService.FlutterTreeType treeType) {
    super(new BorderLayout());

    this.treeType = treeType;
    this.flutterView = flutterView;
    this.flutterApp = flutterApp;
    this.isApplicable = isApplicable;

    refreshRateLimiter = new AsyncRateLimiter(REFRESH_FRAMES_PER_SECOND, this::refresh);

    myRootsTree = new TreeDataProvider(new DefaultMutableTreeNode(null), treeType.displayName);
    myRootsTree.addTreeExpansionListener(new MyTreeExpansionListener());
    myPropertiesPanel = new PropertiesPanel();

    initTree(myRootsTree);
    myRootsTree.getSelectionModel().addTreeSelectionListener(e -> selectionChanged());

    final Splitter treeSplitter = new Splitter(true);
    treeSplitter.setProportion(flutterView.getState().getSplitterProportion());
    flutterView.getState().addListener(e -> {
      final float newProportion = flutterView.getState().getSplitterProportion();
      if (treeSplitter.getProportion() != newProportion) {
        treeSplitter.setProportion(newProportion);
      }
    });
    //noinspection Convert2Lambda
    treeSplitter.addPropertyChangeListener("proportion", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        flutterView.getState().setSplitterProportion(treeSplitter.getProportion());
      }
    });

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

  private CompletableFuture<?> refresh() {
    // TODO(jacobr): refresh the tree as well as just the properties.
    return myPropertiesPanel.refresh();
  }

  public void onIsolateStopped() {
    // Make sure we cleanup all references to objects from the stopped isolate as they are now obsolete.
    if (rootFuture != null && !rootFuture.isDone()) {
      rootFuture.cancel(true);
    }
    rootFuture = null;

    if (pendingSelectionFuture != null && !pendingSelectionFuture.isDone()) {
      pendingSelectionFuture.cancel(true);
    }
    pendingSelectionFuture = null;

    selectedNode = null;

    getTreeModel().setRoot(new DefaultMutableTreeNode());
    myPropertiesPanel.showProperties(null);
  }

  public void onAppChanged() {
    setActivate(isApplicable.compute());
  }

  @Nullable
  private InspectorService getInspectorService() {
    return flutterApp.getInspectorService();
  }

  void setActivate(boolean enabled) {
    if (!enabled) {
      onIsolateStopped();
      isActive = false;
      return;
    }
    if (isActive) {
      // Already activated.
      return;
    }

    isActive = true;
    assert (getInspectorService() != null);
    getInspectorService().addClient(this);
    getInspectorService().isWidgetTreeReady().thenAccept((Boolean ready) -> {
      if (ready) {
        recomputeTreeRoot();
      }
    });
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTreeModel().getRoot();
  }

  private CompletableFuture<DiagnosticsNode> recomputeTreeRoot() {
    if (rootFuture != null && !rootFuture.isDone()) {
      return rootFuture;
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
    return rootFuture;
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

  public void onFlutterFrame() {
    if (rootFuture == null) {
      // This was the first frame.
      recomputeTreeRoot();
    }
    refreshRateLimiter.scheduleRequest();
  }

  private boolean identicalDiagnosticsNodes(DiagnosticsNode a, DiagnosticsNode b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.getDartDiagnosticRef().equals(b.getDartDiagnosticRef());
  }

  public void onInspectorSelectionChanged() {
    if (pendingSelectionFuture != null) {
      // Pending selection changed is obsolete.
      if (!pendingSelectionFuture.isDone()) {
        pendingSelectionFuture.cancel(true);
        pendingSelectionFuture = null;
      }
    }
    pendingSelectionFuture = getInspectorService().getSelection(getSelectedDiagnostic(), treeType);
    whenCompleteUiThread(pendingSelectionFuture, (DiagnosticsNode newSelection, Throwable error) -> {
      pendingSelectionFuture = null;
      if (error != null) {
        LOG.error(error);
        return;
      }
      if (newSelection != getSelectedDiagnostic()) {
        if (newSelection == null) {
          myRootsTree.clearSelection();
          return;
        }
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
        if (diagnostic != null) {
          if (isCreatedByLocalProject(diagnostic)) {
            diagnostic.getCreationLocation().getXSourcePosition().createNavigatable(getFlutterApp().getProject())
              .navigate(false);
          }
        }
        myPropertiesPanel.showProperties(diagnostic);
        if (getInspectorService() != null) {
          getInspectorService().setSelection(diagnostic.getValueRef(), false);
        }
      }
    }
  }

  private void initTree(final Tree tree) {
    tree.setCellRenderer(new DiagnosticsTreeCellRenderer());
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeUtil.installActions(tree);

    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(), ActionManager.getInstance());

    new TreeSpeedSearch(tree) {
      @Override
      protected String getElementText(Object element) {
        final TreePath path = (TreePath)element;
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        final Object object = node.getUserObject();
        if (object instanceof DiagnosticsNode) {
          // TODO(pq): consider a specialized String for matching.
          return object.toString();
        }
        return null;
      }
    };
  }

  private ActionGroup createTreePopupActions() {
    final DefaultActionGroup group = new DefaultActionGroup();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction(InspectorActions.JUMP_TO_SOURCE));
    group.add(actionManager.getAction(InspectorActions.JUMP_TO_TYPE_SOURCE));
    return group;
  }

  @Override
  public void dispose() {
    // TODO(jacobr): actually implement.
  }

  private static class TreeDataProvider extends Tree implements DataProvider, Disposable {
    private TreeDataProvider(final DefaultMutableTreeNode treemodel, String treeName) {
      super(treemodel);

      setRootVisible(false);
      registerShortcuts();
      getEmptyText().setText(treeName + " tree for the running app");

      // Decrease indent, scaled for different display types.
      final BasicTreeUI ui = (BasicTreeUI)getUI();
      ui.setRightChildIndent(JBUI.scale(4));
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
      if (INSPECTOR_KEY.is(dataId)) {
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

  static class PropertyNameColumnInfo extends ColumnInfo<DefaultMutableTreeNode, DiagnosticsNode> {
    private final TableCellRenderer renderer = new PropertyNameRenderer();

    public PropertyNameColumnInfo(String name) {
      super(name);
    }

    @Nullable
    @Override
    public DiagnosticsNode valueOf(DefaultMutableTreeNode node) {
      return (DiagnosticsNode)node.getUserObject();
    }

    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode item) {
      return renderer;
    }
  }

  static class PropertyValueColumnInfo extends ColumnInfo<DefaultMutableTreeNode, DiagnosticsNode> {
    private final TableCellRenderer defaultRenderer;

    public PropertyValueColumnInfo(String name) {
      super(name);
      defaultRenderer = new SimplePropertyValueRenderer();
    }

    @Nullable
    @Override
    public DiagnosticsNode valueOf(DefaultMutableTreeNode node) {
      return (DiagnosticsNode)node.getUserObject();
    }

    @Override
    public TableCellRenderer getRenderer(DefaultMutableTreeNode item) {
      return defaultRenderer;
    }
  }

  private static class PropertiesPanel extends TreeTableView implements DataProvider {
    /**
     * Diagnostic we are displaying properties for.
     */
    private DiagnosticsNode diagnostic;

    /**
     * Current properties being displayed.
     */
    private ArrayList<DiagnosticsNode> currentProperties;

    PropertiesPanel() {
      super(new ListTreeTableModelOnColumns(
        new DefaultMutableTreeNode(),
        new ColumnInfo[]{
          new PropertyNameColumnInfo("Property"),
          new PropertyValueColumnInfo("Value")
        }
      ));
      setRootVisible(false);

      setStriped(true);
      setRowHeight(getRowHeight() + JBUI.scale(4));

      final JTableHeader tableHeader = getTableHeader();
      tableHeader.setPreferredSize(new Dimension(0, getRowHeight()));

      getColumnModel().getColumn(0).setPreferredWidth(120);
      getColumnModel().getColumn(1).setPreferredWidth(200);
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

    public void showProperties(DiagnosticsNode diagnostic) {
      this.diagnostic = diagnostic;

      if (diagnostic == null) {
        getEmptyText().setText(FlutterBundle.message("app.inspector.nothing_to_show"));
        getTreeModel().setRoot(new DefaultMutableTreeNode());
        return;
      }
      getEmptyText().setText(FlutterBundle.message("app.inspector.loading_properties"));
      whenCompleteUiThread(diagnostic.getProperties(), (ArrayList<DiagnosticsNode> properties, Throwable throwable) -> {
        if (throwable != null) {
          getTreeModel().setRoot(new DefaultMutableTreeNode());
          getEmptyText().setText(FlutterBundle.message("app.inspector.error_loading_properties"));
          LOG.error(throwable);
          return;
        }
        showPropertiesHelper(properties);
        PopupHandler.installUnknownPopupHandler( this, createTreePopupActions(), ActionManager.getInstance());
      });
    }

    private void showPropertiesHelper(ArrayList<DiagnosticsNode> properties) {
      currentProperties = properties;
      if (properties.size() == 0) {
        getTreeModel().setRoot(new DefaultMutableTreeNode());
        getEmptyText().setText(FlutterBundle.message("app.inspector.no_properties"));
        return;
      }
      CompletableFuture<Void> loaded = loadPropertyMetadata(properties);

      whenCompleteUiThread(loaded, (Void ignored, Throwable errorGettingInstances) -> {
        if (errorGettingInstances != null) {
          // TODO(jacobr): show error message explaining properties could not
          // be loaded.
          getTreeModel().setRoot(new DefaultMutableTreeNode());
          LOG.error(errorGettingInstances);
          getEmptyText().setText(FlutterBundle.message("app.inspector.error_loading_property_details"));
          return;
        }
        setModelFromProperties(properties);
      });
    }

    private void setModelFromProperties(ArrayList<DiagnosticsNode> properties) {
      final ListTreeTableModelOnColumns model = getTreeModel();
      final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
      for (DiagnosticsNode property : properties) {
        if (property.getLevel() != DiagnosticLevel.hidden) {
          root.add(new DefaultMutableTreeNode(property));
        }
      }
      getEmptyText().setText(FlutterBundle.message("app.inspector.all_properties_hidden"));
      model.setRoot(root);
    }

    private CompletableFuture<Void> loadPropertyMetadata(ArrayList<DiagnosticsNode> properties) {
      // Preload all information we need about each property before instantiating
      // the UI so that the property display UI does not have to deal with values
      // that are not yet available. As the number of properties is small this is
      // a reasonable tradeoff.
      final CompletableFuture[] futures = new CompletableFuture[properties.size()];
      int i = 0;
      for (DiagnosticsNode property : properties) {
        futures[i] = property.getValueProperties();
        ++i;
      }
      return CompletableFuture.allOf(futures);
    }

    private CompletableFuture<?> refresh() {
      if (diagnostic == null) {
        final CompletableFuture<Void> refreshComplete = new CompletableFuture<>();
        refreshComplete.complete(null);
        return refreshComplete;
      }
      CompletableFuture<ArrayList<DiagnosticsNode>> propertiesFuture = diagnostic.getProperties();
      whenCompleteUiThread(propertiesFuture, (ArrayList<DiagnosticsNode> properties, Throwable throwable) -> {
        if (throwable != null) {
          return;
        }
        if (propertiesIdentical(properties, currentProperties)) {
          return;
        }
        showPropertiesHelper(properties);
      });
      return propertiesFuture;
    }

    private boolean propertiesIdentical(ArrayList<DiagnosticsNode> a, ArrayList<DiagnosticsNode> b) {
      if (a == b) {
        return true;
      }
      if (a == null || b == null) {
        return false;
      }
      if (a.size() != b.size()) {
        return false;
      }
      for (int i = 0; i < a.size(); ++i) {
        if (!a.get(i).identicalDisplay(b.get(i))) {
          return false;
        }
      }
      return true;
    }

    @Nullable
    @Override
    public Object getData(String dataId) {
      return INSPECTOR_KEY.is(dataId) ? getTree() : null;
    }
  }

  private static SimpleTextAttributes textAttributesForLevel(DiagnosticLevel level) {
    switch (level) {
      case hidden:
      case fine:
        return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      case warning:
        return WARNING_ATTRIBUTES;
      case error:
        return SimpleTextAttributes.ERROR_ATTRIBUTES;
      case debug:
      case info:
      default:
        return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  }

  private static class PropertyNameRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value == null) return;
      final DiagnosticsNode node = (DiagnosticsNode)value;
      // If we should not show a separator then we should show the property name
      // as part of the property value instead of in its own column.
      if (!node.getShowSeparator() || !node.getShowName()) {
        return;
      }
      // Present user defined properties in BOLD.
      final SimpleTextAttributes attributes =
        node.hasCreationLocation() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
      append(node.getName(), attributes);

      // TODO(pq): add property description.
      //setToolTipText(...);
    }
  }

  /**
   * Property value renderer that handles common cases where colored text
   * is sufficient to describe the property value.
   */
  private static class SimplePropertyValueRenderer extends ColoredTableCellRenderer {
    final ColorIconMaker colorIconMaker = new ColorIconMaker();

    private static int getIntProperty(Map<String, InstanceRef> properties, String propertyName) {
      if (properties == null || !properties.containsKey(propertyName)) {
        return 0;
      }
      return Integer.parseInt(properties.get(propertyName).getValueAsString());
    }

    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      setToolTipText(null);
      if (value == null) return;
      final DiagnosticsNode node = (DiagnosticsNode)value;
      final SimpleTextAttributes textAttributes = textAttributesForLevel(node.getLevel());

      boolean appendDescription = true;

      if (node.getTooltip() != null) {
        setToolTipText(node.getTooltip());
      }
      // TODO(jacobr): also provide custom UI display for padding, transform,
      // and alignment properties.
      final CompletableFuture<Map<String, InstanceRef>> propertiesFuture = node.getValueProperties();
      if (propertiesFuture != null && propertiesFuture.isDone() && !propertiesFuture.isCompletedExceptionally()) {
        final Map<String, InstanceRef> properties = propertiesFuture.getNow(null);
        if (node.isEnumProperty() && properties != null) {
          // We can display a better tooltip as we have access to introsection
          // via the observatory service.
          setToolTipText("Allowed values:\n" + Joiner.on('\n').join(properties.keySet()));
        }

        final String propertyType = node.getPropertyType();
        if (propertyType != null) {
          switch (propertyType) {
            case "Color": {
              final int alpha = getIntProperty(properties, "alpha");
              final int red = getIntProperty(properties, "red");
              final int green = getIntProperty(properties, "green");
              final int blue = getIntProperty(properties, "blue");

              //noinspection UseJBColor
              final Color color = new Color(red, green, blue, alpha);
              this.setIcon(colorIconMaker.getCustomIcon(color));
              if (alpha == 255) {
                append(String.format("#%02x%02x%02x", red, green, blue), textAttributes);
              }
              else {
                append(String.format("#%02x%02x%02x%02x", alpha, red, green, blue), textAttributes);
              }

              appendDescription = false;

              break;
            }

            case "IconData": {
              // IconData(U+0E88F)
              final int codePoint = getIntProperty(properties, "codePoint");
              if (codePoint > 0) {
                final Icon icon = FlutterMaterialIcons.getMaterialIconForHex(String.format("%1$04x", codePoint));
                if (icon != null) {
                  this.setIcon(icon);
                  this.setIconOpaque(false);
                  this.setTransparentIconBackground(true);
                }
              }
              break;
            }
          }
        }
      }

      if (appendDescription) {
        append(node.getDescription(), textAttributes);
      }
    }
  }

  private class DiagnosticsTreeCellRenderer extends ColoredTreeCellRenderer {
    /**
     * Split text into two groups, word characters at the start of a string
     * and all other chracters. Skip an <code>-</code> or <code>#</code> between the
     * two groups.
     */
    private final Pattern primaryDescriptionPattern = Pattern.compile("(\\w+)[-#]?(.*)");

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

      this.tree = tree;
      this.selected = selected;

      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof String) {
        appendText((String)userObject, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        return;
      }
      if (!(userObject instanceof DiagnosticsNode)) return;
      final DiagnosticsNode node = (DiagnosticsNode)userObject;
      final String name = node.getName();
      SimpleTextAttributes textAttributes = textAttributesForLevel(node.getLevel());
      if (name != null && !name.isEmpty() && node.getShowName()) {
        // color in name?
        if (name.equals("child") || name.startsWith("child ")) {
          appendText(name, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          appendText(name, textAttributes);
        }

        if (node.getShowSeparator()) {
          // Is this good?
          appendText(node.getSeparator(), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        appendText(" ", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }

      if (isCreatedByLocalProject(node)) {
        textAttributes = textAttributes.derive(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.getStyle(), null, null, null);
      }

      // TODO(jacobr): custom display for units, colors, iterables, and icons.
      final String description = node.getDescription();
      final Matcher match = primaryDescriptionPattern.matcher(description);
      if (match.matches()) {
        appendText(match.group(1), textAttributes);
        appendText(" ", textAttributes);
        appendText(match.group(2), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else {
        appendText(node.getDescription(), textAttributes);
      }

      if (node.hasTooltip()) {
        setToolTipText(node.getTooltip());
      }

      final Icon icon = node.getIcon();
      if (icon != null) {
        setIcon(icon);
      }
    }

    private void appendText(@NotNull String text, @NotNull SimpleTextAttributes attributes) {
      SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, text, attributes, selected, this);
    }
  }

  boolean isCreatedByLocalProject(DiagnosticsNode node) {
    final Location location = node.getCreationLocation();
    if (location == null) {
      return false;
    }
    final VirtualFile file = location.getFile();
    if (file == null) {
      return false;
    }
    final String filePath = file.getCanonicalPath();
    for (PubRoot root : getFlutterApp().getPubRoots()) {
      if (filePath.startsWith(root.getRoot().getCanonicalPath())) {
        return true;
      }
    }
    return false;
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
    return e.getData(INSPECTOR_KEY);
  }
}
