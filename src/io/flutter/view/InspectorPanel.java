/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XSourcePosition;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.editor.FlutterMaterialIcons;
import io.flutter.inspector.*;
import io.flutter.pub.PubRoot;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.*;
import org.dartlang.vm.service.element.InstanceRef;
import org.dartlang.vm.service.element.IsolateRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class InspectorPanel extends JPanel implements Disposable, InspectorService.InspectorServiceClient, InspectorTabPanel {
  /**
   * Maximum frame rate to refresh the inspector panel at to avoid taxing the
   * physical device with too many requests to recompute properties and trees.
   * <p>
   * A value up to around 30 frames per second could be reasonable for
   * debugging highly interactive cases particularly when the user is on a
   * simulator or high powered native device. The frame rate is set low
   * for now mainly to minimize the risk of unintended consequences.
   */
  public static final double REFRESH_FRAMES_PER_SECOND = 2.0;
  // We have to define this because SimpleTextAttributes does not define a
  // value for warnings.
  private static final SimpleTextAttributes WARNING_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.ORANGE);
  private static final Logger LOG = Logger.getInstance(InspectorPanel.class);
  protected final boolean detailsSubtree;
  protected final boolean isSummaryTree;
  /**
   * Parent InspectorPanel if this is a details subtree
   */
  @Nullable protected final InspectorPanel parentTree;
  protected final InspectorPanel subtreePanel;
  final CustomIconMaker iconMaker = new CustomIconMaker();
  final Splitter treeSplitter;
  final Icon defaultIcon;
  final JBScrollPane treeScrollPane;
  private final InspectorTree myRootsTree;
  @Nullable private final PropertiesPanel myPropertiesPanel;
  private final Computable<Boolean> isApplicable;
  private final InspectorService.FlutterTreeType treeType;
  @NotNull
  private final FlutterApp flutterApp;
  @NotNull private final InspectorService inspectorService;
  private final StreamSubscription<IsolateRef> flutterIsolateSubscription;
  private final TreeScrollAnimator scrollAnimator;
  /**
   * Mode with a tree view and a property table instead of a details tree and
   * a summary tree.
   */
  private final boolean legacyMode;
  private final AsyncRateLimiter refreshRateLimiter;

  /**
   * Groups used to manage and cancel requests to load data to display directly
   * in the tree.
   */
  private final InspectorObjectGroupManager treeGroups;

  /**
   * Groups used to manage and cancel requests to determine what the current
   * selection is.
   * <p>
   * This group needs to be kept separate from treeGroups as the selection is
   * shared more with the details subtree.
   * TODO(jacobr): is there a way we can unify the selection and tree groups?
   */
  private final InspectorObjectGroupManager selectionGroups;

  /**
   * Node being highlighted due to the current hover.
   */
  protected DefaultMutableTreeNode currentShowNode;
  boolean flutterAppFrameReady = false;
  private boolean treeLoadStarted = false;
  private DiagnosticsNode subtreeRoot;
  private boolean programaticSelectionChangeInProgress = false;
  private boolean programaticExpansionInProgress = false;

  private DefaultMutableTreeNode selectedNode;
  private DefaultMutableTreeNode lastExpanded;
  private boolean isActive = false;
  private final Map<InspectorInstanceRef, DefaultMutableTreeNode> valueToTreeNode = new HashMap<>();

  /**
   * When visibleToUser is false we should dispose all allocated objects and
   * not perform any actions.
   */
  private boolean visibleToUser = false;
  private boolean highlightNodesShownInBothTrees = false;

  public InspectorPanel(FlutterView flutterView,
                        @NotNull FlutterApp flutterApp,
                        @NotNull InspectorService inspectorService,
                        @NotNull Computable<Boolean> isApplicable,
                        @NotNull InspectorService.FlutterTreeType treeType,
                        boolean isSummaryTree,
                        boolean legacyMode,
                        @NotNull EventStream<Boolean> shouldAutoHorizontalScroll,
                        @NotNull EventStream<Boolean> highlightNodesShownInBothTrees
  ) {
    this(flutterView, flutterApp, inspectorService, isApplicable, treeType, false, null, isSummaryTree, legacyMode,
         shouldAutoHorizontalScroll, highlightNodesShownInBothTrees);
  }

  private InspectorPanel(FlutterView flutterView,
                         @NotNull FlutterApp flutterApp,
                         @NotNull InspectorService inspectorService,
                         @NotNull Computable<Boolean> isApplicable,
                         @NotNull InspectorService.FlutterTreeType treeType,
                         boolean detailsSubtree,
                         @Nullable InspectorPanel parentTree,
                         boolean isSummaryTree,
                         boolean legacyMode,
                         @NotNull EventStream<Boolean> shouldAutoHorizontalScroll,
                         @NotNull EventStream<Boolean> highlightNodesShownInBothTrees) {
    super(new BorderLayout());

    this.treeType = treeType;
    this.flutterApp = flutterApp;
    this.inspectorService = inspectorService;
    this.treeGroups = new InspectorObjectGroupManager(inspectorService, "tree");
    this.selectionGroups = new InspectorObjectGroupManager(inspectorService, "selection");
    this.isApplicable = isApplicable;
    this.detailsSubtree = detailsSubtree;
    this.isSummaryTree = isSummaryTree;
    this.parentTree = parentTree;
    this.legacyMode = legacyMode;

    this.defaultIcon = iconMaker.fromInfo("Default");

    refreshRateLimiter = new AsyncRateLimiter(REFRESH_FRAMES_PER_SECOND, this::refresh, flutterApp);

    final String parentTreeDisplayName = (parentTree != null) ? parentTree.treeType.displayName : null;

    myRootsTree = new InspectorTree(
      new DefaultMutableTreeNode(null),
      treeType.displayName,
      detailsSubtree,
      parentTreeDisplayName,
      treeType != InspectorService.FlutterTreeType.widget || (!isSummaryTree && !legacyMode),
      legacyMode,
      flutterApp
    );

    // We want to reserve double clicking for navigation within the detail
    // tree and in the future for editing values in the tree.
    myRootsTree.setHorizontalAutoScrollingEnabled(false);
    myRootsTree.setAutoscrolls(false);
    myRootsTree.setToggleClickCount(0);

    myRootsTree.addTreeExpansionListener(new MyTreeExpansionListener());
    final InspectorTreeMouseListener mouseListener = new InspectorTreeMouseListener(this, myRootsTree);
    myRootsTree.addMouseListener(mouseListener);
    myRootsTree.addMouseMotionListener(mouseListener);

    if (isSummaryTree && !legacyMode) {
      subtreePanel = new InspectorPanel(
        flutterView,
        flutterApp,
        inspectorService,
        isApplicable,
        treeType,
        true,
        this,
        false,
        legacyMode,
        shouldAutoHorizontalScroll,
        highlightNodesShownInBothTrees
      );
    }
    else {
      subtreePanel = null;
    }

    initTree(myRootsTree);
    myRootsTree.getSelectionModel().addTreeSelectionListener(this::selectionChanged);

    treeScrollPane = (JBScrollPane)ScrollPaneFactory.createScrollPane(myRootsTree);
    treeScrollPane.setAutoscrolls(false);

    scrollAnimator = new TreeScrollAnimator(myRootsTree, treeScrollPane);
    shouldAutoHorizontalScroll.listen(scrollAnimator::setAutoHorizontalScroll, true);
    highlightNodesShownInBothTrees.listen(this::setHighlightNodesShownInBothTrees, true);
    myRootsTree.setScrollAnimator(scrollAnimator);

    if (!detailsSubtree) {
      treeSplitter = new Splitter(false);
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

      if (subtreePanel == null) {
        myPropertiesPanel = new PropertiesPanel(flutterApp, inspectorService);
        treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myPropertiesPanel));
      }
      else {
        myPropertiesPanel = null; /// This InspectorPanel doesn't have its own property panel.
        treeSplitter.setSecondComponent(subtreePanel);
      }

      Disposer.register(this, treeSplitter::dispose);
      Disposer.register(this, scrollAnimator::dispose);
      treeSplitter.setFirstComponent(treeScrollPane);
      add(treeSplitter);
    }
    else {
      treeSplitter = null;
      myPropertiesPanel = null;
      add(treeScrollPane);
    }

    this.addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
        determineSplitterOrientation();
      }

      @Override
      public void componentMoved(ComponentEvent e) {

      }

      @Override
      public void componentShown(ComponentEvent e) {
        determineSplitterOrientation();
      }

      @Override
      public void componentHidden(ComponentEvent e) {
      }
    });

    determineSplitterOrientation();

    flutterIsolateSubscription = inspectorService.getApp().getVMServiceManager().getCurrentFlutterIsolate((IsolateRef flutterIsolate) -> {
      if (flutterIsolate == null) {
        onIsolateStopped();
      }
    }, true);
  }

  @VisibleForTesting
  public boolean isDetailsSubtree() {
    return detailsSubtree;
  }

  @VisibleForTesting
  public boolean isSummaryTree() {
    return isSummaryTree;
  }

  public boolean isHighlightNodesShownInBothTrees() {
    return highlightNodesShownInBothTrees;
  }

  private void setHighlightNodesShownInBothTrees(boolean value) {
    if (highlightNodesShownInBothTrees != value) {
      highlightNodesShownInBothTrees = value;
      myRootsTree.repaint();
    }
  }

  static DiagnosticsNode getDiagnosticNode(TreeNode treeNode) {
    if (!(treeNode instanceof DefaultMutableTreeNode)) {
      return null;
    }
    final Object userData = ((DefaultMutableTreeNode)treeNode).getUserObject();
    return userData instanceof DiagnosticsNode ? (DiagnosticsNode)userData : null;
  }

  private static void expandAll(JTree tree, TreePath parent, boolean expandProperties) {
    final TreeNode node = (TreeNode)parent.getLastPathComponent();
    if (node.getChildCount() >= 0) {
      for (final Enumeration e = node.children(); e.hasMoreElements(); ) {
        final DefaultMutableTreeNode n = (DefaultMutableTreeNode)e.nextElement();
        if (n.getUserObject() instanceof DiagnosticsNode) {
          final DiagnosticsNode diagonsticsNode = (DiagnosticsNode)n.getUserObject();
          if (!diagonsticsNode.childrenReady() ||
              (diagonsticsNode.isProperty() && !expandProperties)) {
            continue;
          }
        }
        expandAll(tree, parent.pathByAddingChild(n), expandProperties);
      }
    }
    tree.expandPath(parent);
  }

  protected static SimpleTextAttributes textAttributesForLevel(DiagnosticLevel level) {
    switch (level) {
      case hidden:
        return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      case fine:
        return SimpleTextAttributes.REGULAR_ATTRIBUTES;
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

  @Nullable
  public static Tree getTree(final DataContext e) {
    return e.getData(InspectorTree.INSPECTOR_KEY);
  }

  @NotNull
  public FlutterApp getFlutterApp() {
    return flutterApp;
  }

  public InspectorService.FlutterTreeType getTreeType() {
    return treeType;
  }

  public void setVisibleToUser(boolean visible) {
    if (visibleToUser == visible) {
      return;
    }
    visibleToUser = visible;

    if (subtreePanel != null) {
      subtreePanel.setVisibleToUser(visible);
    }
    if (visibleToUser) {
      if (parentTree == null) {
        maybeLoadUI();
      }
    }
    else {
      shutdownTree(false);
    }
  }

  private void determineSplitterOrientation() {
    if (treeSplitter == null) {
      return;
    }
    final double aspectRatio = (double)getWidth() / (double)getHeight();
    final boolean vertical = aspectRatio < 1.4;
    if (vertical != treeSplitter.getOrientation()) {
      treeSplitter.setOrientation(vertical);
    }
  }

  protected boolean hasDiagnosticsValue(InspectorInstanceRef ref) {
    return valueToTreeNode.containsKey(ref);
  }

  protected DiagnosticsNode findDiagnosticsValue(InspectorInstanceRef ref) {
    return getDiagnosticNode(valueToTreeNode.get(ref));
  }

  protected void endShowNode() {
    highlightShowNode((DefaultMutableTreeNode)null);
  }

  protected boolean highlightShowNode(InspectorInstanceRef ref) {
    return highlightShowNode(valueToTreeNode.get(ref));
  }

  protected boolean highlightShowNode(DefaultMutableTreeNode node) {
    if (node == null && parentTree != null) {
      // If nothing is highlighted, highlight the node selected in the parent
      // tree so user has context of where the node selected in the parent is
      // in the details tree.
      node = findMatchingTreeNode(parentTree.getSelectedDiagnostic());
    }

    getTreeModel().nodeChanged(currentShowNode);
    getTreeModel().nodeChanged(node);
    currentShowNode = node;
    return true;
  }

  private DefaultMutableTreeNode findMatchingTreeNode(DiagnosticsNode node) {
    if (node == null) {
      return null;
    }
    return valueToTreeNode.get(node.getValueRef());
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)myRootsTree.getModel();
  }

  private CompletableFuture<?> getPendingUpdateDone() {
    // Wait for the selection to be resolved followed by waiting for the tree to be computed.
    final CompletableFuture<?> ret = new CompletableFuture<>();
    AsyncUtils.whenCompleteUiThread(selectionGroups.getPendingUpdateDone(), (value, error) -> {
      treeGroups.getPendingUpdateDone().whenCompleteAsync((value2, error2) -> {
        ret.complete(null);
      });
    });
    return ret;
  }

  private CompletableFuture<?> refresh() {
    if (!visibleToUser) {
      // We will refresh again once we are visible.
      // There is a risk a refresh got triggered before the view was visble.
      return CompletableFuture.completedFuture(null);
    }

    // TODO(jacobr): refresh the tree as well as just the properties.
    if (myPropertiesPanel != null) {
      myPropertiesPanel.refresh();
    }
    if (myPropertiesPanel != null) {
      return CompletableFuture.allOf(getPendingUpdateDone(), myPropertiesPanel.getPendingUpdateDone());
    }
    else if (subtreePanel != null) {
      return CompletableFuture.allOf(getPendingUpdateDone(), subtreePanel.getPendingUpdateDone());
    }
    else {
      return getPendingUpdateDone();
    }
  }

  public void shutdownTree(boolean isolateStopped) {
    // It is critical we clear all data that is kept alive by inspector object
    // references in this method as that stale data will trigger inspector
    // exceptions.
    programaticSelectionChangeInProgress = true;
    treeGroups.clear(isolateStopped);
    selectionGroups.clear(isolateStopped);

    currentShowNode = null;
    selectedNode = null;
    lastExpanded = null;

    subtreeRoot = null;

    getTreeModel().setRoot(new DefaultMutableTreeNode());
    if (subtreePanel != null) {
      subtreePanel.shutdownTree(isolateStopped);
    }
    if (myPropertiesPanel != null) {
      myPropertiesPanel.shutdownTree(isolateStopped);
    }
    programaticSelectionChangeInProgress = false;
    valueToTreeNode.clear();
  }

  public void onIsolateStopped() {
    flutterAppFrameReady = false;
    treeLoadStarted = false;
    shutdownTree(true);
  }

  @Override
  public CompletableFuture<?> onForceRefresh() {
    if (!visibleToUser) {
      return CompletableFuture.completedFuture(null);
    }
    if (!legacyMode) {
      // We can't efficiently refresh the full tree in legacy mode.
      recomputeTreeRoot(null, null, false, true);
    }
    if (myPropertiesPanel != null) {
      myPropertiesPanel.refresh();
    }

    return getPendingUpdateDone();
  }

  public void onAppChanged() {
    setActivate(isApplicable.compute());
  }

  @NotNull
  private InspectorService getInspectorService() {
    return inspectorService;
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
    getInspectorService().addClient(this);
    maybeLoadUI();
  }

  void maybeLoadUI() {
    if (!visibleToUser || !isActive) {
      return;
    }

    if (flutterAppFrameReady) {
      // We need to start by quering the inspector service to find out the
      // current state of the UI.
      AsyncUtils.whenCompleteUiThread(inspectorService.inferPubRootDirectoryIfNeeded(), (String directory, Throwable throwable) -> {
        // Ignore exceptions, we still want to show the Inspector View.
        updateSelectionFromService(false);
      });
    }
    else {
      AsyncUtils.whenCompleteUiThread(inspectorService.isWidgetTreeReady(), (Boolean ready, Throwable throwable) -> {
        if (throwable != null) {
          return;
        }
        flutterAppFrameReady = ready;
        if (isActive && ready) {
          maybeLoadUI();
        }
      });
    }
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTreeModel().getRoot();
  }

  private void recomputeTreeRoot(DiagnosticsNode newSelection,
                                 DiagnosticsNode detailsSelection,
                                 boolean setSubtreeRoot,
                                 boolean textEditorUpdated) {
    treeGroups.cancelNext();
    treeGroups.getNext().safeWhenComplete(detailsSubtree
                                          ? treeGroups.getNext().getDetailsSubtree(subtreeRoot)
                                          : treeGroups.getNext().getRoot(treeType), (final DiagnosticsNode n, Throwable error) -> {
      if (error != null) {
        FlutterUtils.warn(LOG, error);
        treeGroups.cancelNext();
        return;
      }
      // TODO(jacobr): as a performance optimization we should check if the
      // new tree is identical to the existing tree in which case we should
      // dispose the new tree and keep the old tree.
      treeGroups.promoteNext();
      clearValueToTreeNodeMapping();
      if (n != null) {
        final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(n);
        getTreeModel().setRoot(rootNode);
        setupTreeNode(rootNode, n, true);

        // Legacy case. We got the root node but no children are loaded yet.
        // When the root node is hidden, we will never show anything unless we
        // load the children.
        if (n.hasChildren() && !n.childrenReady()) {
          maybeLoadChildren(rootNode);
        }
      }
      else {
        getTreeModel().setRoot(null);
      }
      refreshSelection(newSelection, detailsSelection, setSubtreeRoot, textEditorUpdated);
    });
  }

  private void clearValueToTreeNodeMapping() {
    if (parentTree != null) {
      for (InspectorInstanceRef v : valueToTreeNode.keySet()) {
        parentTree.maybeUpdateValueUI(v);
      }
    }
    valueToTreeNode.clear();
  }

  /**
   * Show the details subtree starting with node subtreeRoot highlighting
   * node subtreeSelection.
   */
  public void showDetailSubtrees(DiagnosticsNode subtreeRoot, DiagnosticsNode subtreeSelection) {
    // TODO(jacobr): handle render objects subtree panel and other subtree panels here.
    assert (!legacyMode);

    this.subtreeRoot = subtreeRoot;
    myRootsTree.setHighlightedRoot(getSubtreeRootNode());
    if (subtreePanel != null) {
      subtreePanel.setSubtreeRoot(subtreeRoot, subtreeSelection);
    }
  }

  public InspectorInstanceRef getSubtreeRootValue() {
    return subtreeRoot != null ? subtreeRoot.getValueRef() : null;
  }

  public void setSubtreeRoot(DiagnosticsNode node, DiagnosticsNode selection) {
    assert (detailsSubtree);
    if (selection == null) {
      selection = node;
    }
    if (node != null && node.equals(subtreeRoot)) {
      //  Select the new node in the existing subtree.
      applyNewSelection(selection, null, false, true);
      return;
    }
    subtreeRoot = node;
    if (node == null) {
      // Passing in a null node indicates we should clear the subtree and free any memory allocated.
      shutdownTree(false);
      return;
    }

    // Clear now to eliminate frame of highlighted nodes flicker.
    clearValueToTreeNodeMapping();
    recomputeTreeRoot(selection, null, false, true);
  }

  DefaultMutableTreeNode getSubtreeRootNode() {
    if (subtreeRoot == null) {
      return null;
    }
    return valueToTreeNode.get(subtreeRoot.getValueRef());
  }

  private void refreshSelection(DiagnosticsNode newSelection, DiagnosticsNode detailsSelection, boolean setSubtreeRoot, boolean textEditorUpdated) {
    if (newSelection == null) {
      newSelection = getSelectedDiagnostic();
    }
    setSelectedNode(findMatchingTreeNode(newSelection));
    syncSelectionHelper(setSubtreeRoot, detailsSelection, textEditorUpdated);

    if (subtreePanel != null) {
      if ((subtreeRoot != null && getSubtreeRootNode() == null)) {
        subtreeRoot = newSelection;
        subtreePanel.setSubtreeRoot(newSelection, detailsSelection);
      }
    }
    if (!legacyMode) {
      myRootsTree.setHighlightedRoot(getSubtreeRootNode());
    }

    syncTreeSelection();
  }

  private void syncTreeSelection() {
    programaticSelectionChangeInProgress = true;
    final TreePath path = selectedNode != null ? new TreePath(selectedNode.getPath()) : null;
    myRootsTree.setSelectionPath(path);
    programaticSelectionChangeInProgress = false;
    myRootsTree.expandPath(path);
    animateTo(selectedNode);
  }

  private void selectAndShowNode(DiagnosticsNode node) {
    if (node == null) {
      return;
    }
    selectAndShowNode(node.getValueRef());
  }

  private void selectAndShowNode(InspectorInstanceRef ref) {
    final DefaultMutableTreeNode node = valueToTreeNode.get(ref);
    if (node == null) {
      return;
    }
    setSelectedNode(node);
    syncTreeSelection();
  }

  private TreePath getTreePath(DiagnosticsNode node) {
    if (node == null) {
      return null;
    }
    final DefaultMutableTreeNode treeNode = valueToTreeNode.get(node.getValueRef());
    if (treeNode == null) {
      return null;
    }
    return new TreePath(treeNode.getPath());
  }

  protected void maybeUpdateValueUI(InspectorInstanceRef valueRef) {
    final DefaultMutableTreeNode node = valueToTreeNode.get(valueRef);
    if (node == null) {
      // The value isn't shown in the parent tree. Nothing to do.
      return;
    }
    getTreeModel().nodeChanged(node);
  }

  void setupTreeNode(DefaultMutableTreeNode node, DiagnosticsNode diagnosticsNode, boolean expandChildren) {
    node.setUserObject(diagnosticsNode);
    node.setAllowsChildren(diagnosticsNode.hasChildren());
    final InspectorInstanceRef valueRef = diagnosticsNode.getValueRef();
    // Properties do not have unique values so should not go in the valueToTreeNode map.
    if (valueRef.getId() != null && !diagnosticsNode.isProperty()) {
      valueToTreeNode.put(valueRef, node);
    }
    if (parentTree != null) {
      parentTree.maybeUpdateValueUI(valueRef);
    }
    if (diagnosticsNode.hasChildren() || !diagnosticsNode.getInlineProperties().isEmpty()) {
      if (diagnosticsNode.childrenReady() || !diagnosticsNode.hasChildren()) {
        final CompletableFuture<ArrayList<DiagnosticsNode>> childrenFuture = diagnosticsNode.getChildren();
        assert (childrenFuture.isDone());
        setupChildren(diagnosticsNode, node, childrenFuture.getNow(null), expandChildren);
      }
      else {
        node.removeAllChildren();
        node.add(new DefaultMutableTreeNode("Loading..."));
      }
    }
  }

  void setupChildren(DiagnosticsNode parent, DefaultMutableTreeNode treeNode, ArrayList<DiagnosticsNode> children, boolean expandChildren) {
    final DefaultTreeModel model = getTreeModel();
    if (treeNode.getChildCount() > 0) {
      // Only case supported is this is the loading node.
      assert (treeNode.getChildCount() == 1);
      model.removeNodeFromParent((DefaultMutableTreeNode)treeNode.getFirstChild());
    }
    final ArrayList<DiagnosticsNode> inlineProperties = parent.getInlineProperties();
    treeNode.setAllowsChildren(!children.isEmpty() || !inlineProperties.isEmpty());

    if (inlineProperties != null) {
      for (DiagnosticsNode property : inlineProperties) {
        final DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode();
        setupTreeNode(childTreeNode, property, false);
        childTreeNode.setAllowsChildren(childTreeNode.getChildCount() > 0);
        model.insertNodeInto(childTreeNode, treeNode, treeNode.getChildCount());
      }
    }
    for (DiagnosticsNode child : children) {
      final DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode();
      setupTreeNode(childTreeNode, child, false);
      model.insertNodeInto(childTreeNode, treeNode, treeNode.getChildCount());
    }
    if (expandChildren) {
      programaticExpansionInProgress = true;
      expandAll(myRootsTree, new TreePath(treeNode.getPath()), false);
      programaticExpansionInProgress = false;
    }
  }

  void maybeLoadChildren(DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof DiagnosticsNode)) {
      return;
    }
    final DiagnosticsNode diagnosticsNode = (DiagnosticsNode)node.getUserObject();
    if (diagnosticsNode.hasChildren() || !diagnosticsNode.getInlineProperties().isEmpty()) {
      if (hasPlaceholderChildren(node)) {
        diagnosticsNode.safeWhenComplete(diagnosticsNode.getChildren(), (ArrayList<DiagnosticsNode> children, Throwable throwable) -> {
          if (throwable != null) {
            // TODO(jacobr): Display that children failed to load.
            return;
          }
          if (node.getUserObject() != diagnosticsNode) {
            // Node changed, this data is stale.
            return;
          }
          setupChildren(diagnosticsNode, node, children, true);
          if (node == selectedNode || node == lastExpanded) {
            animateTo(node);
          }
        });
      }
    }
  }

  public void onFlutterFrame() {
    flutterAppFrameReady = true;
    if (!visibleToUser) {
      return;
    }

    if (!treeLoadStarted) {
      treeLoadStarted = true;
      // This was the first frame.
      maybeLoadUI();
    }
    refreshRateLimiter.scheduleRequest();
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean identicalDiagnosticsNodes(DiagnosticsNode a, DiagnosticsNode b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.getDartDiagnosticRef().equals(b.getDartDiagnosticRef());
  }

  public void onInspectorSelectionChanged(boolean uiAlreadyUpdated, boolean textEditorUpdated) {
    if (uiAlreadyUpdated) return;
    if (!visibleToUser) {
      // Don't do anything. We will update the view once it is visible again.
      return;
    }
    if (detailsSubtree) {
      // Wait for the master to update.
      return;
    }
    updateSelectionFromService(textEditorUpdated);
  }

  public void updateSelectionFromService(boolean textEditorUpdated) {
    treeLoadStarted = true;
    selectionGroups.cancelNext();

    final CompletableFuture<DiagnosticsNode> pendingSelectionFuture =
      selectionGroups.getNext().getSelection(getSelectedDiagnostic(), treeType, isSummaryTree);

    CompletableFuture<?> allPending;
    final CompletableFuture<DiagnosticsNode> pendingDetailsFuture =
      isSummaryTree ? selectionGroups.getNext().getSelection(getSelectedDiagnostic(), treeType, false) : null;

    final CompletableFuture<?> selectionsReady =
      isSummaryTree ? CompletableFuture.allOf(pendingDetailsFuture, pendingSelectionFuture) : pendingSelectionFuture;
    selectionGroups.getNext().safeWhenComplete(selectionsReady, (ignored, error) -> {
      if (error != null) {
        FlutterUtils.warn(LOG, error);
        selectionGroups.cancelNext();
        return;
      }

      selectionGroups.promoteNext();

      final DiagnosticsNode newSelection = pendingSelectionFuture.getNow(null);

      if (!legacyMode) {
        DiagnosticsNode detailsSelection = null;
        if (pendingDetailsFuture != null) {
          detailsSelection = pendingDetailsFuture.getNow(null);
        }
        subtreeRoot = newSelection;

        applyNewSelection(newSelection, detailsSelection, true, textEditorUpdated);
      }
      else {
        // Legacy case. TODO(jacobr): deprecate and remove this code.
        // This case only exists for the RenderObject tree which we haven't updated yet to use the new UI style.
        // TODO(jacobr): delete it.
        if (newSelection == null) {
          recomputeTreeRoot(null, null, false, true);
          return;
        }
        // TODO(jacobr): switch to using current and next groups in this case
        // as well to avoid memory leaks.
        treeGroups.getCurrent()
          .safeWhenComplete(treeGroups.getCurrent().getParentChain(newSelection), (ArrayList<DiagnosticsPathNode> path, Throwable ex) -> {
            if (ex != null) {
              FlutterUtils.warn(LOG, ex);
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
              if (!identicalDiagnosticsNodes(pathDiagnosticNode, existingNode)) {
                treeNode.setUserObject(pathDiagnosticNode);
              }
              treeNode.setAllowsChildren(!newChildren.isEmpty());
              for (int j = 0; j < newChildren.size(); ++j) {
                final DiagnosticsNode newChild = newChildren.get(j);
                if (j >= treeNode.getChildCount() || !identicalDiagnosticsNodes(newChild, getDiagnosticNode(treeNode.getChildAt(j)))) {
                  final DefaultMutableTreeNode child;
                  if (j >= treeNode.getChildCount()) {
                    child = new DefaultMutableTreeNode();
                    treeNode.add(child);
                  }
                  else {
                    child = (DefaultMutableTreeNode)treeNode.getChildAt(j);
                  }
                  if (j != pathNode.getChildIndex()) {
                    setupTreeNode(child, newChild, false);
                    model.reload(child);
                  }
                  else {
                    child.setUserObject(newChild);
                    child.setAllowsChildren(newChild.hasChildren());
                    child.removeAllChildren();
                  }

                  // TODO(jacobr): we are likely calling the wrong node structure changed APIs.
                  // For example, we should be getting these change notifications for free if we
                  // switched to call methods on the model object directly to manipulate the tree.
                  model.nodeStructureChanged(child);
                  model.nodeChanged(child);
                }
                model.reload(treeNode);
              }
              if (i != path.size() - 1) {
                treeNode = (DefaultMutableTreeNode)treeNode.getChildAt(pathNode.getChildIndex());
              }
            }
            // TODO(jacobr): review and simplify this.
            final TreePath selectionPath = new TreePath(treePath);
            myRootsTree.setSelectionPath(selectionPath);
            animateTo(treePath[treePath.length - 1]);
          });
      }
    });
  }

  protected void applyNewSelection(DiagnosticsNode newSelection,
                                   DiagnosticsNode detailsSelection,
                                   boolean setSubtreeRoot,
                                   boolean textEditorUpdated) {
    final DefaultMutableTreeNode nodeInTree = findMatchingTreeNode(newSelection);

    if (nodeInTree == null) {
      // The tree has probably changed since we last updated. Do a full refresh
      // so that the tree includes the new node we care about.
      recomputeTreeRoot(newSelection, detailsSelection, setSubtreeRoot, textEditorUpdated);
    }

    refreshSelection(newSelection, detailsSelection, setSubtreeRoot, textEditorUpdated);
  }

  private DiagnosticsNode getSelectedDiagnostic() {
    return getDiagnosticNode(selectedNode);
  }

  private void animateTo(DefaultMutableTreeNode node) {
    if (node == null) {
      return;
    }
    final List<TreePath> targets = new ArrayList<>();
    final TreePath target = new TreePath(node.getPath());
    targets.add(target);

    // Backtrack to the the first non-property parent so that all properties
    // for the node are visible if one property is animated to. This is helpful
    // as typically users want to view the properties of a node as a chunk.
    for (int i = target.getPathCount() - 1; i >= 0; i--) {
      node = (DefaultMutableTreeNode)target.getPathComponent(i);
      final Object userObject = node.getUserObject();
      if (userObject instanceof DiagnosticsNode && !((DiagnosticsNode)userObject).isProperty()) {
        break;
      }
    }
    // Make sure we scroll so that immediate un-expanded children
    // are also in view. There is no risk in including these children as
    // the amount of space they take up is bounded. This also ensures that if
    // a node is selected, its properties will also be selected as by
    // convention properties are the first children of a node and properties
    // typically do not have children and are never expanded by default.
    for (int i = 0; i < node.getChildCount(); ++i) {
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
      final DiagnosticsNode diagnosticsNode = TreeUtils.maybeGetDiagnostic(child);
      final TreePath childPath = new TreePath(child.getPath());
      targets.add(childPath);
      if (!child.isLeaf() && myRootsTree.isExpanded(childPath)) {
        // Stop if we get to expanded children as they might be too large
        // to try to scroll into view.
        break;
      }
      if (diagnosticsNode != null && !diagnosticsNode.isProperty()) {
        break;
      }
    }
    scrollAnimator.animateTo(targets);
  }

  private void maybePopulateChildren(DefaultMutableTreeNode treeNode) {
    final Object userObject = treeNode.getUserObject();
    if (userObject instanceof DiagnosticsNode) {
      final DiagnosticsNode diagnostic = (DiagnosticsNode)userObject;
      if (diagnostic.hasChildren() && treeNode.getChildCount() == 0) {
        diagnostic.safeWhenComplete(diagnostic.getChildren(), (ArrayList<DiagnosticsNode> children, Throwable throwable) -> {
          if (throwable != null) {
            FlutterUtils.warn(LOG, throwable);
            return;
          }
          if (treeNode.getChildCount() == 0) {
            setupChildren(diagnostic, treeNode, children, true);
          }
          getTreeModel().nodeStructureChanged(treeNode);
          if (treeNode == selectedNode) {
            myRootsTree.expandPath(new TreePath(treeNode.getPath()));
          }
        });
      }
    }
  }

  private void setSelectedNode(DefaultMutableTreeNode newSelection) {
    if (newSelection == selectedNode) {
      return;
    }
    if (selectedNode != null) {
      if (!detailsSubtree) {
        getTreeModel().nodeChanged(selectedNode.getParent());
      }
    }
    selectedNode = newSelection;
    animateTo(selectedNode);

    lastExpanded = null; // New selected node takes prescidence.
    endShowNode();
    if (subtreePanel != null) {
      subtreePanel.endShowNode();
    }
    else if (parentTree != null) {
      parentTree.endShowNode();
    }
  }

  private void selectionChanged(TreeSelectionEvent event) {
    if (!visibleToUser) {
      return;
    }

    final DefaultMutableTreeNode[] selectedNodes = myRootsTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
    for (DefaultMutableTreeNode node : selectedNodes) {
      maybePopulateChildren(node);
    }
    if (programaticSelectionChangeInProgress) {
      return;
    }
    if (selectedNodes.length > 0) {
      assert (selectedNodes.length == 1);
      setSelectedNode(selectedNodes[0]);

      final DiagnosticsNode selectedDiagnostic = getSelectedDiagnostic();
      // Don't reroot if the selected value is already visible in the details tree.
      final boolean maybeReroot = isSummaryTree && subtreePanel != null && selectedDiagnostic != null &&
                                  !subtreePanel.hasDiagnosticsValue(selectedDiagnostic.getValueRef());
      syncSelectionHelper(maybeReroot, null, false);
      if (!maybeReroot) {
        if (isSummaryTree && subtreePanel != null) {
          subtreePanel.selectAndShowNode(selectedDiagnostic);
        }
        else if (parentTree != null) {
          parentTree.selectAndShowNode(firstAncestorInParentTree(selectedNode));
        }
      }
    }
  }

  DiagnosticsNode firstAncestorInParentTree(DefaultMutableTreeNode node) {
    if (parentTree == null) {
      return getDiagnosticNode(node);
    }
    while (node != null) {
      final DiagnosticsNode diagnostic = getDiagnosticNode(node);
      if (diagnostic != null && parentTree.hasDiagnosticsValue(diagnostic.getValueRef())) {
        return parentTree.findDiagnosticsValue(diagnostic.getValueRef());
      }
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return null;
  }

  private void syncSelectionHelper(boolean maybeRerootSubtree, DiagnosticsNode detailsSelection, boolean textEditorUpdated) {
    if (!detailsSubtree && selectedNode != null) {
      getTreeModel().nodeChanged(selectedNode.getParent());
    }
    final DiagnosticsNode diagnostic = getSelectedDiagnostic();
    if (diagnostic != null && !textEditorUpdated) {
      if (isCreatedByLocalProject(diagnostic)) {
        final XSourcePosition position = diagnostic.getCreationLocation().getXSourcePosition();
        if (position != null) {
          position.createNavigatable(getFlutterApp().getProject()).navigate(false);
        }
      }
    }
    if (myPropertiesPanel != null) {
      myPropertiesPanel.showProperties(diagnostic);
    }
    if (detailsSubtree || subtreePanel == null) {
      if (diagnostic != null) {
        DiagnosticsNode toSelect = diagnostic;
        if (diagnostic.isProperty()) {
          // Set the selection to the parent of the property not the property as what we
          // should display on device is the selected widget not the selected property
          // of the widget.
          final TreePath path = new TreePath(selectedNode.getPath());
          // TODO(jacobr): even though it isn't currently an issue, we should
          // search for the first non-diagnostic node parent instead of just
          // assuming the first parent is a regular node.
          toSelect = getDiagnosticNode((DefaultMutableTreeNode)path.getPathComponent(path.getPathCount() - 2));
        }
        toSelect.setSelection(toSelect.getValueRef(), true);
      }
    }

    if (maybeRerootSubtree) {
      showDetailSubtrees(diagnostic, detailsSelection);
    }
    else if (diagnostic != null) {
      // We can't rely on the details tree to update the selection on the server in this case.
      final DiagnosticsNode selection = detailsSelection != null ? detailsSelection : diagnostic;
      selection.setSelection(selection.getValueRef(), true);
    }
  }

  private void initTree(final Tree tree) {
    tree.setCellRenderer(new DiagnosticsTreeCellRenderer(this));
    tree.setShowsRootHandles(true);
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
    flutterIsolateSubscription.dispose();

    // TODO(jacobr): actually implement.
    final InspectorService service = getInspectorService();
    shutdownTree(false);
    // TODO(jacobr): verify subpanels are disposed as well.
  }

  boolean isCreatedByLocalProject(DiagnosticsNode node) {
    if (node.isCreatedByLocalProject()) {
      return true;
    }
    // TODO(jacobr): remove the following code once the
    // `setPubRootDirectories` method has been in two revs of the Flutter Alpha
    // channel. The feature is expected to have landed in the Flutter dev
    // chanel on March 2, 2018.
    final InspectorSourceLocation location = node.getCreationLocation();
    if (location == null) {
      return false;
    }
    final VirtualFile file = location.getFile();
    if (file == null) {
      return false;
    }
    final String filePath = file.getCanonicalPath();
    if (filePath != null) {
      for (PubRoot root : getFlutterApp().getPubRoots()) {
        final String canonicalPath = root.getRoot().getCanonicalPath();
        if (canonicalPath != null && filePath.startsWith(canonicalPath)) {
          return true;
        }
      }
    }
    return false;
  }

  boolean hasPlaceholderChildren(DefaultMutableTreeNode node) {
    return node.getChildCount() == 0 ||
           (node.getChildCount() == 1 && ((DefaultMutableTreeNode)node.getFirstChild()).getUserObject() instanceof String);
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
        node.hasCreationLocation() ? SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
      append(node.getName(), attributes);

      // Set property description in tooltip.
      // TODO (pq):
      //  * consider tooltips for values
      //  * consider rich navigation hovers (w/ styling and navigable docs)

      final CompletableFuture<String> propertyDoc = node.getPropertyDoc();
      final String doc = propertyDoc.getNow(null);
      if (doc != null) {
        setToolTipText(doc);
      }
      else {
        // Make sure we see nothing stale while we wait.
        setToolTipText(null);
        node.safeWhenComplete(propertyDoc, (String tooltip, Throwable th) -> {
          // TODO(jacobr): make sure we still care about seeing this tooltip.
          if (th != null) {
            FlutterUtils.warn(LOG, th);
          }
          setToolTipText(tooltip);
        });
      }
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
          // We can display a better tooltip as we have access to introspection
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
                final Icon icon = FlutterMaterialIcons.getIconForHex(String.format("%1$04x", codePoint));
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

  private class PropertiesPanel extends TreeTableView implements DataProvider {
    private final InspectorObjectGroupManager groups;
    private final FlutterApp flutterApp;
    /**
     * Diagnostic we are displaying properties for.
     */
    private DiagnosticsNode diagnostic;
    /**
     * Current properties being displayed.
     */
    private ArrayList<DiagnosticsNode> currentProperties;

    PropertiesPanel(FlutterApp flutterApp, InspectorService inspectorService) {
      super(new ListTreeTableModelOnColumns(
        new DefaultMutableTreeNode(),
        new ColumnInfo[]{
          new PropertyNameColumnInfo("Property"),
          new PropertyValueColumnInfo("Value")
        }
      ));
      this.flutterApp = flutterApp;
      this.groups = new InspectorObjectGroupManager(inspectorService, "panel");
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
      // TODO(pq): implement
      //group.add(new JumpToPropertyDeclarationAction());
      return group;
    }

    ListTreeTableModelOnColumns getTreeModel() {
      return (ListTreeTableModelOnColumns)getTableModel();
    }

    public void showProperties(DiagnosticsNode diagnostic) {
      this.diagnostic = diagnostic;
      if (diagnostic == null || diagnostic.isDisposed()) {
        shutdownTree(false);
        return;
      }
      getEmptyText().setText(FlutterBundle.message("app.inspector.loading_properties"));
      groups.cancelNext();
      groups.getNext()
        .safeWhenComplete(diagnostic.getProperties(groups.getNext()), (ArrayList<DiagnosticsNode> properties, Throwable throwable) -> {
          if (throwable != null) {
            getTreeModel().setRoot(new DefaultMutableTreeNode());
            getEmptyText().setText(FlutterBundle.message("app.inspector.error_loading_properties"));
            FlutterUtils.warn(LOG, throwable);
            groups.cancelNext();
            return;
          }
          showPropertiesHelper(properties);
          PopupHandler.installUnknownPopupHandler(this, createTreePopupActions(), ActionManager.getInstance());
        });
    }

    private void showPropertiesHelper(ArrayList<DiagnosticsNode> properties) {
      currentProperties = properties;
      if (properties.size() == 0) {
        getTreeModel().setRoot(new DefaultMutableTreeNode());
        getEmptyText().setText(FlutterBundle.message("app.inspector.no_properties"));
        groups.promoteNext();
        return;
      }
      groups.getNext().safeWhenComplete(loadPropertyMetadata(properties), (Void ignored, Throwable errorGettingInstances) -> {
        if (errorGettingInstances != null) {
          // TODO(jacobr): show error message explaining properties could not be loaded.
          getTreeModel().setRoot(new DefaultMutableTreeNode());
          FlutterUtils.warn(LOG, errorGettingInstances);
          getEmptyText().setText(FlutterBundle.message("app.inspector.error_loading_property_details"));
          groups.cancelNext();
          return;
        }
        groups.promoteNext();
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

    private void refresh() {
      if (diagnostic == null) {
        return;
      }
      // We don't know whether we will switch to the new group or keep the current group
      // until we have tested whether the properties are identical.

      groups.cancelNext(); // Cancel any existing pending next state.
      if (diagnostic.isDisposed()) {
        // We are getting properties for a stale object. Wait until the next frame when we will have new properties.
        return;
      }
      groups.getNext()
        .safeWhenComplete(diagnostic.getProperties(groups.getNext()), (ArrayList<DiagnosticsNode> properties, Throwable throwable) -> {
          if (throwable != null || propertiesIdentical(properties, currentProperties)) {
            // Dispose the new group as it wasn't used.
            groups.cancelNext();
            return;
          }
          showPropertiesHelper(properties);
        });
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
      return InspectorTree.INSPECTOR_KEY.is(dataId) ? getTree() : null;
    }

    public void shutdownTree(boolean isolateStopped) {
      diagnostic = null;
      getEmptyText().setText(FlutterBundle.message("app.inspector.nothing_to_show"));
      getTreeModel().setRoot(new DefaultMutableTreeNode());
      groups.clear(isolateStopped);
    }

    public CompletableFuture<?> getPendingUpdateDone() {
      return groups.getPendingUpdateDone();
    }
  }

  private class MyTreeExpansionListener implements TreeExpansionListener {
    @Override
    public void treeExpanded(TreeExpansionEvent event) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)event.getPath().getLastPathComponent();
      maybeLoadChildren(treeNode);

      if (!programaticExpansionInProgress) {
        lastExpanded = treeNode;
        animateTo(treeNode);
      }
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
    }
  }
}
