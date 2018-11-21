/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.profilers.*;
import com.android.tools.profilers.sessions.SessionsView;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBEmptyBorder;
import icons.StudioIcons;
import io.flutter.utils.AsyncUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.dartlang.vm.service.element.ElementList;
import org.dartlang.vm.service.element.Library;
import org.dartlang.vm.service.element.LibraryDependency;
import org.dartlang.vm.service.element.LibraryRef;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.function.BiFunction;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerFonts.H4_FONT;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_HEIGHT;
import static io.flutter.profiler.FilterLibraryDialog.ALL_DART_LIBRARIES;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

// Refactored from Android 3.2 Studio adt-ui code.
public class FlutterStudioProfilersView
  extends AspectObserver implements Disposable {

  private static int runningFilterCollection = 0;
  private static final Logger LOG = Logger.getInstance(FlutterStudioProfilersView.class);

  private final static String LOADING_VIEW_CARD = "LoadingViewCard";
  private final static String STAGE_VIEW_CARD = "StageViewCard";
  private static final int SHORTCUT_MODIFIER_MASK_NUMBER = SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK;
  @NotNull public static final String ATTACH_LIVE = "Attach to live";
  @NotNull public static final String DETACH_LIVE = "Detach live";
  @NotNull public static final String ZOOM_IN = "Zoom in";
  @NotNull public static final String ZOOM_OUT = "Zoom out";

  private final FlutterStudioProfilers profiler;
  private final ViewBinder<FlutterStudioProfilersView, FlutterStage,
    FlutterStageView> binder;
  private FlutterStageView stageView;

  @NotNull
  private final ProfilerLayeredPane layeredPane;
  /**
   * Splitter between the sessions and main profiler stage panel. We use IJ's
   * {@link ThreeComponentsSplitter} as it supports zero-width
   * divider while still handling mouse resize properly.
   */
  @NotNull private final ThreeComponentsSplitter splitter;
  //@NotNull private final LoadingPanel stageLoadingPanel;
  private final JBPanel stageComponent;
  private final JBPanel stageCenterComponent;
  private final CardLayout stageCenterCardLayout;
  private SessionsView sessionsView;
  private JBPanel toolbar;
  private JBPanel stageToolbar;
  private JBPanel monitoringToolbar;
  private JBPanel commonToolbar;
  private JBPanel goLiveToolbar;
  private JToggleButton goLive;
  private CommonButton zoomOut;
  private CommonButton zoomIn;
  private CommonButton resetZoom;
  private CommonButton frameSelection;
  private ProfilerAction frameSelectionAction;
  private CommonButton snapshot;                    // Snapshot the VM memory.
  private CommonButton filterLibrary;               // Filter libraries
  private CommonButton resetSnapshotStatistics;     // Compact the GC
  private Set<String> selectedLibraries;

  public FlutterStudioProfilersView(@NotNull FlutterStudioProfilers theProfiler) {
    profiler = theProfiler;
    stageView = null;
    selectedLibraries = new HashSet<String>();

    stageComponent = new JBPanel(new BorderLayout());
    stageCenterCardLayout = new CardLayout();
    stageCenterComponent = new JBPanel(stageCenterCardLayout);

    // TODO(terry): Multiple profiler views.
    //stageLoadingPanel = myIdeProfilerComponents.createLoadingPanel(0);
    //stageLoadingPanel.setLoadingText("");
    //stageLoadingPanel.getComponent().setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    splitter = new ThreeComponentsSplitter();
    // Override the splitter's custom traversal policy back to the default,
    // because the custom policy prevents the profilers from tabbing
    // across the components (e.g. sessions panel and the main stage UI).
    splitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    splitter.setDividerWidth(0);
    splitter.setDividerMouseZoneSize(-1);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setLastComponent(stageComponent);
    Disposer.register(this, splitter);

    layeredPane = new ProfilerLayeredPane(splitter);

    initializeStageUi();

    binder = new ViewBinder<>();
    binder.bind(FlutterStudioMonitorStage.class, FlutterStudioMonitorStageView::new);
    // TODO(terry): Multiple profiler views.
    //binder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    //binder.bind(MemoryProfilerStage.class, MemoryProfilerStageView::new);
    //binder.bind(NetworkProfilerStage.class, NetworkProfilerStageView::new);
    //binder.bind(NullMonitorStage.class, NullMonitorStageView::new); // This is for no device detected
    //binder.bind(EnergyProfilerStage.class, EnergyProfilerStageView::new);

    profiler.addDependency(this)
      .onChange(ProfilerAspect.STAGE, this::updateStageView);
    updateStageView();
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  public <S extends FlutterStage, T extends FlutterStageView>
  void bind(@NotNull Class<S> clazz,
            @NotNull BiFunction<FlutterStudioProfilersView, S, T> constructor) {

    binder.bind(clazz, constructor);
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomInButton() {
    return zoomIn;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomOutButton() {
    return zoomOut;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getResetZoomButton() {
    return resetZoom;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getFrameSelectionButton() {
    return frameSelection;
  }

  @VisibleForTesting
  @NotNull
  JToggleButton getGoLiveButton() {
    return goLive;
  }

  @VisibleForTesting
  public FlutterStageView getStageView() {
    return stageView;
  }

  @VisibleForTesting
  @NotNull
  SessionsView getSessionsView() {
    return sessionsView;
  }

  private void initializeStageUi() {
    toolbar = new JBPanel(new BorderLayout());
    JBPanel leftToolbar = new JBPanel(ProfilerLayout.createToolbarLayout());

    toolbar.setBorder(DEFAULT_BOTTOM_BORDER);
    toolbar.setPreferredSize(new Dimension(0, TOOLBAR_HEIGHT));

    commonToolbar = new JBPanel(ProfilerLayout.createToolbarLayout());
    JButton button = new CommonButton(StudioIcons.Common.BACK_ARROW);
    button.addActionListener(action -> {
      profiler.setMonitoringStage();
      //profiler.getIdeServices().getFeatureTracker().trackGoBack();
    });
    commonToolbar.add(button);
    commonToolbar.add(new FlatSeparator());

    JComboBox<Class<? extends FlutterStage>> stageCombo = new FlatComboBox<>();
    JComboBoxView stages = new JComboBoxView<>(stageCombo, profiler,
                                               ProfilerAspect.STAGE,
                                               profiler::getDirectStages,
                                               profiler::getStageClass,
                                               stage -> {
                                                 // Track first, so current
                                                 // stage is sent the event
                                                 //profiler.getIdeServices().getFeatureTracker().trackSelectMonitor();
                                                 profiler.setNewStage(stage);
                                               });
    stageCombo.setRenderer(new StageComboBoxRenderer());
    stages.bind();
    commonToolbar.add(stageCombo);
    commonToolbar.add(new FlatSeparator());

    monitoringToolbar = new JBPanel(ProfilerLayout.createToolbarLayout());

    leftToolbar.add(commonToolbar);
    toolbar.add(leftToolbar, BorderLayout.WEST);

    snapshot = new CommonButton("Snapshot");
    snapshot.addActionListener(event -> {
      FlutterStudioMonitorStageView view = (FlutterStudioMonitorStageView)(this.getStageView());
      view.displaySnapshot(view, false);
      view.updateClassesStatus("Snapshoting...");
      snapshot.setEnabled(false);
      resetSnapshotStatistics.setEnabled(false);
    });
    snapshot.setToolTipText("Snapshot of VM's memory");
    leftToolbar.add(snapshot);


    filterLibrary = new CommonButton("Filter");
    filterLibrary.addActionListener(filterEvent -> {
      FlutterStudioMonitorStageView view = (FlutterStudioMonitorStageView)(this.getStageView());

      Set<String> libraryKeys = view.allLibraries.keySet();
      List<String> libraryNames = Arrays.asList(libraryKeys.toArray(new String[libraryKeys.size()]));
      Collections.sort((libraryNames));

      runningFilterCollection = 0;

      JFrame parentFrame = (JFrame)SwingUtilities.windowForComponent(toolbar);
      Set<String> selectedOnes = loadFilterDialog(parentFrame, libraryNames.toArray(new String[0]), selectedLibraries);
      if (selectedOnes != null) {
        // New libraries to filter.
        selectedLibraries = selectedOnes;
        view.filteredLibraries = new HashSet<String>();
      }

      if (view.classesPanel.isVisible()) {
        view.memorySnapshot.resetFilteredClasses();
        // Update the classes table if visible.  If not, we'll wait for a snapshot click.
        view.memorySnapshot.removeAllClassChildren(true);
      }

      selectedLibrariesToFilter(selectedLibraries, true);
      view.getClassesTable().updateUI();
    });
    filterLibrary.setToolTipText("Filter Dart Libraries");
    leftToolbar.add(filterLibrary);

    resetSnapshotStatistics = new CommonButton("Reset Stats");
    resetSnapshotStatistics.addActionListener(event -> {
      FlutterStudioMonitorStageView view = (FlutterStudioMonitorStageView)(this.getStageView());

      view.displaySnapshot(view, true);

      view.updateClassesStatus("Reset Snapshot...");

      snapshot.setEnabled(false);
      resetSnapshotStatistics.setEnabled(false);
    });
    resetSnapshotStatistics.setToolTipText("Reset Snapshot Statistics");
    leftToolbar.add(resetSnapshotStatistics);

    JBPanel rightToolbar = new JBPanel(ProfilerLayout.createToolbarLayout());
    toolbar.add(rightToolbar, BorderLayout.EAST);
    rightToolbar.setBorder(new JBEmptyBorder(0, 0, 0, 2));

    ProfilerTimeline timeline = profiler.getTimeline();
    zoomOut = new CommonButton(StudioIcons.Common.ZOOM_OUT);
    zoomOut.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_OUT));
    zoomOut.addActionListener(event -> {
      timeline.zoomOut();
      //profiler.getIdeServices().getFeatureTracker().trackZoomOut();
    });
    ProfilerAction zoomOutAction =
      new ProfilerAction.Builder(ZOOM_OUT)
        .setContainerComponent(stageComponent)
        .setActionRunnable(() -> zoomOut.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();

    zoomOut.setToolTipText(zoomOutAction.getDefaultToolTipText());
    rightToolbar.add(zoomOut);

    zoomIn = new CommonButton(StudioIcons.Common.ZOOM_IN);
    zoomIn.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_IN));
    zoomIn.addActionListener(event -> {
      timeline.zoomIn();
      //profiler.getIdeServices().getFeatureTracker().trackZoomIn();
    });
    ProfilerAction zoomInAction =
      new ProfilerAction.Builder(ZOOM_IN).setContainerComponent(stageComponent)
        .setActionRunnable(() -> zoomIn.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();
    zoomIn.setToolTipText(zoomInAction.getDefaultToolTipText());
    rightToolbar.add(zoomIn);

    resetZoom = new CommonButton(StudioIcons.Common.RESET_ZOOM);
    resetZoom.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM));
    resetZoom.addActionListener(event -> {
      timeline.resetZoom();
      //profiler.getIdeServices().getFeatureTracker().trackResetZoom();
    });
    ProfilerAction resetZoomAction =
      new ProfilerAction.Builder("Reset zoom").setContainerComponent(stageComponent)
        .setActionRunnable(() -> resetZoom.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0),
                       KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)).build();
    resetZoom.setToolTipText(resetZoomAction.getDefaultToolTipText());
    rightToolbar.add(resetZoom);

    frameSelection = new CommonButton(StudioIcons.Common.ZOOM_SELECT);
    frameSelection.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT));
    frameSelection.addActionListener(event -> {
      timeline.frameViewToRange(timeline.getSelectionRange());
    });
    frameSelectionAction = new ProfilerAction.Builder("Zoom to Selection")
      .setContainerComponent(stageComponent)
      .setActionRunnable(() -> frameSelection.doClick(0))
      .setEnableBooleanSupplier(() -> !timeline.getSelectionRange().isEmpty()).build();
    frameSelection.setToolTipText(frameSelectionAction.getDefaultToolTipText());
    rightToolbar.add(frameSelection);
    timeline.getSelectionRange()
      .addDependency(this)
      .onChange(Range.Aspect.RANGE,
                () -> frameSelection.setEnabled(frameSelectionAction.isEnabled()));

    goLiveToolbar = new JBPanel(ProfilerLayout.createToolbarLayout());
    goLiveToolbar.add(new FlatSeparator());

    goLive = new CommonToggleButton("", StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    goLive.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.GOTO_LIVE));
    goLive.setFont(H4_FONT);
    goLive.setHorizontalTextPosition(SwingConstants.LEFT);
    goLive.setHorizontalAlignment(SwingConstants.LEFT);
    goLive.setBorder(new JBEmptyBorder(3, 7, 3, 7));
    // Configure shortcuts for GoLive.
    ProfilerAction attachAction =
      new ProfilerAction.Builder(ATTACH_LIVE).setContainerComponent(stageComponent)
        .setActionRunnable(() -> goLive.doClick(0))
        .setEnableBooleanSupplier(
          () -> goLive.isEnabled() &&
                !goLive.isSelected() &&
                stageView.navigationControllersEnabled())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,
                                              SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();
    ProfilerAction detachAction =
      new ProfilerAction.Builder(DETACH_LIVE).setContainerComponent(stageComponent)
        .setActionRunnable(() -> goLive.doClick(0))
        .setEnableBooleanSupplier(
          () -> goLive.isEnabled() &&
                goLive.isSelected() &&
                stageView.navigationControllersEnabled())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,
                                              0)).build();

    goLive.setToolTipText(detachAction.getDefaultToolTipText());
    goLive.addActionListener(event -> {
      timeline.toggleStreaming();
      //profiler.getIdeServices().getFeatureTracker().trackToggleStreaming();
    });
    goLive.addChangeListener(e -> {
      boolean isSelected = goLive.isSelected();
      goLive.setIcon(isSelected ? StudioIcons.Profiler.Toolbar.PAUSE_LIVE : StudioIcons.Profiler.Toolbar.GOTO_LIVE);
      goLive.setToolTipText(isSelected ? detachAction.getDefaultToolTipText() : attachAction.getDefaultToolTipText());
    });
    timeline.addDependency(this).onChange(ProfilerTimeline.Aspect.STREAMING, this::updateStreaming);
    goLiveToolbar.add(goLive);
    rightToolbar.add(goLiveToolbar);

    ProfilerContextMenu.createIfAbsent(stageComponent)
      .add(attachAction, detachAction, ContextMenuItem.SEPARATOR, zoomInAction, zoomOutAction);
    toggleTimelineButtons();

    stageToolbar = new JBPanel(new BorderLayout());
    toolbar.add(stageToolbar, BorderLayout.CENTER);

    stageComponent.add(toolbar, BorderLayout.NORTH);
    stageComponent.add(stageCenterComponent, BorderLayout.CENTER);

    updateStreaming();
  }

  private void selectedLibrariesToFilter(Set<String> selectedLibraries, boolean processTheLibrary) {
    FlutterStudioMonitorStageView view = (FlutterStudioMonitorStageView)(this.getStageView());

    selectedLibraries.forEach((String libraryName) -> {
      LibraryRef libraryRef = view.allLibraries.get(libraryName);
      if (libraryRef == null) {
        if (libraryName == ALL_DART_LIBRARIES) {
          // Filter all dart libraries.
          view.dartLibraries.forEach((String key, LibraryRef dartLibraryRef) -> {
            String dartLibraryId = dartLibraryRef.getId();
            view.filteredLibraries.add(dartLibraryId);
            if (processTheLibrary) {
              processLibrary(view, dartLibraryId);
            }
          });
        }
        LOG.warn("Library not found " + libraryName);
      }
      else {
        String libraryId = libraryRef.getId();
        view.filteredLibraries.add(libraryId);
        if (processTheLibrary) {
          processLibrary(view, libraryId);
        }
      }
    });
  }

  // Process a library ID to see all
  private void processLibrary(FlutterStudioMonitorStageView view, String libraryId) {
    runningFilterCollection++;
    AsyncUtils.whenCompleteUiThread(view.vmGetObject(libraryId), (JsonObject response, Throwable exception) -> {
      Library library = new Library(response);
      ElementList<LibraryDependency> dependencies = library.getDependencies();
      for (LibraryDependency libraryDependency : dependencies) {
        view.filteredLibraries.add(libraryDependency.getTarget().getId());
      }
      if (--runningFilterCollection == 0) {
        // All libraries have been computed update the current snapshot.
        // Filter the ClassesTable if it's visible - update the model/view.
        if (view.classesPanel.isVisible()) {
          Iterator<Memory.AllClassesInformation> allClassesIterator = view.memorySnapshot.allClassesUnfiltered.iterator();
          while (allClassesIterator.hasNext()) {
            Memory.AllClassesInformation currentClass = allClassesIterator.next();
            view.memorySnapshot.filterClassesTable(view, view.getClassesTable(), currentClass);
          }
        }
        view.updateClassesStatus(
          "Filtering " + view.filteredLibraries.size() + " libraries for " + view.memorySnapshot.getFilteredClassesCount() + " classes.");
      }
    });
  }

  public void snapshotComplete() {
    snapshot.setEnabled(true);
    resetSnapshotStatistics.setEnabled(true);
  }

  private void toggleTimelineButtons() {
    zoomOut.setEnabled(true);
    zoomIn.setEnabled(true);
    resetZoom.setEnabled(true);
    frameSelection.setEnabled(frameSelectionAction.isEnabled());
    goLive.setEnabled(true);
    goLive.setSelected(true);
  }

  private void updateStreaming() {
    goLive.setSelected(profiler.getTimeline().isStreaming());
  }

  private void updateStageView() {
    FlutterStage stage = profiler.getStage();
    if (stageView != null && stageView.getStage() == stage) {
      return;
    }

    stageView = binder.build(this, stage);
    SwingUtilities.invokeLater(() -> stageView.getComponent().requestFocusInWindow());

    stageCenterComponent.removeAll();
    stageCenterComponent.add(stageView.getComponent(), STAGE_VIEW_CARD);
    //stageCenterComponent.add(stageLoadingPanel.getComponent(), LOADING_VIEW_CARD);
    stageCenterComponent.revalidate();
    stageToolbar.removeAll();
    stageToolbar.add(stageView.getToolbar(), BorderLayout.CENTER);
    stageToolbar.revalidate();
    toolbar.setVisible(stageView.isToolbarVisible());
    goLiveToolbar.setVisible(stageView.navigationControllersEnabled());

    boolean topLevel = stageView == null || stageView.needsProcessSelection();
    monitoringToolbar.setVisible(topLevel);
    commonToolbar.setVisible(!topLevel && stageView.navigationControllersEnabled());
  }

  void setInitialSelectedLibraries(Set<String> allLibraries) {
    selectedLibraries = allLibraries;
    selectedLibrariesToFilter(allLibraries, false);
  }

  @NotNull
  public JLayeredPane getComponent() {
    return layeredPane;
  }

  /**
   * Installs the {@link ContextMenuItem} common to all profilers.
   *
   * @param component
   */
  public void installCommonMenuItems(@NotNull JComponent component) {
    //ContextMenuInstaller contextMenuInstaller = getIdeProfilerComponents().createContextMenuInstaller();
    //ProfilerContextMenu.createIfAbsent(stageComponent).getContextMenuItems()
    //                   .forEach(item -> contextMenuInstaller.installGenericContextMenu(component, item));
  }

  @VisibleForTesting
  final JBPanel getStageComponent() {
    return stageComponent;
  }


  @VisibleForTesting
  public static class StageComboBoxRenderer extends ColoredListCellRenderer<Class> {

    private static ImmutableMap<Class<? extends FlutterStage>, String> CLASS_TO_NAME = ImmutableMap.of(
      //CpuProfilerStage.class, "CPU",
      //MemoryProfilerStage.class, "MEMORY",
      //NetworkProfilerStage.class, "NETWORK",
      //EnergyProfilerStage.class, "ENERGY"
    );

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Class value, int index, boolean selected, boolean hasFocus) {
      String name = CLASS_TO_NAME.get(value);
      append(name == null ? "[UNKNOWN]" : name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  static public Set<String> loadFilterDialog(JFrame parentFrame, String[] libraryNames, Set<String> selectedLibraries) {
    FilterLibraryDialog filterDialog = new FilterLibraryDialog(parentFrame,
                                                               libraryNames,
                                                               selectedLibraries);
    if (filterDialog.showAndGet()) {
      // OK pressed.
      return filterDialog.selectedLibraries();
    }

    // Nothing selected.
    return null;
  }
}
