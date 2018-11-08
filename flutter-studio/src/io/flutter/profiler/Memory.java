/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.profiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.flutter.utils.AsyncUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import kotlin.jvm.internal.Ref;
import org.dartlang.vm.service.element.AllocationProfile;
import org.dartlang.vm.service.element.ClassHeapStats;
import org.dartlang.vm.service.element.ClassObj;
import org.dartlang.vm.service.element.ClassRef;
import org.dartlang.vm.service.element.ElementList;
import org.dartlang.vm.service.element.Library;
import org.dartlang.vm.service.element.LibraryDependency;


class Memory {
  class ClassesTableModel extends AbstractTableModel {
    public static final int CLASS_COLUMN_INDEX = 0;
    public static final int INSTANCE_COUNT_COLUMN_INDEX = 1;
    public static final int TOTAL_BYTES_COLUMN_INDEX = 2;

    private final String[] COLUMN_NAMES = {"Class", "Instances", "Instances Total Bytes"};

    private DefaultMutableTreeNode _classesRoot;

    public ClassesTableModel(DefaultMutableTreeNode classesRoot) {
      this._classesRoot = classesRoot;
    }

    @Override
    public String getColumnName(int col) {
      return COLUMN_NAMES[col];
    }

    @Override
    public int getRowCount() {
      return _classesRoot.getChildCount();
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NAMES.length;
    }

    @Override
    public Class getColumnClass(int column) {
      // Column 0 is String everything else is an Integer.
      switch (column) {
        case 0:
          return String.class;
        default:
          return Integer.class;
      }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex >= _classesRoot.getChildCount()) {
        // TODO(terry): How is this possible?
        return "????";
      }

      DefaultMutableTreeNode node = (DefaultMutableTreeNode)(_classesRoot.getChildAt(rowIndex));
      ClassNode classNode = (ClassNode)(node.getUserObject());
      if (columnIndex == CLASS_COLUMN_INDEX) {
        return classNode.getClassName();
      }
      else if (columnIndex == INSTANCE_COUNT_COLUMN_INDEX) {
        return classNode.getInstancesCount();
      }
      else
      // Implied column index of TOTAL_BYTES_COLUMN_INDEX
      {
        return classNode.getByteSize();
      }
    }

