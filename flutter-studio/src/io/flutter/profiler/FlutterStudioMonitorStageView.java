/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.profilers.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import icons.FlutterIcons;
import icons.StudioIcons;
import io.flutter.FlutterUtils;
import io.flutter.server.vmService.VMServiceManager;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.StreamSubscription;
import io.flutter.view.HighlightedTable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.AllocationProfileConsumer;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.element.AllocationProfile;
import org.dartlang.vm.service.element.BoundField;
import org.dartlang.vm.service.element.ClassRef;
import org.dartlang.vm.service.element.ElementList;
import org.dartlang.vm.service.element.Instance;
import org.dartlang.vm.service.element.InstanceKind;
import org.dartlang.vm.service.element.InstanceRef;
import org.dartlang.vm.service.element.Isolate;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.LibraryRef;
import org.dartlang.vm.service.element.Obj;
import org.dartlang.vm.service.element.RPCError;
import org.dartlang.vm.service.element.Sentinel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS;
import static com.android.tools.profilers.ProfilerLayout.MARKER_LENGTH;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;
import static com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN;
import static io.flutter.profiler.FilterLibraryDialog.ALL_DART_LIBRARIES;
import static io.flutter.profiler.FilterLibraryDialog.ALL_FLUTTER_LIBRARIES;
import static io.flutter.profiler.FilterLibraryDialog.DART_LIBRARY_PREFIX;
import static io.flutter.profiler.FilterLibraryDialog.FLUTTER_LIBRARY_PREFIX;

/**
 * Bird eye view displaying high-level information across all profilers.
 * Refactored from Android Studio 3.2 adt-ui code.
 */
public class FlutterStudioMonitorStageView extends FlutterStageView<FlutterStudioMonitorStage> {
  private static final Logger LOG = Logger.getInstance(FlutterStudioMonitorStageView.class);

  private static final String TAB_NAME = "Memory";
  private static final String HEAP_LABEL = "Heap";

  private static final Color MEMORY_USED = new JBColor(new Color(0x56BFEC), new Color(0x2B7DA2));
  private static final Color MEMORY_EXTERNAL = new JBColor(new Color(0x56A5CB), new Color(0x226484));
  private static final Color MEMORY_CAPACITY = new JBColor(new Color(0x1B4D65), new Color(0xF6F6F6));
  private static final Color MEMORY_RSS = new JBColor(new Color(0xF1B876), new Color(0xFFDFA6));

