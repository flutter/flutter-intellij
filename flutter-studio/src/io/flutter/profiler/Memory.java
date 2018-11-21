/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.profiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.utils.AsyncUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.dartlang.vm.service.element.AllocationProfile;
import org.dartlang.vm.service.element.ClassHeapStats;
import org.dartlang.vm.service.element.ClassObj;
import org.dartlang.vm.service.element.ClassRef;
import org.gradle.internal.impldep.com.esotericsoftware.minlog.Log;


class Memory {
  private final static Logger LOG = Logger.getInstance(Memory.class);

  // Entries returned from call to getAllocationProfile - new and old heap statistics (ClassHeapStats).
  //
  //     public List<Integer> getNew()  // new heap space
  //     public List<Integer> getOld()  // old heap space
  //
  //     getNew/getOld both returns a list with 8 entries of the particular heap (new/old) in this order:
  //        [0] Pre-GC heap space allocation count
  //        [1] Pre-GC heap space allocations (includes new external)
  //        [2] Post-GC heap space allocation count
  //        [3] Post-GC heap space allocations (includes new external)
  //        [4] Recent heap space allocation count
  //        [5] Recent heap space allocations (includes new external)
  //        [6] Total heap space allocation count since last reset
  //        [7] Total heap space allocations (including new external) since last reset
  //
  //     public int getPromotedBytes()
  //        number of bytes promoted from new space to old space since last GC of new space
  //     public int getPromotedInstances()
  //        number of instances promoted from new space to old space since last GC of new space
  private final int ALLOCATED_BEFORE_GC = 0;
  private final int ALLOCATED_BEFORE_GC_SIZE = 1;
  private final int LIVE_AFTER_GC = 2;
  private final int LIVE_AFTER_GC_SIZE = 3;
  private final int ALLOCATED_SINCE_GC = 4;
  private final int ALLOCATED_SINCE_GC_SIZE = 5;
  private final int ACCUMULATED = 6;
  private final int ACCUMULATED_SIZE = 7;

  class ClassesTableModel extends AbstractTableModel {
    public static final int CLASS_COLUMN_INDEX = 0;
    public static final int INSTANCE_COUNT_COLUMN_INDEX = 1;
    public static final int ACCUMULATED_INSTNACE_COUNT_COLUMN_INDEX = 2;
    public static final int TOTAL_BYTES_COLUMN_INDEX = 3;

    // Class is class name, "Instances Allocated" is number of instances (active / to be GC'd), and
    // "Total Bytes Allocated" is number of bytes allocated in the heap (active and to be GC'd).
    private final String[] COLUMN_NAMES = {"Class", "Instances", "Accumulated Instances", "Total Bytes"};

    private DefaultMutableTreeNode classesRoot;

    public ClassesTableModel(DefaultMutableTreeNode classesRoot) {
      this.classesRoot = classesRoot;
    }

    @Override
    public String getColumnName(int col) {
      return COLUMN_NAMES[col];
    }

    @Override
    public int getRowCount() {
      return classesRoot.getChildCount();
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
      if (rowIndex >= classesRoot.getChildCount()) {
        // TODO(terry): How is this possible?
        return "????";
      }

      DefaultMutableTreeNode node = (DefaultMutableTreeNode)(classesRoot.getChildAt(rowIndex));
      ClassNode classNode = (ClassNode)(node.getUserObject());
      switch (columnIndex) {
        case CLASS_COLUMN_INDEX:
          return classNode.getClassName();
        case INSTANCE_COUNT_COLUMN_INDEX:
          return classNode.getInstancesCount();
        case TOTAL_BYTES_COLUMN_INDEX:
          return classNode.getByteSize();
        case ACCUMULATED_INSTNACE_COUNT_COLUMN_INDEX:
          return classNode.getAccumulatedInstancesCount();
        default:
          Log.error("Unexpected columnIndex: " + columnIndex);
          return "";
      }
    }