    Memory.ClassNode getClassNode(int rowIndex) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)(_classesRoot.getChildAt(rowIndex));
      return (Memory.ClassNode)(node.getUserObject());
    }
  }

  DefaultMutableTreeNode _classesRoot;
  DefaultTreeModel _myClassesTreeModel;

  DefaultMutableTreeNode _myInstancesRoot;
  DefaultTreeModel _myInstancesTreeModel;

  // All Classes (unfiltered) associated with the ClassesTable.
  List<AllClassesInformation> _allClassesUnfiltered;

  // Used to signal when all ClassRefs have been interrogated.
  private int runningClassInfo;
  private int filteredClasses;        // Total filtered classes displayed in snapshot

  public void resetFilteredClasses() {
    filteredClasses = 0;
  }

  public int getFilteredClassesCount() {
    return filteredClasses;
  }

  protected class InstanceNode {
    static final String ROOT_POSTFIX = " Instances";

    private String _objectRef;

    InstanceNode(String objectRef) {
      this._objectRef = objectRef;
    }

    String getObjectRef() {
      return _objectRef;
    }

    void setObjectRef(String newRef) { _objectRef = newRef; }

    DefaultTreeModel getInstancesModel() { return _myInstancesTreeModel; }

    void setRootSize(int size) {
      if (_objectRef.endsWith(ROOT_POSTFIX)) {
        _objectRef = size + ROOT_POSTFIX;
      }
    }

    @Override
    public String toString() {
      return getObjectRef();
    }
  }

  protected class ClassNode {
    private ClassRef _ref;
    private int _byteSize;
    private int _instanceCount;
    private List<String> _instanceIds;

    ClassNode(ClassRef ref, int byteSize, int instanceCount) {
      this._ref = ref;
      this._byteSize = byteSize;
      this._instanceCount = instanceCount;
    }

    ClassRef getClassRef() { return _ref; }

    String getClassName() { return _ref.getName(); }

    int getByteSize() { return _byteSize; }

    int getInstancesCount() { return _instanceCount; }

    List<String> getInstanceIds() { return _instanceIds; }

    void addInstances(List<String> instanceIds) {
      _instanceIds = instanceIds;
    }

    @Override
    public String toString() {
      if (_ref == null) {
        return "CLASSES USED";
      }
      return getClassName() + "  " + _instanceCount + "  [" + _byteSize + "]";
    }
  }

  // All Classes information displayed in ClassTable.
  protected class AllClassesInformation {
    private ClassRef classRef;
    private ClassObj classObj;
    private int bytes;
    private int instances;

    public AllClassesInformation(ClassRef classRef, ClassObj classObj, int total_bytes, int total_instances) {
      this.classRef = classRef;
      this.classObj = classObj;
      this.bytes = total_bytes;
      this.instances = total_instances;
    }
  }

  Memory() {
    _classesRoot = new DefaultMutableTreeNode(new ClassNode(null, -1, -1));
    _myClassesTreeModel = new DefaultTreeModel(_classesRoot);

    _myInstancesRoot = new DefaultMutableTreeNode(new InstanceNode(InstanceNode.ROOT_POSTFIX));
    _myInstancesTreeModel = new DefaultTreeModel(_myInstancesRoot);
  }

  DefaultTreeModel getClassTreeModel() { return _myClassesTreeModel; }

  ClassesTableModel getClassesTableModel() {
    return new ClassesTableModel(_classesRoot);
  }

  void removeAllClassChildren(Boolean updateUI) {
    _classesRoot.removeAllChildren();
    if (updateUI) {
      _myClassesTreeModel.reload();
    }
  }

  void removeAllInstanceChildren(Boolean updateUI) {
    _myInstancesRoot.removeAllChildren();
    if (updateUI) {
      _myInstancesTreeModel.reload();
    }
  }

  void addClassToTreeModel(DefaultMutableTreeNode node) {
    _myClassesTreeModel.insertNodeInto(node, _classesRoot, _classesRoot.getChildCount());
  }

  void addInstanceToTreeModel(DefaultMutableTreeNode node) {
    _myInstancesTreeModel.insertNodeInto(node, _myInstancesRoot, _myInstancesRoot.getChildCount());
  }

  void addDetailNodeInstance(DefaultMutableTreeNode parent, DefaultMutableTreeNode node) {
    parent.insert(node, parent.getChildCount());
  }


  protected void decodeClassesInHeap(FlutterStudioMonitorStageView view, AllocationProfile allocatedResponse, JTable classesTable) {
    _myClassesTreeModel = new DefaultTreeModel(_classesRoot);

    _allClassesUnfiltered = new ArrayList<AllClassesInformation>();

    ClassesTableModel tableModel = getClassesTableModel();
    classesTable.setModel(tableModel);          // Change underlying model associated with this JTable.

    runningClassInfo = 0;
    filteredClasses = 0;
    view.updateClassesStatus("Decoding Classes...");

    final Iterator<ClassHeapStats> itClassStats = allocatedResponse.getMembers().iterator();
    while (itClassStats.hasNext()) {
      ClassHeapStats data = itClassStats.next();
      ClassRef classRef = data.getClassRef();

      String className = classRef.getName();
      String classId = classRef.getId();

      // TODO(terry): Check with VM team - _vmName looks like internal things, ignoring for now.
      if (className.length() == 0) {
        JsonElement jsonVmName = classRef.getJson().get("_vmName");
        if (jsonVmName != null) {
          String vmName = jsonVmName.getAsString();
          if (vmName.length() != 0) continue;
        }
      }

      runningClassInfo++;

      AsyncUtils.whenCompleteUiThread(view.vmGetObject(classId), (JsonObject response, Throwable exception) -> {
        ClassObj classObj = new ClassObj(response);
        AllClassesInformation currentClass =
          new AllClassesInformation(classRef, classObj, data.getPromotedBytes(), data.getPromotedInstances());
        _allClassesUnfiltered.add(currentClass);

        filterClassesTable(view, classesTable, currentClass);

        view.updateClassesStatus("Processing ClassRefs: " + runningClassInfo);

        if (--runningClassInfo == 0) {
          view.updateClassesStatus("Snapshot complete filtered " + filteredClasses + " classes.");
          view.getProfilersView().snapshotComplete();
        }
      });
    }
  }

  public void filterClassesTable(FlutterStudioMonitorStageView view, JTable classesTable, AllClassesInformation currentClass) {
    String classLibrary = currentClass.classObj.getLibrary().getId();

    ClassesTableModel tableModel = (ClassesTableModel)(classesTable.getModel());

    // Only show ClassRef's if its in a library checked.
    if (view.filteredLibraries.contains(classLibrary)) {
      filteredClasses++;
      int bytes = currentClass.bytes;
      int instances = currentClass.instances;

      DefaultMutableTreeNode node = new DefaultMutableTreeNode(new ClassNode(currentClass.classRef, bytes, instances));
      addClassToTreeModel(node);

      // Update the UI
      SwingUtilities.invokeLater(() -> {
        tableModel.fireTableStructureChanged();     // Update the table UI.
        _myClassesTreeModel.reload();

        // Setup for sorting the classes table.
        TableRowSorter<TableModel> sorter = new TableRowSorter(classesTable.getModel());
        classesTable.setRowSorter(sorter);
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();

        // Default sorting of the instance count in descending order.
        int columnIndexToSort = ClassesTableModel.INSTANCE_COUNT_COLUMN_INDEX;
        sortKeys.add(new RowSorter.SortKey(columnIndexToSort, SortOrder.DESCENDING));

        // Apply the sorting.
        sorter.setSortKeys(sortKeys);
        sorter.sort();
      });
    }
  }

  protected void decodeInstances(FlutterStudioMonitorStageView view, List<String> instances, JTree instanceObjects) {
    _myInstancesTreeModel = new DefaultTreeModel(_myInstancesRoot);

    InstanceNode rootNode = (InstanceNode)(_myInstancesRoot.getUserObject());
    rootNode.setRootSize(instances.size());

    for (int index = 0; index < instances.size(); index++) {
      String instanceId = instances.get(index);

      DefaultMutableTreeNode node = new DefaultMutableTreeNode(new InstanceNode(instanceId));
      addInstanceToTreeModel(node);

      // TODO(terry): Fake node for now need to hookup Instance Detail node.
      DefaultMutableTreeNode instanceDetailNode = new DefaultMutableTreeNode();
      addDetailNodeInstance(node, instanceDetailNode);
    }

    instanceObjects.setModel(_myInstancesTreeModel);

    DefaultMutableTreeNode r = TreeUtil.deepCopyTree(_myInstancesRoot, (DefaultMutableTreeNode)_myInstancesRoot.clone());
    //TreeUtil.sortClasses(_myInstancesRoot);

    view.updateClassesStatus(instances.size() + " Instances loaded.");
    // TODO(terry): enabling clicking in classes again.

    // Update the new data.
    _myInstancesTreeModel.reload();
  }
}

