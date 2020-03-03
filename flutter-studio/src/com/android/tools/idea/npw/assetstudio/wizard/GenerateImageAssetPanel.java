/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio.wizard;

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.toLowerCamelCase;
import static com.android.tools.idea.npw.assetstudio.IconGenerator.getResDirectory;

import com.android.resources.Density;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.npw.assetstudio.DrawableRenderer;
import com.android.tools.idea.npw.assetstudio.GeneratedIcon;
import com.android.tools.idea.npw.assetstudio.GeneratedImageIcon;
import com.android.tools.idea.npw.assetstudio.IconCategory;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.icon.CategoryIconMap;
import com.android.tools.idea.npw.assetstudio.icon.IconGeneratorResult;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureIconPanel;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureIconView;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureLauncherIconPanel;
import com.android.tools.idea.npw.assetstudio.ui.PreviewIconsPanel;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A panel which presents a UI for selecting some source asset and converting it to a target set of
 * Android Icons. See {@link AndroidIconType} for the types of icons this can generate.
 *
 * Before generating icons, you should first check {@link #hasErrors()} to make sure there won't be
 * any errors in the generation process.
 *
 * This is a Swing port of the various icon generators provided by the
 * <a href="https://romannurik.github.io/AndroidAssetStudio/index.html">Asset Studio</a>
 * web application.
 */
public final class GenerateImageAssetPanel extends JPanel implements Disposable, PersistentStateComponent<PersistentState> {
  private static final String OUTPUT_ICON_TYPE_PROPERTY = "outputIconType";

  @NotNull private final AndroidModuleTemplate myDefaultPaths;
  private final ValidatorPanel myValidatorPanel;

  @NotNull private final BindingsManager myBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();

  private final Map<AndroidIconType, PreviewIconsPanel> myOutputPreviewPanels;
  private final ObjectProperty<AndroidIconType> myOutputIconType;
  private final StringProperty myOutputName = new StringValueProperty();

  private JPanel myRootPanel;
  private JComboBox<AndroidIconType> myIconTypeCombo;
  private JPanel myConfigureIconPanels;
  private Map<AndroidIconType, ConfigureIconView> myConfigureIconViews = new TreeMap<>();
  private CheckeredBackgroundPanel myOutputPreviewPanel;
  private TitledSeparator myOutputPreviewLabel;
  private JBScrollPane myOutputPreviewScrollPane;
  private JSplitPane mySplitPane;
  private JCheckBox myShowGrid;
  private JCheckBox myShowSafeZone;
  private JComboBox<Density> myPreviewResolutionComboBox;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myPreviewPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myIconTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myPreviewTitlePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myPreviewContentsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myOutputIconTypePanel;
  private SelectedProperty myShowGridProperty;
  private SelectedProperty myShowSafeZoneProperty;
  private AbstractProperty<Density> myPreviewDensityProperty;
  private JBLoadingPanel myLoadingPanel;

  @NotNull private AndroidModuleTemplate myPaths;
  @NotNull private final IconGenerationProcessor myIconGenerationProcessor = new IconGenerationProcessor();
  @NotNull private final StringProperty myPreviewRenderingError = new StringValueProperty();

  /**
   * Create a panel which can generate Android icons. The supported types passed in will be
   * presented to the user in a dropdown menu (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public GenerateImageAssetPanel(@NotNull Disposable disposableParent, @NotNull AndroidFacet facet,
                                 @NotNull AndroidModuleTemplate defaultPaths, @NotNull AndroidIconType... supportedTypes) {
    super(new BorderLayout());

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), panel -> new LoadingDecorator(panel, this, -1) {
      @Override
      protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
        final NonOpaquePanel panel = super.customizeLoadingLayer(parent, text, icon);
        Font font = text.getFont();
        text.setFont(font.deriveFont(font.getStyle(), font.getSize() + 6));
        //noinspection UseJBColor
        text.setForeground(ColorUtil.toAlpha(Color.BLACK, 100));
        panel.setOpaque(true);
        return panel;
      }
    });
    myLoadingPanel.add(myOutputPreviewPanel);
    myOutputPreviewScrollPane.getViewport().setView(myLoadingPanel);
    myLoadingPanel.setLoadingText("Rendering preview images");
    myLoadingPanel.startLoading();

    myDefaultPaths = defaultPaths;
    myPaths = myDefaultPaths;

    if (supportedTypes.length == 0) {
      supportedTypes = AndroidIconType.values();
    }

    DefaultComboBoxModel<AndroidIconType> supportedTypesModel = new DefaultComboBoxModel<>(supportedTypes);
    myIconTypeCombo.setModel(supportedTypesModel);
    myIconTypeCombo.setVisible(supportedTypes.length > 1);
    myOutputIconType = ObjectProperty.wrap(new SelectedItemProperty<>(myIconTypeCombo));
    myOutputPreviewPanel.setName("PreviewIconsPanel"); // for UI Tests

    myValidatorPanel = new ValidatorPanel(this, myRootPanel);

    myPreviewResolutionComboBox.setRenderer(new ListCellRendererWrapper<Density>() {
      @Override
      public void customize(JList list, Density value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.getResourceValue());
        }
      }
    });
    DefaultComboBoxModel<Density> densitiesModel = new DefaultComboBoxModel<>();
    densitiesModel.addElement(Density.MEDIUM);
    densitiesModel.addElement(Density.HIGH);
    densitiesModel.addElement(Density.XHIGH);
    densitiesModel.addElement(Density.XXHIGH);
    densitiesModel.addElement(Density.XXXHIGH);
    myPreviewResolutionComboBox.setModel(densitiesModel);
    myPreviewDensityProperty = ObjectProperty.wrap(new SelectedItemProperty<>(myPreviewResolutionComboBox));

    myShowGridProperty = new SelectedProperty(myShowGrid);
    myShowSafeZoneProperty = new SelectedProperty(myShowSafeZone);

    AndroidModuleInfo androidModuleInfo = AndroidModuleInfo.getInstance(facet);
    int minSdkVersion = androidModuleInfo.getMinSdkVersion().getApiLevel();

    // Create a card and a view for each icon type.
    assert myConfigureIconPanels.getLayout() instanceof CardLayout;
    DrawableRenderer renderer = new DrawableRenderer(facet);
    Disposer.register(this, renderer);
    for (AndroidIconType iconType : supportedTypes) {
      ConfigureIconView view;
      switch (iconType) {
        case LAUNCHER:
          view = new ConfigureLauncherIconPanel(this, facet, myShowGridProperty, myShowSafeZoneProperty,
                                                myPreviewDensityProperty, myValidatorPanel, renderer);
          break;
        case LAUNCHER_LEGACY:
        case ACTIONBAR:
        case NOTIFICATION:
          view = new ConfigureIconPanel(this, facet, iconType, minSdkVersion, renderer);
          break;
        default:
          throw new IllegalArgumentException("Invalid icon type");
      }
      myConfigureIconViews.put(iconType, view);
      myConfigureIconPanels.add(view.getRootComponent(), iconType.toString());
    }

    // Create an output preview panel for each icon type
    ImmutableMap.Builder<AndroidIconType, PreviewIconsPanel> previewPanelBuilder = ImmutableMap.builder();
    previewPanelBuilder.put(AndroidIconType.LAUNCHER, new LauncherIconsPreviewPanel());
    previewPanelBuilder.put(AndroidIconType.LAUNCHER_LEGACY, new LauncherLegacyIconsPreviewPanel());
    previewPanelBuilder.put(AndroidIconType.ACTIONBAR, new ActionBarIconsPreviewPanel());
    previewPanelBuilder.put(AndroidIconType.NOTIFICATION, new NotificationIconsPreviewPanel());
    myOutputPreviewPanels = previewPanelBuilder.build();

    WrappedFlowLayout previewLayout = new WrappedFlowLayout(FlowLayout.LEADING);
    previewLayout.setAlignOnBaseline(true);
    myOutputPreviewPanel.setLayout(previewLayout);
    myOutputPreviewScrollPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(@NotNull ComponentEvent event) {
        // When resizing the JScrollPane, we want the WrappedFlowLayout to re-layout components
        // based on the new size (i.e. width) of the viewport.
        myOutputPreviewPanel.revalidate();
      }
    });

    // Replace the JSplitPane component with a Splitter (IntelliJ look & feel).
    //
    // Note: We set the divider location on the JSplitPane from the left component preferred size to override the
    //       default divider location of the new Splitter (the default is to put the divider in the middle).
    mySplitPane.setDividerLocation(mySplitPane.getLeftComponent().getPreferredSize().width);
    GuiUtils.replaceJSplitPaneWithIDEASplitter(mySplitPane);

    initializeListenersAndBindings();
    initializeValidators();

    Disposer.register(disposableParent, this);
    Disposer.register(this, myValidatorPanel);
    add(myValidatorPanel);
  }

  private void initializeListenersAndBindings() {
    // Add listener to re-generate asset when options change in configuration panels
    ActionListener onAssetModified = actionEvent -> renderIconPreviews();
    for (ConfigureIconView view : myConfigureIconViews.values()) {
      view.addAssetListener(onAssetModified);
    }

    // Re-generate preview when icon type, "Show Grid" or "Show Safe Zone" change.
    Runnable updatePreview = () -> {
      ConfigureIconView iconView = getActiveIconView();
      myBindings.bind(myOutputName, iconView.outputName());
      myOutputPreviewLabel.setText("Preview");
      renderIconPreviews();
    };
    myListeners.receiveAndFire(myOutputIconType, iconType -> {
      ((CardLayout)myConfigureIconPanels.getLayout()).show(myConfigureIconPanels, iconType.toString());
      updatePreview.run();
    });
    myListeners.receiveAndFire(myShowGridProperty, selected -> updatePreview.run());
    myListeners.receiveAndFire(myShowSafeZoneProperty, selected -> updatePreview.run());
    myListeners.receiveAndFire(myPreviewDensityProperty, value -> updatePreview.run());

    // Show interactive preview components only if creating adaptive icons.
    BooleanExpression isAdaptiveIconOutput =
        BooleanExpression.create(() -> myOutputIconType.get() == AndroidIconType.LAUNCHER, myOutputIconType);
    myBindings.bind(new VisibleProperty(myShowGrid), isAdaptiveIconOutput);
    myBindings.bind(new VisibleProperty(myShowSafeZone), isAdaptiveIconOutput);
    myBindings.bind(new VisibleProperty(myPreviewResolutionComboBox), isAdaptiveIconOutput);
  }

  /**
   * Updates our output preview panel with icons generated in the output icon panel
   * of the current icon type.
   */
  private void updateOutputPreviewPanel() {
    myOutputPreviewPanel.removeAll();
    PreviewIconsPanel iconsPanel = myOutputPreviewPanels.get(myOutputIconType.get());
    for (PreviewIconsPanel.IconPreviewInfo previewInfo : iconsPanel.getIconPreviewInfos()) {
      ImagePreviewPanel previewPanel = new ImagePreviewPanel();
      previewPanel.getComponent().setName("IconPanel"); // for UI Tests
      previewPanel.setLabelText(previewInfo.getLabel());
      previewPanel.setImage(previewInfo.getImage());
      previewPanel.setImageBackground(previewInfo.getImageBackground());
      previewPanel.setImageOpaque(previewInfo.isImageOpaque());
      if (myOutputIconType.get() != AndroidIconType.LAUNCHER) {
        previewPanel.setImageBorder(previewInfo.getImageBorder());
      }

      myOutputPreviewPanel.add(previewPanel.getComponent());
    }
    myOutputPreviewPanel.revalidate();
    myOutputPreviewPanel.repaint();
  }

  @NotNull
  private ConfigureIconView getActiveIconView() {
    for (ConfigureIconView view : myConfigureIconViews.values()) {
      if (view.getRootComponent().isVisible()) {
        return view;
      }
    }

    throw new IllegalStateException(getClass().getSimpleName() + " is configured incorrectly. Please report this error.");
  }

  private void initializeValidators() {
    myValidatorPanel.registerValidator(myOutputName, outputName -> {
      String trimmedName = outputName.trim();
      if (trimmedName.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, "Icon name must be set");
      }
      else if (iconExists()) {
        return new Validator.Result(Validator.Severity.WARNING, "An icon with the same name already exists and will be overwritten.");
      }
      else {
        return Validator.Result.OK;
      }
    });

    myValidatorPanel.registerValidator(myPreviewRenderingError, errorMessage -> {
      if (!errorMessage.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, errorMessage);
      }
      else {
        return Validator.Result.OK;
      }
    });
  }

  /**
   * Set the target project paths that this panel should use when generating assets. If not set,
   * this panel will attempt to use reasonable defaults for the project.
   */
  @SuppressWarnings("unused") // Will be used when template wizard is updated to use this new class
  public void setProjectPaths(@Nullable AndroidModuleTemplate projectPaths) {
    myPaths = projectPaths != null ? projectPaths : myDefaultPaths;
  }

  /**
   * Set the output name for the icon type currently being edited. This is exposed as some UIs may wish
   * to set this explicitly instead of relying on defaults.
   */
  @SuppressWarnings("unused") // Will be used when template wizard is updated to use this new class
  public void setOutputName(@NotNull String name) {
    getActiveIconView().outputName().set(name);
  }

  /**
   * Returns an icon generator which will create Android icons using the panel's current settings.
   */
  @NotNull
  public IconGenerator getIconGenerator() {
    return getActiveIconView().getIconGenerator();
  }

  /**
   * A boolean property which will be true if validation logic catches any problems with any of the
   * current icon settings, particularly the output name / path. You should probably not generate
   * icons if there are any errors.
   */
  @NotNull
  public ObservableBool hasErrors() {
    return myValidatorPanel.hasErrors();
  }

  private boolean iconExists() {
    File resDirectory = getResDirectory(myPaths);
    if (resDirectory != null) {
      Map<File, GeneratedIcon> pathImageMap = getIconGenerator().generateIconPlaceholders(resDirectory);
      for (File path : pathImageMap.keySet()) {
        if (path.exists()) {
          return true;
        }
      }
    }

    return false;
  }

  private void renderIconPreviews() {
    // This method is often called as the result of a UI property changing which may also cause
    // some other properties to change. Due to asynchronous nature of some property changes, it
    // is necessary to use two invokeLater calls to make sure that everything settles before
    // icons generation is attempted.
    invokeVeryLate(this::enqueueGenerateNotificationIcons, ModalityState.any(), o -> Disposer.isDisposed(this));
  }

  /**
   * Generating notification icons is not a lightweight process, and if we try to do it
   * synchronously, it stutters the UI. So instead we enqueue the request to run on a background
   * thread. If several requests are made in a row while an existing worker is still in progress,
   * only the most recently added will be handled, whenever the worker finishes.
   */
  private void enqueueGenerateNotificationIcons() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    AndroidIconType iconType = myOutputIconType.get();
    IconGenerator iconGenerator = getActiveIconView().getIconGenerator();

    myIconGenerationProcessor.enqueue(iconGenerator, iconGeneratorResult -> {
      // There is no map if there was no source asset.
      if (iconGeneratorResult == null) {
        return;
      }

      myLoadingPanel.stopLoading();
      // Update the icon type specific output preview panel with the new preview images
      myOutputPreviewPanels.get(iconType).showPreviewImages(iconGeneratorResult);

      // Update the current preview panel only if the icon type has not changed since the request was enqueued.
      if (Objects.equals(iconType, myOutputIconType.get())) {
        updateOutputPreviewPanel();

        Collection<String> errors = iconGeneratorResult.getErrors();
        String errorMessage = errors.isEmpty() ?
                              "" :
                              errors.size() == 1 ?
                              "Preview rendering error: " + Iterables.getOnlyElement(errors) :
                              "Icon preview was rendered with errors";
        myPreviewRenderingError.set(errorMessage);
      }
    });
  }

  /**
   * Executes the given runnable after a double 'invokeLater' delay.
   */
  private static void invokeVeryLate(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired) {
    Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> application.invokeLater(runnable, state, expired), state, expired);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.set(OUTPUT_ICON_TYPE_PROPERTY, myOutputIconType.get(), AndroidIconType.LAUNCHER);
    for (Map.Entry<AndroidIconType, ConfigureIconView> entry: myConfigureIconViews.entrySet()) {
      state.setChild(toLowerCamelCase(entry.getKey()), entry.getValue().getState());
    }
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    myOutputIconType.set(state.get(OUTPUT_ICON_TYPE_PROPERTY, AndroidIconType.LAUNCHER));
    // Load persistent state of individual panels after dust settles.
    ApplicationManager.getApplication().invokeLater(
        () -> {
          for (Map.Entry<AndroidIconType, ConfigureIconView> entry: myConfigureIconViews.entrySet()) {
            PersistentStateUtil.load(entry.getValue(), state.getChild(toLowerCamelCase(entry.getKey())));
          }
        },
        ModalityState.any());
  }

  private static class LauncherIconsPreviewPanel extends PreviewIconsPanel {
    public LauncherIconsPreviewPanel() {
      super("", Theme.TRANSPARENT);
    }

    /**
     * Overrides the default implementation to show only preview images, sorted by
     * a predefined preview name/category order.
     */
    @Override
    public void showPreviewImages(@NotNull IconGeneratorResult result) {
      // Default
      Collection<GeneratedIcon> generatedIcons = result.getIcons();
      List<Pair<String, BufferedImage>> list = generatedIcons.stream()
        .filter(icon -> icon instanceof GeneratedImageIcon)
        .map(icon -> (GeneratedImageIcon)icon)
        .filter(icon -> filterPreviewIcon(icon, ((LauncherIconGenerator.LauncherIconOptions)result.getOptions()).previewDensity))
        .map(icon -> Pair.of(getPreviewShapeFromId(icon.getName()), icon.getImage()))
        .sorted((pair1, pair2) -> comparePreviewShapes(pair1.getFirst(), pair2.getFirst()))
        .map(pair -> Pair.of(pair.getFirst().displayName, pair.getSecond()))
        .collect(Collectors.toList());
      showPreviewImagesImpl(list);
    }

    protected boolean filterPreviewIcon(@NotNull GeneratedImageIcon icon, @NotNull Density density) {
      return
        Objects.equals(IconCategory.PREVIEW, icon.getCategory()) &&
        Objects.equals(icon.getDensity(), density);
    }

    private static int comparePreviewShapes(@NotNull LauncherIconGenerator.PreviewShape x, @NotNull LauncherIconGenerator.PreviewShape y) {
      return Integer.compare(getPreviewShapeDisplayOrder(x), getPreviewShapeDisplayOrder(y));
    }

    private static int getPreviewShapeDisplayOrder(@NotNull LauncherIconGenerator.PreviewShape previewShape) {
      switch (previewShape) {
        case CIRCLE:
          return 1;
        case SQUIRCLE:
          return 2;
        case ROUNDED_SQUARE:
          return 3;
        case SQUARE:
          return 4;
        case FULL_BLEED:
          return 5;
        case LEGACY:
          return 6;
        case LEGACY_ROUND:
          return 7;
        case WEB:
          return 8;
        case NONE:
        default:
          return 1000;  // Arbitrary high value
      }
    }

    @NotNull
    private static LauncherIconGenerator.PreviewShape getPreviewShapeFromId(@NotNull String previewShapeId) {
      for (LauncherIconGenerator.PreviewShape shape : LauncherIconGenerator.PreviewShape.values()) {
        if (Objects.equals(shape.id, previewShapeId)) {
          return shape;
        }
      }
      return LauncherIconGenerator.PreviewShape.SQUARE;
    }
  }

  private static class LauncherLegacyIconsPreviewPanel extends PreviewIconsPanel {
    public LauncherLegacyIconsPreviewPanel() {
      super("", Theme.TRANSPARENT);
    }

    /**
     * Override the default implementation to filter out the "web" density image, and keep
     * only the images with a "regular" densities (mdpi, hdpi, etc.).
     */
    @Override
    public void showPreviewImages(@NotNull IconGeneratorResult iconGeneratorResult) {
      Collection<GeneratedIcon> generatedIcons = iconGeneratorResult.getIcons();
      List<Pair<String, BufferedImage>> list = generatedIcons.stream()
        .filter(icon -> icon instanceof GeneratedImageIcon)
        .map(icon -> (GeneratedImageIcon)icon)
        .filter(icon -> icon.getDensity() != Density.NODPI) // Skip Web image
        .sorted(Comparator.comparingInt(icon -> -icon.getDensity().getDpiValue()))
        .map(icon -> Pair.of(icon.getDensity().getResourceValue(), icon.getImage()))
        .collect(Collectors.toList());
      showPreviewImagesImpl(list);
    }
  }

  private static class ActionBarIconsPreviewPanel extends PreviewIconsPanel {
    public ActionBarIconsPreviewPanel() {
      super("", Theme.TRANSPARENT);
    }
  }

  private static class NotificationIconsPreviewPanel extends PreviewIconsPanel {
    public NotificationIconsPreviewPanel() {
      super("", Theme.DARK, CategoryIconMap.ACCEPT_ALL);
    }
  }
}