  static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);

  public static final Border MONITOR_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, ProfilerColors.MONITOR_BORDER);

  private static final int AXIS_SIZE = 100000;

  JBPanel classesPanel;                         // All classes information in this panel.
  JBScrollPane heapObjectsScoller;              // Contains the JTree of heap objects.
  private final JBLabel classesStatusArea;      // Classes status area.
  private final HighlightedTable classesTable;  // Display classes found in the heap snapshot.

  private JBPanel instancesPanel;               // All instances info in panel (title, close and JTree).
  private JBLabel instancesTitleArea;           // Display of all instances displayed.
  private JBScrollPane instanceObjectsScoller;  // Contains the JTree of instance objects.
  private final Tree instanceObjects;           // Instances of all objects of the same object class type.

  private List<RangedContinuousSeries> rangedData;
  private LegendComponentModel legendComponentModel;
  private LegendComponent legendComponent;

  @NotNull private final JBSplitter myMainSplitter = new JBSplitter(false);
  @NotNull private final JBSplitter myChartCaptureSplitter = new JBSplitter(true);
  @NotNull private final JBSplitter myInstanceDetailsSplitter = new JBSplitter(true);

  String isolateId;
  static final String GRAPH_EVENTS = "_Graph";

  // Libraries to filter in ClassRefs.
  Set<String> filteredLibraries = new HashSet<String>();

  // Memory objects currently active.
  Memory memorySnapshot;

  // Used to manage fetching instances status.
  boolean runningComputingInstances;

  // Any library name key in allLibraries prefixed with "%%" is not displayed in the filter dialog.
  final static String PREFIX_LIBRARY_NAME_HIDDEN = "%%";

  // All Dart Libraries associated with the Flutter application, key is name and value is Uri as String.
  final Map<String, LibraryRef> allLibraries = new HashMap<>();
  final Map<String, LibraryRef> dartLibraries = new HashMap<>();
  final Map<String, LibraryRef> flutterLibraries = new HashMap<>();

  // TODO(terry): Remove below debugging before checking in.
  LineChartModel debugModel;

  private void DEBUG_dumpChartModels() {
    List<SeriesData<Long>> usedSeriesData = ((List<SeriesData<Long>>)(debugModel.getSeries().get(0).getSeries()));
    List<SeriesData<Long>> capacitySeriesData = ((List<SeriesData<Long>>)(debugModel.getSeries().get(1).getSeries()));
    List<SeriesData<Long>> externalSeriesData = ((List<SeriesData<Long>>)(debugModel.getSeries().get(2).getSeries()));

    assert (usedSeriesData.size() == capacitySeriesData.size() && usedSeriesData.size() == externalSeriesData.size());
    for (int i = 0; i < usedSeriesData.size(); i++) {
      long usedTime = usedSeriesData.get(i).x;
      long capacityTime = capacitySeriesData.get(i).x;
      long externalTime = externalSeriesData.get(i).x;

      long usedValue = usedSeriesData.get(i).value;
      long capacityValue = capacitySeriesData.get(i).value;
      long externalValue = externalSeriesData.get(i).value;

      System.out.println("DUMP time [" + i + "] " + usedTime);
      if (usedTime == capacityTime && usedTime == externalTime) {
        System.out.println("    " + usedValue + ", " + capacityValue + ", " + externalValue);
      }
      else {
        System.out.println("ERROR: timestamps don't match for entry " + i);
      }
    }
  }

  public FlutterStudioMonitorStageView(@NotNull FlutterStudioProfilersView profilersView,
                                       @NotNull FlutterStudioMonitorStage stage) {
    super(profilersView, stage);

    memorySnapshot = new Memory();

    initializeVM();

    // Hookup splitters.
    myMainSplitter.getDivider().setBorder(DEFAULT_VERTICAL_BORDERS);
    myChartCaptureSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    myInstanceDetailsSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);

    myChartCaptureSplitter.setFirstComponent(buildUI(stage));

    classesTable = new HighlightedTable(memorySnapshot.getClassesTableModel());
    classesTable.setVisible(true);
    classesTable.setAutoCreateRowSorter(true);
    classesTable.getRowSorter().toggleSortOrder(1);   // Sort by number of instances in descending order.

    heapObjectsScoller = new JBScrollPane(classesTable);

    FlutterStudioMonitorStageView view = (FlutterStudioMonitorStageView)(this);

    classesTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        // Only allow one compute all instances of a particular ClassRef one at a
        // time.
        if (runningComputingInstances) {
          view.updateClassesStatus("Ignored - other instances computation in process...");
          return;
        }

        memorySnapshot.removeAllInstanceChildren(true);

        // Find the selected item in the JTable.
        JBTable selectedUi = (JBTable)(e.getSource());

        Point p = e.getPoint();
        int selectedRow = classesTable.rowAtPoint(p);
        int modelIndex = selectedUi.convertRowIndexToModel(selectedRow);

        Memory.ClassesTableModel tableModel = (Memory.ClassesTableModel)(selectedUi.getModel());
        Memory.ClassNode classNode = tableModel.getClassNode(modelIndex);
        ClassRef cls = classNode.getClassRef();

        String classRef = cls.getId();

        int instanceLimit = classNode.getInstancesCount();

        view.updateClassesStatus("Fetching " + instanceLimit + " instances.");

        AsyncUtils.whenCompleteUiThread(getInstances(classRef, instanceLimit), (JsonObject response, Throwable exception) -> {
          JsonArray instances = response.getAsJsonArray("samples");
          int totalInstances = response.get("totalCount").getAsInt();
          List<String> instanceIds = new ArrayList<String>(totalInstances);

          Iterator it = instances.iterator();
          int gettingInstanceCount = 0;

          while (it.hasNext()) {
            gettingInstanceCount++;
            view.updateClassesStatus("Processing " + gettingInstanceCount + " of " + instanceLimit + " instances.");

            JsonObject instance = (JsonObject)(it.next());
            String objectRef = instance.get("id").getAsString();

            instanceIds.add(objectRef);

            final Map<String, Object> objectParams = new HashMap<>();
            objectParams.put("objectId", objectRef);
            // TODO(terry): Display returned instances.
          }

          LOG.info("Instances returned " + instanceIds.size());

          classNode.addInstances(instanceIds);
          memorySnapshot.decodeInstances(view, instanceIds, instanceObjects);

          runningComputingInstances = false;
          view.updateClassesStatus("All Instances Processed.");
          view.setClassForInstancesTitle(cls.getName());

          // Update the UI
          instanceObjects.setVisible(true);
          instancesPanel.setVisible(true);
        });
      }
    });

    instanceObjects = new Tree();
    instanceObjects.setVisible(true);

    instanceObjects.setEditable(false);
    instanceObjects.getSelectionModel().setSelectionMode
      (TreeSelectionModel.SINGLE_TREE_SELECTION);
    instanceObjects.setShowsRootHandles(false);

    instanceObjects.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath expanded = event.getPath();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)(expanded.getLastPathComponent());

        // Only compute if not computed
        // TODO(terry): Better check like UserObject not null???
        if (node.getChildCount() == 1) {
          if (node.getFirstChild().toString() == "") {
            // TODO(terry): Don't inundate the VM with more than more one at a time.
            inspectInstance(node);
          }
          else {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)(node.getFirstChild());
            Object userObj = childNode.getUserObject();
            if (userObj instanceof String) {
              String objectRef = (String)(userObj);
              if (objectRef.startsWith("objects/")) {
                getObject(node, objectRef);
              }
            }
          }
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) { }
    });

    classesPanel = new JBPanel();
    classesPanel.setVisible(false);
    classesPanel.setLayout(new BoxLayout(classesPanel, BoxLayout.PAGE_AXIS));

    JBPanel classesToolbar = new JBPanel();
    classesToolbar.setLayout(new BorderLayout());
    classesToolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

    classesStatusArea = new JBLabel();
    classesStatusArea.setText("Computing...");
    classesToolbar.add(classesStatusArea, BorderLayout.WEST);

    CommonButton closeClasses = new CommonButton("x");
    closeClasses.setToolTipText("Close Classes");
    closeClasses.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        classesPanel.setVisible(false);
        instancesPanel.setVisible(false);
      }
    });

    closeClasses.setBorder(new EmptyBorder(0, 0, 0, 0));
    closeClasses.setPreferredSize(new Dimension(20, 20));
    closeClasses.setMaximumSize(new Dimension(20, 20));
    classesToolbar.add(closeClasses, BorderLayout.EAST);

    classesPanel.add(classesToolbar, BorderLayout.PAGE_END);
    classesPanel.add(heapObjectsScoller);

    instanceObjectsScoller = new JBScrollPane(instanceObjects);

    instancesPanel = new JBPanel();
    instancesPanel.setVisible(false);
    instancesPanel.setLayout(new BoxLayout(instancesPanel, BoxLayout.PAGE_AXIS));

    JBPanel instancesToolbar = new JBPanel();
    instancesToolbar.setLayout(new BorderLayout());
    instancesToolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

    instancesTitleArea = new JBLabel();
    setClassForInstancesTitle("");
    instancesToolbar.add(instancesTitleArea, BorderLayout.WEST);

    CommonButton closeInstances = new CommonButton("x");
    closeInstances.setToolTipText("Close Instances");
    closeInstances.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        instancesPanel.setVisible(false);
      }
    });

    closeInstances.setBorder(new EmptyBorder(0, 0, 0, 0));
    closeInstances.setPreferredSize(new Dimension(20, 20));
    closeInstances.setMaximumSize(new Dimension(20, 20));
    instancesToolbar.add(closeInstances, BorderLayout.EAST);

    instancesPanel.add(instancesToolbar, BorderLayout.PAGE_END);
    instancesPanel.add(instanceObjectsScoller);

    instancesPanel.setVisible(false);

    myInstanceDetailsSplitter.setOpaque(true);
    myInstanceDetailsSplitter.setFirstComponent(instancesPanel);

    myChartCaptureSplitter.setSecondComponent(classesPanel);

    myInstanceDetailsSplitter.setOpaque(true);
    myInstanceDetailsSplitter.setFirstComponent(instancesPanel);

    myMainSplitter.setFirstComponent(myChartCaptureSplitter);
    myMainSplitter.setSecondComponent(myInstanceDetailsSplitter);
    myMainSplitter.setProportion(0.6f);

    // Display in the Inspector Memory tab.
    getComponent().add(myMainSplitter, BorderLayout.CENTER);
  }

  void buildCharting() {
    JComponent chartingUI = myChartCaptureSplitter.getFirstComponent();
    myChartCaptureSplitter.remove(chartingUI);
    myChartCaptureSplitter.setFirstComponent(buildUI(this.getStage()));
  }

  public JBTable getClassesTable() { return classesTable; }

  void inspectInstance(@NotNull DefaultMutableTreeNode node) {
    // Is it one of our models?  Otherwise its synthesized nodes from other getObjects.
    if (node.getUserObject() instanceof Memory.InstanceNode) {
      Memory.InstanceNode instanceNode = (Memory.InstanceNode)(node.getUserObject());
      String objectRef = instanceNode.getObjectRef();
      getObject(node, objectRef);
    }
  }

  void updateClassesStatus(String newStatus) {
    classesStatusArea.setText(newStatus);
  }

  void setClassForInstancesTitle(String className) {
    instancesTitleArea.setText("Instances for Class " + className);
  }

  public CompletableFuture<Isolate> vmGetLibraries() {
    FlutterStudioProfilers profilers = getStage().getStudioProfilers();

    CompletableFuture<Isolate> isolateFuture = new CompletableFuture<>();
    profilers.getApp().getVmService().getIsolate(isolateId, new GetIsolateConsumer() {
      @Override
      public void onError(RPCError error) {
        isolateFuture.completeExceptionally(new RuntimeException(error.getMessage()));
      }

      @Override
      public void received(Isolate response) {
        ElementList<LibraryRef> libraryRefs = response.getLibraries();
        Iterator<LibraryRef> iterator = libraryRefs.iterator();
        while (iterator.hasNext()) {
          LibraryRef ref = iterator.next();

          if (ref.getName().length() > 0) {
            rememberLibrary(ref.getName(), ref);
          }
          else {
            // No unique name to display use the Uri and don't display in list of known library/packages.
            rememberLibrary(PREFIX_LIBRARY_NAME_HIDDEN + ref.getUri(), ref);
          }
        }
        allLibraries.put(ALL_DART_LIBRARIES, null);       // All Dart libraries are in this entry.
        allLibraries.put(ALL_FLUTTER_LIBRARIES, null);    // All Flutter libraries are in this entry.

        Set<String> displayedLibraries = new TreeSet<>();
        // The initial list of selected libraries is all of them.
        for (String s : allLibraries.keySet()) {
          if (!s.startsWith(PREFIX_LIBRARY_NAME_HIDDEN)) {
            displayedLibraries.add(s);
          }
        }
        getProfilersView().setInitialSelectedLibraries(displayedLibraries);

        isolateFuture.complete(response);
      }

      @Override
      public void received(Sentinel response) {
        // Unable to get the isolate.
        isolateFuture.complete(null);
      }
    });
    return isolateFuture;
  }

  void rememberLibrary(String name, LibraryRef ref) {
    assert name.length() > 0;

    if (name.startsWith("dart.")) {
      dartLibraries.put(name, ref);
    }
    else if (name.startsWith("file:///")) {
      // Is user code (not in a package or library)
      // TODO(terry): Need to store local file names for each libraryRef we'll always display classes from user code.
      allLibraries.put(name, ref);
    }
    else {
      if (ref.getUri().startsWith(DART_LIBRARY_PREFIX)) {
        // Library named 'nativewrappers' but URI is 'dart:nativewrappers' is a Dart library.
        dartLibraries.put(name, ref);
      }
      else if (ref.getUri().startsWith(FLUTTER_LIBRARY_PREFIX)) {
        flutterLibraries.put(name, ref);
      }
      else {
        allLibraries.put(name, ref);
      }
    }
  }

  public CompletableFuture<JsonObject> vmGetObject(String classOrInstanceRefId) {
    final CompletableFuture<JsonObject> future = new CompletableFuture<JsonObject>();

    FlutterStudioProfilers profilers = getStage().getStudioProfilers();

    profilers.getApp().getVmService().getObject(isolateId, classOrInstanceRefId, new GetObjectConsumer() {
                                                  @Override
                                                  public void onError(RPCError error) {
                                                    future.completeExceptionally(new RuntimeException(error.toString()));
                                                  }

                                                  @Override
                                                  public void received(Obj response) {
                                                    updateClassesStatus("getObject " + classOrInstanceRefId + " processed.");
                                                    future.complete((response.getJson()));
                                                  }

                                                  @Override
                                                  public void received(Sentinel response) {
                                                    future.completeExceptionally(new RuntimeException(response.toString()));
                                                  }
                                                }
    );

    return future;
  }

  public CompletableFuture<JsonObject> getInstances(String classRef, int maxInstances) {
    final Map<String, Object> params = new HashMap<>();
    params.put("classId", classRef);
    params.put("limit", maxInstances);

    FlutterStudioProfilers profilers = getStage().getStudioProfilers();
    return profilers.getApp().callServiceExtension("_getInstances", params)
      .thenApply((response) -> response)
      .exceptionally(err -> {
        FlutterUtils.warn(LOG, err);
        return null;
      });
  }

  private void initializeVM() {
    FlutterStudioProfilers profilers = getStage().getStudioProfilers();
    VMServiceManager vmServiceMgr = profilers.getApp().getVMServiceManager();

    StreamSubscription<IsolateRef> subscription = vmServiceMgr.getCurrentFlutterIsolate((isolate) -> {
      CompletableFuture<Object> libraryRef = new CompletableFuture<>();
      if (libraryRef.isDone()) {
        libraryRef = new CompletableFuture<>();
      }

      if (isolate != null) {
        isolateId = isolate.getId();

        // Known libraries used for this application.
        vmGetLibraries();
      }
    }, true);
  }

  protected void closeClassObjectDetails() {
    memorySnapshot.removeAllInstanceChildren(true);
    memorySnapshot.removeAllClassChildren(true);

    memorySnapshot.myClassesTreeModel.reload();
    memorySnapshot.myInstancesTreeModel.reload();

    //heapObjectsScoller.setVisible(false);
    classesPanel.setVisible(false);
    instanceObjectsScoller.setVisible(false);
  }

  protected void displaySnapshot(FlutterStudioMonitorStageView view, boolean reset) {
    memorySnapshot.removeAllClassChildren(true);
    classesTable.updateUI();

    classesPanel.setVisible(true);

    // Let's do a snapshot...
    FlutterStudioProfilers profilers = getStage().getStudioProfilers();
    VmService vmService = profilers.getApp().getVmService();

    // TODO(terry): For now call AllocationProfile however, we need to request snapshot (binary data).

    final CompletableFuture<AllocationProfile> future = new CompletableFuture<AllocationProfile>();

    vmService.getAllocationProfile(isolateId, null, reset ? true : null, new AllocationProfileConsumer() {
      @Override
      public void onError(RPCError error) {
        FlutterUtils.warn(LOG, "Allocation Profile - " + error.getDetails());
        future.completeExceptionally(new RuntimeException(error.toString()));
      }

      @Override
      public void received(AllocationProfile response) {
        future.complete(response);

        updateClassesStatus("Allocations Received");

        memorySnapshot.decodeClassesInHeap(view, response, classesTable);
      }
    });
  }

  protected void gcNow(FlutterStudioMonitorStageView view) {
    FlutterStudioProfilers profilers = getStage().getStudioProfilers();
    VmService vmService = profilers.getApp().getVmService();

    final CompletableFuture<AllocationProfile> future = new CompletableFuture<AllocationProfile>();

    vmService.getAllocationProfile(isolateId, "full", null, new AllocationProfileConsumer() {
      @Override
      public void onError(RPCError error) {
        FlutterUtils.warn(LOG, "Allocation Profile during gcNow - " + error.getDetails());
        future.completeExceptionally(new RuntimeException(error.toString()));
      }

      @Override
      public void received(AllocationProfile response) {
        // TODO(terry): Add GC Icon to overlay - should happen in FlutterAllMemoryData however handleGCEvent is never called.
        view.getStage().recordGC();
      }
    });
  }

  // TODO(terry): Need to remove when we 3.2 of AS isn't supported only post 3.2 have this png.
  private static Icon load(String path) {
    return IconLoader.getIcon(path);
  }

  public static final Icon ProfilerCheckMark = load("/icons/profiler/checkmark_laficon.png");

  private JBPanel buildUI(@NotNull FlutterStudioMonitorStage stage) {
    ProfilerTimeline timeline = stage.getStudioProfilers().getTimeline();

    SelectionModel selectionModel = new SelectionModel(timeline.getSelectionRange());
    SelectionComponent selection = new SelectionComponent(selectionModel, timeline.getViewRange());
    OverlayComponent overlayComponent = new OverlayComponent(selection);
    selection.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    selectionModel.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        // TODO(terry): Bring up the memory object list view using getTimeline().getSelectionRange().getMin() .. getMax().
        // TODO(terry): Need to record all memory statistics then the snapshot shows the collected range will need VM support.
        //displaySnapshot();
      }

      @Override
      public void selectionCleared() {
        // Clear stuff here
      }
    });

    RangeTooltipComponent
      tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(),
                                          timeline.getDataRange(), getTooltipPanel(),
                                          getProfilersView().getComponent(), () -> true);

    TabularLayout layout = new TabularLayout("*");
    JBPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    ProfilerScrollbar sb = new ProfilerScrollbar(timeline, panel);
    panel.add(sb, new TabularLayout.Constraint(3, 0));

    FlutterStudioProfilers profilers = stage.getStudioProfilers();
    JComponent timeAxis = buildTimeAxis(profilers);

    panel.add(timeAxis, new TabularLayout.Constraint(2, 0));

    JBPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JBLabel label = new JBLabel(TAB_NAME);
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(JBLabel.TOP);
    label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    final JBPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);

    // Initial size of Y-axis in MB set a range of 100 MB multiply is 1024 (1K), so upper range is 100 MB
    // to start with.
    Range yRange1Animatable = new Range(0, AXIS_SIZE);
    AxisComponent yAxisBytes;

    ResizingAxisComponentModel yAxisAxisBytesModel =
      new ResizingAxisComponentModel.Builder(yRange1Animatable, MemoryAxisFormatter.DEFAULT).setLabel(HEAP_LABEL).build();
    yAxisBytes = new AxisComponent(yAxisAxisBytesModel, AxisComponent.AxisOrientation.RIGHT);

    yAxisBytes.setShowMax(true);
    yAxisBytes.setShowUnitAtMax(true);
    yAxisBytes.setHideTickAtMin(true);
    yAxisBytes.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    yAxisBytes.setMargins(0, Y_AXIS_TOP_MARGIN);

    LineChartModel model = new LineChartModel();

    FlutterAllMemoryData.ThreadSafeData memoryUsedDataSeries = stage.getUsedDataSeries();
    FlutterAllMemoryData.ThreadSafeData memoryMaxDataSeries = stage.getCapacityDataSeries();
    FlutterAllMemoryData.ThreadSafeData memoryExternalDataSeries = stage.getExternalDataSeries();
    FlutterAllMemoryData.ThreadSafeData rssDataSeries = null;
    if (getProfilersView().displayRSSInformation) {
      rssDataSeries = stage.getRSSDataSeries();
    }

    FlutterAllMemoryData.ThreadSafeData gcDataSeries = stage.getGcDataSeries();
    FlutterAllMemoryData.ThreadSafeData resetDataSeries = stage.getResetDataSeries();
    FlutterAllMemoryData.ThreadSafeData snapshotDataSeries = stage.getSnapshotDataSeries();


    Range dataRanges = new Range(0, 1024 * 1024 * 100);
    Range viewRange = getTimeline().getViewRange();

    RangedContinuousSeries usedMemoryRange =
      new RangedContinuousSeries("Used", viewRange, dataRanges, memoryUsedDataSeries);
    RangedContinuousSeries maxMemoryRange =
      new RangedContinuousSeries("Capacity", viewRange, dataRanges, memoryMaxDataSeries);
    RangedContinuousSeries externalMemoryRange =
      new RangedContinuousSeries("External", viewRange, dataRanges, memoryExternalDataSeries);
    RangedContinuousSeries rssRange = rssRange = null;
    if (getProfilersView().displayRSSInformation) {
      rssRange = new RangedContinuousSeries("RSS", viewRange, dataRanges, rssDataSeries);
      model.add(rssRange);            // Plot used RSS size line.
    }

    model.add(maxMemoryRange);        // Plot total size of allocated heap.
    model.add(externalMemoryRange);   // Plot total size of external memory (bottom of stacked chart).
    model.add(usedMemoryRange);       // Plot used memory (top of stacked chart).

    // TODO(terry): Used for debugging only.
    debugModel = model;

    getStage().getStudioProfilers().getUpdater().register(model);
    LineChart mLineChart = new LineChart(model);
    mLineChart.setBackground(JBColor.background());

    // TODO(terry): Looks nice but might be too much information in chart.  Only display the RSS in the legend.
    if (getProfilersView().displayRSSInformation) {
      mLineChart.configure(rssRange, new LineConfig(MEMORY_RSS)
        .setStroke(LineConfig.DEFAULT_LINE_STROKE).setLegendIconType(LegendConfig.IconType.LINE));
    }
    // Stacked chart of external and used memory.
    configureStackedFilledLine(mLineChart, MEMORY_USED, usedMemoryRange);
    configureStackedFilledLine(mLineChart, MEMORY_EXTERNAL, externalMemoryRange);

    mLineChart.configure(maxMemoryRange, new LineConfig(MEMORY_CAPACITY)
      .setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    mLineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    mLineChart.setTopPadding(Y_AXIS_TOP_MARGIN);
    mLineChart.setFillEndGap(true);

    final JBPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    axisPanel.add(yAxisBytes, BorderLayout.WEST);

    // Render the GC glyphs.
    final DurationDataModel<GcDurationData> durationModel =
      new DurationDataModel<>(new RangedSeries<>(viewRange, new GcStatsDataSeries(gcDataSeries)));
    durationModel.setAttachedSeries(maxMemoryRange, Interpolatable.SegmentInterpolator);
    durationModel.update(0);

    // Build the GC icon rendering.
    final DurationDataRenderer<GcDurationData> gcRenderer = new DurationDataRenderer.Builder<>(durationModel, JBColor.BLACK)
      .setIcon(StudioIcons.Profiler.Events.GARBAGE_EVENT)
      // Need to offset the GcDurationData by the margin difference between the overlay component and the
      // line chart. This ensures we are able to render the Gc events in the proper locations on the line.
      .setLabelOffsets(-StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconWidth() / 2f,
                       StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconHeight() / 2f)
      .setHostInsets(new Insets(Y_AXIS_TOP_MARGIN, 0, 100, 0))
      // TODO(terry): Need to display number of bytes reclaimed e.g., QC(.07 MB) when handleGCEvent is called (doesn't work yet).
      .setLabelProvider(data -> String.format("GC"))
      .build();

    mLineChart.addCustomRenderer(gcRenderer);
    overlayComponent.addDurationDataRenderer(gcRenderer);

    // Build the Reset icon rendering.
    final DurationDataModel<ResetData> resetModel =
      new DurationDataModel<>(new RangedSeries<>(viewRange, new ResetStatsDataSeries(resetDataSeries)));
    resetModel.setAttachedSeries(maxMemoryRange, Interpolatable.SegmentInterpolator);
    resetModel.update(0);

    final DurationDataRenderer<ResetData> resetRenderer = new DurationDataRenderer.Builder<>(resetModel, JBColor.BLACK)
      .setIcon(FlutterIcons.ResetMemoryStats)
      .setHostInsets(new Insets(Y_AXIS_TOP_MARGIN, 0, 0, 0))
      .build();

    mLineChart.addCustomRenderer(resetRenderer);
    overlayComponent.addDurationDataRenderer(resetRenderer);

    // Build the Snapshot icon rendering.
    final DurationDataModel<SnapshotData> snapshotModel =
      new DurationDataModel<>(new RangedSeries<>(viewRange, new SnapshotDataSeries(snapshotDataSeries)));
    snapshotModel.setAttachedSeries(maxMemoryRange, Interpolatable.SegmentInterpolator);
    snapshotModel.update(0);

    final DurationDataRenderer<SnapshotData> snapshotRenderer = new DurationDataRenderer.Builder<>(snapshotModel, JBColor.BLACK)
      .setIcon(FlutterIcons.Snapshot)
      .setHostInsets(new Insets(Y_AXIS_TOP_MARGIN, 0, 0, 0))
      .build();

    mLineChart.addCustomRenderer(snapshotRenderer);
    overlayComponent.addDurationDataRenderer(snapshotRenderer);

    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    final OverlayComponent overlay = new OverlayComponent(selection);
    overlay.addDurationDataRenderer(gcRenderer);
    overlay.addDurationDataRenderer(resetRenderer);
    overlay.addDurationDataRenderer(snapshotRenderer);
    overlayPanel.add(overlay, BorderLayout.CENTER);

    final Range timelineDataRange = getTimeline().getDataRange();

    legendComponentModel = new LegendComponentModel(usedMemoryRange.getXRange());

    // TODO(terry): Check w/ Joshua on timeLineDataRange seems outside when ThreadSafeData's getDataForXRange is called.
    SeriesLegend legendRss = null;
    if (getProfilersView().displayRSSInformation) {
      legendRss = new SeriesLegend(rssRange, MEMORY_AXIS_FORMATTER, timelineDataRange);
      legendComponentModel.add(legendRss);
    }

    final SeriesLegend legendMax = new SeriesLegend(maxMemoryRange, MEMORY_AXIS_FORMATTER, timelineDataRange);
    legendComponentModel.add(legendMax);
    final SeriesLegend legendUsed = new SeriesLegend(usedMemoryRange, MEMORY_AXIS_FORMATTER, timelineDataRange);
    legendComponentModel.add(legendUsed);
    final SeriesLegend legendExternal = new SeriesLegend(externalMemoryRange, MEMORY_AXIS_FORMATTER, timelineDataRange);
    legendComponentModel.add(legendExternal);

    legendComponent = new LegendComponent(legendComponentModel);

    if (getProfilersView().displayRSSInformation) {
      legendComponent.configure(legendRss, new LegendConfig(LegendConfig.IconType.LINE, MEMORY_RSS));
    }      new DurationDataModel<>(new RangedSeries<>(viewRange, new ResetStatsDataSeries(resetDataSeries)));

    legendComponent.configure(legendMax, new LegendConfig(LegendConfig.IconType.DASHED_LINE, MEMORY_CAPACITY));
    legendComponent.configure(legendUsed, new LegendConfig(LegendConfig.IconType.BOX, MEMORY_USED));
    legendComponent.configure(legendExternal, new LegendConfig(LegendConfig.IconType.BOX, MEMORY_EXTERNAL));

    // Place legend in a panel.
    final JBPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(legendComponent, BorderLayout.EAST);
    // Give the right-side edge a 30 pixel gap from the "Heap" name in the right-side chart title.
    Border border = legendPanel.getBorder();
    final Border margin = new EmptyBorder(0, 0, 0, 30);
    legendPanel.setBorder(new CompoundBorder(border, margin));

    // Make the legend visible.
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));

    monitorPanel.add(tooltip, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(overlayComponent, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(yAxisBytes, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(mLineChart, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(1, "*"); // Give monitor as much space as possible
    panel.add(monitorPanel, new TabularLayout.Constraint(1, 0));

    return panel;
  }

  private static void configureStackedFilledLine(LineChart chart, Color color, RangedContinuousSeries series) {
    chart.configure(series, new LineConfig(color).setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
  }

  private void expandMonitor(ProfilerMonitor monitor) {
    // Track first, so current stage is sent with the event
    // TODO(terry): Needed to go from minimized to selected zoomed out view.
    //getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectMonitor();
    monitor.expand();
  }

  @Override
  public JComponent getToolbar() {
    // TODO(terry): What should I return here?
    return new JBPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }

  private DefaultMutableTreeNode addNode(DefaultMutableTreeNode parent, String fieldName, String value) {
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode(fieldName + value);

    SwingUtilities.invokeLater(() -> {
      parent.insert(node, parent.getChildCount());

      DefaultTreeModel model = (DefaultTreeModel)instanceObjects.getModel();
      model.reload(node);
      instanceObjects.getAccessibleContext().getAccessibleSelection().clearAccessibleSelection();
    });

    return node;
  }

  // Node Place holder for an object that we have not yet ask the VM to interrogated viewing its values.
  private DefaultMutableTreeNode addPlaceHodlerNode(DefaultMutableTreeNode parent, String objectRefName) {
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode(objectRefName);

    SwingUtilities.invokeLater(() -> {
      parent.insert(node, parent.getChildCount());
      DefaultTreeModel model = (DefaultTreeModel)instanceObjects.getModel();
      model.reload(node);
    });

    return node;
  }

  void getObject(DefaultMutableTreeNode parent, String objectRef) {
    // Remove our place holder node.
    parent.remove(0);

    // Interrogate the values of the object.
    AsyncUtils.whenCompleteUiThread(vmGetObject(objectRef), (JsonObject response, Throwable exception) -> {
      Stack<String> objectStack = new Stack<String>();

      if (exception instanceof RuntimeException && exception.getMessage().startsWith("org.dartlang.vm.service.element.Sentinel@")) {
        // Object is now a sentinel signal that change.
        Memory.InstanceNode userNode = (Memory.InstanceNode)(parent.getUserObject());
        String userNodeName = userNode.getObjectRef();
        if (!userNodeName.endsWith(" [Sentinel]")) {
          userNode.setObjectRef(userNodeName + " [Sentinel]");
          SwingUtilities.invokeLater(() -> {
            memorySnapshot.myInstancesTreeModel.reload(parent);
          });
        }
        return;
      }

      Instance instance = new Instance(response);

      ElementList<BoundField> fields = instance.getFields();
      if (!fields.isEmpty()) {
        final Iterator<BoundField> iter = fields.iterator();
        ;
        while (iter.hasNext()) {
          BoundField field = iter.next();
          String fieldName = field.getDecl().getName();
          InstanceRef valueRef = field.getValue();
          InstanceKind valueKind = valueRef.getKind();

          switch (valueKind) {
            case BoundedType:
              // TODO(terry): Not sure what this is?
              addNode(parent, fieldName, " = [BoundType]");
              break;

            case Closure:
              // TODO(terry): Should we should the function (or at least be able to navigate to the function)?
              addNode(parent, fieldName, " = [Closure]");
              break;

            // Primitive Dart Types display raw value
            case Bool:
            case Double:
            case Float32x4:
            case Float64x2:
            case Int:
            case Int32x4:
            case Null:
            case String:
              try {
                final String fieldValue = valueRef.getValueAsString();
                addNode(parent, fieldName, " = " + fieldValue);
              }
              catch (Exception e) {
                FlutterUtils.warn(LOG, "Error getting value " + valueRef.getKind(), e);
              }
              break;

            case List:
            case Map:
              // Pointing to a nested class.
              if (valueRef == null) {
                // TODO(terry): This shouldn't happen.
                FlutterUtils.warn(LOG, "ValueRef is NULL");
              }
              final String nestedObjectRef1 = valueRef.getId();    // Pull the object/Class we're pointing too.
              final DefaultMutableTreeNode node1 = addNode(parent, fieldName, " [" + nestedObjectRef1 + "]");

              // Add a placeholder for this object being interogated iff the user clicks on the expand then we'll
              // call the VM to drill into the object.
              addPlaceHodlerNode(node1, nestedObjectRef1);
              break;

            case MirrorReference:
              // TODO(terry): Not sure what to show other than its a mirror.
              addNode(parent, fieldName, " = [Mirror]");
              break;

            // A general instance of the Dart class Object.
            case Float32List:
            case Float32x4List:
            case Float64List:
            case Float64x2List:
            case Int16List:
            case Int32List:
            case Int32x4List:
            case Int64List:
            case Int8List:
            case Uint16List:
            case Uint32List:
            case Uint64List:
            case Uint8ClampedList:
            case Uint8List:
              // Pointing to a nested class.
              if (valueRef == null) {
                // TODO(terry): This shouldn't happen.
                FlutterUtils.warn(LOG, "ValueRef is NULL for nnnnnList");
              }
              break;

            case PlainInstance:
              // Pointing to a nested class.
              if (valueRef == null) {
                // TODO(terry): This shouldn't happen.
                FlutterUtils.warn(LOG, "ValueRef is NULL");
              }
              final String nestedObjectRef = valueRef.getId();    // Pull the object/Class we're pointing too.
              final DefaultMutableTreeNode node = addNode(parent, fieldName, " [" + nestedObjectRef + "]");

              // Add a placeholder for this object being interogated iff the user clicks on the expand then we'll
              // call the VM to drill into the object.
              addPlaceHodlerNode(node, nestedObjectRef);
              break;

            case RegExp:
            case StackTrace:
            case Type:
            case TypeParameter:
            case TypeRef:
            case WeakProperty:
              // TODO(terry): Should we show something other than special?
              addNode(parent, fieldName, " = [SPECIAL]");
              break;

            case Unknown:
              addNode(parent, fieldName, " = [UNKNOWN]");
              break;
          }
        }

        SwingUtilities.invokeLater(() -> {
          memorySnapshot.myInstancesTreeModel.reload(parent);
        });
      }
      else if (instance.getKind() == InstanceKind.List) {
        // Empty list.
        addNode(parent, "", "[]");
      }
      else if (instance.getKind() == InstanceKind.Map) {
        // Empty list.
        addNode(parent, "", "{}");
      }
    });
  }
}