final class TreeUtil {
  private static boolean ascending;
  private static Comparator<DefaultMutableTreeNode> tnc = Comparator.comparing(DefaultMutableTreeNode::isLeaf)
    .thenComparing(n -> n.getUserObject().toString());

  private static Comparator<DefaultMutableTreeNode> tncCount =
    Comparator.comparing(DefaultMutableTreeNode::isLeaf)
      .thenComparing(n -> ((Memory.ClassNode)(n.getUserObject())).getInstancesCount());

  private static void timsort(DefaultMutableTreeNode parent) {
    int n = parent.getChildCount();
    List<DefaultMutableTreeNode> children = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      children.add((DefaultMutableTreeNode)parent.getChildAt(i));
    }

    Collections.sort(children, tncCount);

    parent.removeAllChildren();
    if (ascending) {
      children.forEach(parent::add);
    }
    else {
      int numChildren = children.size();
      for (int idx = numChildren - 1; idx >= 0; idx--) {
        parent.add(children.get(idx));
      }
    }
  }

  public static void sortClasses(DefaultMutableTreeNode parent, boolean sortAscending) {
    ascending = sortAscending;
    Collections.list((Enumeration<?>)parent.preorderEnumeration()).stream()
      .filter(DefaultMutableTreeNode.class::isInstance)
      .map(DefaultMutableTreeNode.class::cast)
      .filter(node -> !node.isLeaf())
      .forEach(TreeUtil::timsort);
  }

  public static DefaultMutableTreeNode deepCopyTree(DefaultMutableTreeNode src, DefaultMutableTreeNode tgt) {
    Collections.list((Enumeration<?>)src.children()).stream()
      .filter(DefaultMutableTreeNode.class::isInstance)
      .map(DefaultMutableTreeNode.class::cast)
      .forEach(node -> {
        DefaultMutableTreeNode clone = new DefaultMutableTreeNode(node.getUserObject());
        tgt.add(clone);
        if (!node.isLeaf()) {
          deepCopyTree(node, clone);
        }
      });
    return tgt;
  }
}