    Memory.ClassNode getClassNode ( int rowIndex){
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)(classesRoot.getChildAt(rowIndex));
        return (Memory.ClassNode)(node.getUserObject());
      }
    }

    DefaultMutableTreeNode classesRoot;
    DefaultTreeModel myClassesTreeModel;

    DefaultMutableTreeNode myInstancesRoot;
    DefaultTreeModel myInstancesTreeModel;

    // All Classes (unfiltered) associated with the ClassesTable.
    List<AllClassesInformation> allClassesUnfiltered;

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

      private String objectRef;

      InstanceNode(String objectRef) {
        this.objectRef = objectRef;
      }

      String getObjectRef() {
        return objectRef;
      }

      void setObjectRef(String newRef) { objectRef = newRef; }

      DefaultTreeModel getInstancesModel() { return myInstancesTreeModel; }

      void setRootSize(int size) {
        if (objectRef.endsWith(ROOT_POSTFIX)) {
          objectRef = size + ROOT_POSTFIX;
        }
      }

      @Override
      public String toString() {
        return getObjectRef();
      }
    }

    protected class ClassNode {
      private ClassRef ref;
      private int byteSize;
      private int instanceCount;
      private int accumulatedInstanceCount;
      private List<String> instanceIds;

      ClassNode(ClassRef ref, int byteSize, int instanceCount, int accumulatedInstanceCount) {
        this.ref = ref;
        this.byteSize = byteSize;
        this.instanceCount = instanceCount;
        this.accumulatedInstanceCount = accumulatedInstanceCount;
      }

      ClassRef getClassRef() { return ref; }

      String getClassName() { return ref.getName(); }

      int getByteSize() { return byteSize; }

      int getInstancesCount() { return instanceCount; }

      int getAccumulatedInstancesCount() { return accumulatedInstanceCount; }

      List<String> getInstanceIds() { return instanceIds; }

      void addInstances(List<String> instanceIds) {
        this.instanceIds = instanceIds;
      }

      @Override
      public String toString() {
        if (ref == null) {
          return "CLASSES USED";
        }
        return getClassName() + "  " + instanceCount + "  [" + byteSize + "]";
      }
    }

    // All Classes information displayed in ClassTable.
    protected class AllClassesInformation {
      private ClassRef classRef;
      private ClassObj classObj;
      private int bytes;
      private int instances;
      private int accumulatedInstances;

      public AllClassesInformation(ClassRef classRef, ClassObj classObj, int total_bytes, int total_instances, int accumulated_instances) {
        this.classRef = classRef;
        this.classObj = classObj;
        this.bytes = total_bytes;
        this.instances = total_instances;
        this.accumulatedInstances = accumulated_instances;
      }
    }

    Memory() {
      classesRoot = new DefaultMutableTreeNode(new ClassNode(null, -1, -1, -1));
      myClassesTreeModel = new DefaultTreeModel(classesRoot);

      myInstancesRoot = new DefaultMutableTreeNode(new InstanceNode(InstanceNode.ROOT_POSTFIX));
      myInstancesTreeModel = new DefaultTreeModel(myInstancesRoot);
    }

    DefaultTreeModel getClassTreeModel() { return myClassesTreeModel; }

    ClassesTableModel getClassesTableModel() {
      return new ClassesTableModel(classesRoot);
    }

    void removeAllClassChildren(Boolean updateUI) {
      classesRoot.removeAllChildren();
      if (updateUI) {
        myClassesTreeModel.reload();
      }
    }

    void removeAllInstanceChildren(Boolean updateUI) {
      myInstancesRoot.removeAllChildren();
      if (updateUI) {
        myInstancesTreeModel.reload();
      }
    }

    void addClassToTreeModel(DefaultMutableTreeNode node) {
      myClassesTreeModel.insertNodeInto(node, classesRoot, classesRoot.getChildCount());
    }

    void addInstanceToTreeModel(DefaultMutableTreeNode node) {
      myInstancesTreeModel.insertNodeInto(node, myInstancesRoot, myInstancesRoot.getChildCount());
    }

    void addDetailNodeInstance(DefaultMutableTreeNode parent, DefaultMutableTreeNode node) {
      parent.insert(node, parent.getChildCount());
    }


    protected void decodeClassesInHeap(FlutterStudioMonitorStageView view, AllocationProfile allocatedResponse, JTable classesTable) {
      myClassesTreeModel = new DefaultTreeModel(classesRoot);

      allClassesUnfiltered = new ArrayList<AllClassesInformation>();

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

        // Ignore any class with _vmName it's an internal VM thing.
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

          List<Integer> newHeap = data.getNew();
          List<Integer> oldHeap = data.getOld();

          int totalAccumulatedBytes = newHeap.get(ACCUMULATED_SIZE) + oldHeap.get(ACCUMULATED_SIZE);
          int totalAccumulatedInstances = newHeap.get(ACCUMULATED) + oldHeap.get(ACCUMULATED);
          int totalInstances = newHeap.get(LIVE_AFTER_GC) + newHeap.get(ALLOCATED_SINCE_GC) +
                               oldHeap.get(LIVE_AFTER_GC) + oldHeap.get(ALLOCATED_SINCE_GC);
          int totalBytesAllocated = newHeap.get(LIVE_AFTER_GC_SIZE) + newHeap.get(ALLOCATED_SINCE_GC_SIZE) +
                                    oldHeap.get(LIVE_AFTER_GC_SIZE) + oldHeap.get(ALLOCATED_SINCE_GC_SIZE);

          AllClassesInformation currentClass = new AllClassesInformation(classRef, classObj,
                                                                         totalBytesAllocated,
                                                                         totalInstances,
                                                                         totalAccumulatedInstances);
          allClassesUnfiltered.add(currentClass);

          filterClassesTable(view, classesTable, currentClass);

          view.updateClassesStatus("Processing ClassRefs: " + runningClassInfo);

          if (--runningClassInfo == 0) {
            view.updateClassesStatus("Snapshot complete filtered " + filteredClasses + " classes.");
            view.getProfilersView().snapshotComplete();
          }
        });
      }
    }

    int getClassRefInstanceCount(FlutterStudioMonitorStageView view, ClassRef classRef) {
      int instanceLimit = 1;
      String classId = classRef.getId();

      AtomicInteger totalCount = new AtomicInteger();
      AsyncUtils.whenCompleteUiThread(view.getInstances(classId, instanceLimit), (JsonObject response, Throwable exception) -> {
        totalCount.set(response.get("totalCount").getAsInt());
      });

      return totalCount.get();
    }

    public void filterClassesTable(FlutterStudioMonitorStageView view, JTable classesTable, AllClassesInformation currentClass) {
      String classLibrary = currentClass.classObj.getLibrary().getId();

      ClassesTableModel tableModel = (ClassesTableModel)(classesTable.getModel());

      // Only show ClassRef's if its in a library checked.
      if (view.filteredLibraries.contains(classLibrary)) {
        filteredClasses++;
        int bytes = currentClass.bytes;
        int instances = currentClass.instances;
        int accumulatedInstances = currentClass.accumulatedInstances;

        DefaultMutableTreeNode node =
          new DefaultMutableTreeNode(new ClassNode(currentClass.classRef, bytes, instances, accumulatedInstances));
        addClassToTreeModel(node);

        // Update the UI
        SwingUtilities.invokeLater(() -> {
          tableModel.fireTableStructureChanged();     // Update the table UI.
          myClassesTreeModel.reload();

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
      myInstancesTreeModel = new DefaultTreeModel(myInstancesRoot);

      InstanceNode rootNode = (InstanceNode)(myInstancesRoot.getUserObject());
      rootNode.setRootSize(instances.size());

      for (int index = 0; index < instances.size(); index++) {
        String instanceId = instances.get(index);

        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new InstanceNode(instanceId));
        addInstanceToTreeModel(node);

        // TODO(terry): Fake node for now need to hookup Instance Detail node.
        DefaultMutableTreeNode instanceDetailNode = new DefaultMutableTreeNode();
        addDetailNodeInstance(node, instanceDetailNode);
      }

      instanceObjects.setModel(myInstancesTreeModel);

      DefaultMutableTreeNode r = TreeUtil.deepCopyTree(myInstancesRoot, (DefaultMutableTreeNode)myInstancesRoot.clone());
      //TreeUtil.sortClasses(_myInstancesRoot);

      view.updateClassesStatus(instances.size() + " Instances loaded.");
      // TODO(terry): enabling clicking in classes again.

      // Update the new data.
      myInstancesTreeModel.reload();
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
