/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npwOld.assetstudio.ui;

//import static com.android.tools.adtui.validation.ValidatorPanel.truncateMessage;
import static com.android.tools.idea.npwOld.assetstudio.AssetStudioUtils.toUpperCamelCase;

import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.npwOld.assetstudio.DrawableRenderer;
import com.android.tools.idea.npwOld.assetstudio.IconGenerator.Shape;
import com.android.tools.idea.npwOld.assetstudio.LauncherIconGenerator;
import com.android.tools.idea.npwOld.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npwOld.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npwOld.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npwOld.assetstudio.wizard.PersistentState;
import com.android.tools.idea.npwOld.assetstudio.wizard.PersistentStateUtil;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
//import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.observable.expressions.string.FormatExpression;
import com.android.tools.idea.observable.ui.ColorProperty;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.SliderValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A panel which allows the configuration of an icon, by specifying the source asset used to
 * generate the icon plus some other options. Note that this panel provides a superset of all
 * options used by each {@link AndroidIconType}, but the relevant options are shown / hidden based
 * on the exact type passed into the constructor.
 */
public class ConfigureLauncherIconPanel extends JPanel implements Disposable, ConfigureIconView, PersistentStateComponent<PersistentState> {
  private static final boolean HIDE_INAPPLICABLE_CONTROLS = false; // TODO Decide on hiding or disabling.

  private static final File DEFAULT_FOREGROUND_IMAGE = getTemplateImage("drawable-v24", "ic_launcher_foreground.xml");
  private static final File DEFAULT_BACKGROUND_IMAGE = getTemplateImage("drawable", "ic_launcher_background.xml");
  private static final ForegroundAssetType DEFAULT_FOREGROUND_ASSET_TYPE = ForegroundAssetType.IMAGE;
  private static final BackgroundAssetType DEFAULT_BACKGROUND_ASSET_TYPE = BackgroundAssetType.IMAGE;
  private static final Density DEFAULT_PREVIEW_DENSITY = Density.XHIGH;
  private static final String DEFAULT_OUTPUT_NAME = AndroidIconType.LAUNCHER.toOutputName("");
  private static final Shape DEFAULT_ICON_SHAPE = Shape.SQUARE;

  private static final String FOREGROUND_ASSET_TYPE_PROPERTY = "foregroundAssetType";
  private static final String BACKGROUND_ASSET_TYPE_PROPERTY = "backgroundAssetType";
  private static final String BACKGROUND_COLOR_PROPERTY = "backgroundColor";
  private static final String GENERATE_LEGACY_ICON_PROPERTY = "generateLegacyIcon";
  private static final String GENERATE_ROUND_ICON_PROPERTY = "generateRoundIcon";
  private static final String GENERATE_WEB_ICON_PROPERTY = "generateWebIcon";
  private static final String LEGACY_ICON_SHAPE_PROPERTY = "legacyIconShape";
  private static final String WEB_ICON_SHAPE_PROPERTY = "webIconShape";
  private static final String SHOW_GRID_PROPERTY = "showGrid";
  private static final String SHOW_SAFE_ZONE_PROPERTY = "showSafeZone";
  private static final String PREVIEW_DENSITY_PROPERTY = "previewDensity";
  private static final String OUTPUT_NAME_PROPERTY = "outputName";
  private static final String FOREGROUND_LAYER_NAME_PROPERTY = "foregroundLayerName";
  private static final String BACKGROUND_LAYER_NAME_PROPERTY = "backgroundLayerName";
  private static final String FOREGROUND_IMAGE_PROPERTY = "foregroundImage";
  private static final String BACKGROUND_IMAGE_PROPERTY = "backgroundImage";
  private static final String FOREGROUND_CLIPART_ASSET_PROPERTY = "foregroundClipartAsset";
  private static final String FOREGROUND_TEXT_ASSET_PROPERTY = "foregroundTextAsset";

  /**
   * This panel presents a list of radio buttons (clipart, image, text), and whichever one is
   * selected sets the active asset.
   */
  private JPanel myRootPanel;
  private JBLabel myOutputNameLabel;
  private JTextField myOutputNameTextField;

  private JPanel myForegroundAllOptionsPanel;
  private JRadioButton myForegroundClipartRadioButton;
  private JRadioButton myForegroundTextRadioButton;
  private JRadioButton myForegroundImageRadioButton;
  private JRadioButton myForegroundTrimYesRadioButton;
  private JRadioButton myForegroundTrimNoRadioButton;
  private JPanel myForegroundTrimOptionsPanel;
  private JSlider myForegroundResizeSlider;
  private JLabel myForegroundResizeValueLabel;
  private JPanel myForegroundAssetRadioButtonsPanel;
  private JPanel myForegroundResizeSliderPanel;
  private JTextField myForegroundLayerNameTextField;
  private JPanel myForegroundColorRowPanel;
  private ColorPanel myForegroundColorPanel;
  private JPanel mGenerateLegacyIconRadioButtonsPanel;
  private JRadioButton myGenerateLegacyIconYesRadioButton;
  private JBScrollPane myForegroundScrollPane;
  private JPanel myForegroundImageAssetRowPanel;
  private JPanel myForegroundClipartAssetRowPanel;
  private JPanel myForegroundTextAssetRowPanel;
  private ImageAssetBrowser myForegroundImageAssetBrowser;
  private ClipartIconButton myForegroundClipartAssetButton;
  private TextAssetEditor myForegroundTextAssetEditor;
  private JBLabel myForegroundLayerNameLabel;
  private JLabel myForegroundAssetTypeLabel;
  private JBLabel myForegroundImagePathLabel;
  private JBLabel myForegroundClipartLabel;
  private JBLabel myForegroundTextLabel;
  private JBLabel myForegroundTrimLabel;
  private JBLabel myForegroundResizeLabel;
  private JBLabel myForegroundColorLabel;
  private JBLabel myGenerateLegacyIconLabel;

  private JPanel myBackgroundAllOptionsPanel;
  private JRadioButton myBackgroundImageRadioButton;
  private JRadioButton myBackgroundColorRadioButton;
  private JRadioButton myBackgroundTrimYesRadioButton;
  private JPanel myBackgroundTrimOptionsPanel;
  private JSlider myBackgroundResizeSlider;
  private JLabel myBackgroundResizeValueLabel;
  private JPanel myBackgroundAssetRadioButtonsPanel;
  private JPanel myBackgroundResizeSliderPanel;
  private JTextField myBackgroundLayerNameTextField;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundTrimRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundResizeRowPanel;
  private JPanel myBackgroundColorRowPanel;
  private ColorPanel myBackgroundColorPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myGenerateRoundIconRowPanel;
  private JPanel myGenerateRoundIconRadioButtonsPanel;
  private JRadioButton myGenerateRoundIconYesRadioButton;
  private JBScrollPane myBackgroundScrollPane;
  private JComboBox<Shape> myLegacyIconShapeComboBox;
  private JPanel myBackgroundImageAssetRowPanel;
  private ImageAssetBrowser myBackgroundImageAssetBrowser;
  private JBLabel myBackgroundLayerNameLabel;
  private JLabel myBackgroundAssetTypeLabel;
  private JBLabel myBackgroundImagePathLabel;
  private JBLabel myBackgroundTrimLabel;
  private JBLabel myBackgroundResizeLabel;
  private JBLabel myGenerateRoundIconLabel;
  private JBLabel myBackgroundColorLabel;
  private JBLabel myLegacyIconShapeLabel;
  private JBScrollPane myOtherIconsScrollPane;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOtherIconsAllOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundAssetTypeSourcePanel;
  private JPanel myForegroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundAssetTypeSourcePanel;
  private JPanel myBackgroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myLegacyIconShapePanelRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOutputNamePanelRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myForegroundScalingTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator mySourceAssetTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundResizePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myBackgroundScalingTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myBackgroundSourceAssetTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myGenerateLegacyIconRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundTrimPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myLegacyIconShapePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myGenerateWebIconRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JBLabel myGenerateWebIconLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myGenerateWebIconRadioButtonsPanel;
  private JRadioButton myGenerateWebIconYesRadioButton;
  private JRadioButton myBackgroundTrimNoRadioButton;
  private JBLabel myWebIconShapeLabel;
  private JComboBox<Shape> myWebIconShapeComboBox;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myWebIconShapePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myWebIconShapePanelRow;

  // @formatter:off
  private final Map<Shape, String> myShapeNames = ImmutableMap.of(
      Shape.NONE, "None",
      Shape.CIRCLE, "Circle",
      Shape.SQUARE, "Square",
      Shape.VRECT, "Vertical",
      Shape.HRECT, "Horizontal");
  // @formatter:on

  @NotNull private final AndroidVersion myBuildSdkVersion;
  @NotNull private final LauncherIconGenerator myIconGenerator;
  @NotNull private final ValidatorPanel myValidatorPanel;

  @NotNull private final BindingsManager myGeneralBindings = new BindingsManager();
  @NotNull private final BindingsManager myForegroundActiveAssetBindings = new BindingsManager();
  @NotNull private final BindingsManager myBackgroundActiveAssetBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final List<ActionListener> myAssetListeners = new ArrayList<>(1);

  @NotNull private final ImmutableMap<ForegroundAssetType, AssetComponent> myForegroundAssetPanelMap;

  @NotNull private final StringProperty myOutputName;
  @NotNull private final StringProperty myForegroundLayerName;
  @NotNull private final StringProperty myBackgroundLayerName;
  @NotNull private final ObjectProperty<BaseAsset> myForegroundActiveAsset;
  @NotNull private final OptionalProperty<ImageAsset> myBackgroundImageAsset;
  @NotNull private final ObjectProperty<Validator.Result> myForegroundAssetValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  @NotNull private final ObjectProperty<Validator.Result> myBackgroundAssetValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  @NotNull private final AbstractProperty<ForegroundAssetType> myForegroundAssetType;
  @NotNull private final AbstractProperty<BackgroundAssetType> myBackgroundAssetType;
  @NotNull private final BoolProperty myShowGrid;
  @NotNull private final BoolProperty myShowSafeZone;
  @NotNull private final AbstractProperty<Density> myPreviewDensity;
  private AbstractProperty<Color> myForegroundColor;
  private AbstractProperty<Color> myBackgroundColor;
  private BoolProperty myForegroundTrimmed;
  private BoolProperty myBackgroundTrimmed;
  private IntProperty myForegroundResizePercent;
  private IntProperty myBackgroundResizePercent;
  private BoolProperty myGenerateLegacyIcon;
  private BoolProperty myGenerateRoundIcon;
  private BoolProperty myGenerateWebIcon;
  private AbstractProperty<Shape> myLegacyIconShape;
  private AbstractProperty<Shape> myWebIconShape;

  /**
   * Initializes a panel which can generate Android launcher icons. The supported types passed in
   * will be presented to the user in a combo box (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public ConfigureLauncherIconPanel(@NotNull Disposable disposableParent,
                                    @NotNull AndroidFacet facet,
                                    @NotNull BoolProperty showGrid,
                                    @NotNull BoolProperty showSafeZone,
                                    @NotNull AbstractProperty<Density> previewDensity,
                                    @NotNull ValidatorPanel validatorPanel,
                                    @Nullable DrawableRenderer renderer) {
    super(new BorderLayout());
    AndroidModuleInfo androidModuleInfo = AndroidModuleInfo.getInstance(facet);
    AndroidVersion buildSdkVersion = androidModuleInfo.getBuildSdkVersion();
    myBuildSdkVersion = buildSdkVersion != null ? buildSdkVersion : new AndroidVersion(26);

    myShowGrid = showGrid;
    myShowSafeZone = showSafeZone;
    myPreviewDensity = previewDensity;
    myIconGenerator =
        new LauncherIconGenerator(facet.getModule().getProject(), androidModuleInfo.getMinSdkVersion().getApiLevel(), renderer);
    myValidatorPanel = validatorPanel;

    DefaultComboBoxModel<Shape> legacyShapesModel = new DefaultComboBoxModel<>();
    for (Shape shape : myShapeNames.keySet()) {
      legacyShapesModel.addElement(shape);
    }
    myLegacyIconShapeComboBox.setRenderer(new ListCellRendererWrapper<Shape>() {
      @Override
      public void customize(JList list, Shape shape, int index, boolean selected, boolean hasFocus) {
        setText(myShapeNames.get(shape));
      }
    });
    myLegacyIconShapeComboBox.setModel(legacyShapesModel);
    myLegacyIconShapeComboBox.setSelectedItem(Shape.SQUARE);

    DefaultComboBoxModel<Shape> webShapesModel = new DefaultComboBoxModel<>();
    for (Shape shape : myShapeNames.keySet()) {
      webShapesModel.addElement(shape);
    }
    myWebIconShapeComboBox.setRenderer(new ListCellRendererWrapper<Shape>() {
      @Override
      public void customize(JList list, Shape shape, int index, boolean selected, boolean hasFocus) {
        setText(myShapeNames.get(shape));
      }
    });
    myWebIconShapeComboBox.setModel(webShapesModel);
    myWebIconShapeComboBox.setSelectedItem(Shape.SQUARE);

    myForegroundScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myForegroundScrollPane.setBorder(JBUI.Borders.empty());

    myBackgroundScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myBackgroundScrollPane.setBorder(JBUI.Borders.empty());

    myOtherIconsScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myOtherIconsScrollPane.setBorder(JBUI.Borders.empty());

    myOutputName = new TextProperty(myOutputNameTextField);
    myForegroundLayerName = new TextProperty(myForegroundLayerNameTextField);
    myBackgroundLayerName = new TextProperty(myBackgroundLayerNameTextField);

    myForegroundAssetPanelMap = ImmutableMap.of(
      ForegroundAssetType.IMAGE, myForegroundImageAssetBrowser,
      ForegroundAssetType.CLIP_ART, myForegroundClipartAssetButton,
      ForegroundAssetType.TEXT, myForegroundTextAssetEditor);

    myForegroundImageAssetBrowser.getAsset().imagePath().setValue(DEFAULT_FOREGROUND_IMAGE);
    myBackgroundImageAssetBrowser.getAsset().imagePath().setValue(DEFAULT_BACKGROUND_IMAGE);

    // Call "setLabelFor" in code instead of designer since designer is so inconsistent about
    // valid targets.
    myOutputNameLabel.setLabelFor(myOutputNameTextField);

    myForegroundLayerNameLabel.setLabelFor(myForegroundLayerNameTextField);
    myForegroundAssetTypeLabel.setLabelFor(myForegroundAssetRadioButtonsPanel);
    myForegroundImagePathLabel.setLabelFor(myForegroundImageAssetBrowser);
    myForegroundClipartLabel.setLabelFor(myForegroundClipartAssetButton);
    myForegroundTextLabel.setLabelFor(myForegroundTextAssetEditor);
    myForegroundTrimLabel.setLabelFor(myForegroundTrimOptionsPanel);
    myForegroundResizeLabel.setLabelFor(myForegroundResizeSliderPanel);
    myForegroundColorLabel.setLabelFor(myForegroundColorPanel);
    myGenerateLegacyIconLabel.setLabelFor(mGenerateLegacyIconRadioButtonsPanel);

    myBackgroundLayerNameLabel.setLabelFor(myBackgroundLayerNameTextField);
    myBackgroundAssetTypeLabel.setLabelFor(myBackgroundAssetRadioButtonsPanel);
    myBackgroundImagePathLabel.setLabelFor(myBackgroundImageAssetBrowser);
    myBackgroundTrimLabel.setLabelFor(myBackgroundTrimOptionsPanel);
    myBackgroundResizeLabel.setLabelFor(myBackgroundResizeSliderPanel);
    myBackgroundColorLabel.setLabelFor(myBackgroundColorPanel);
    myGenerateRoundIconLabel.setLabelFor(myGenerateRoundIconRadioButtonsPanel);
    myLegacyIconShapeLabel.setLabelFor(myLegacyIconShapeComboBox);
    myWebIconShapeLabel.setLabelFor(myWebIconShapeComboBox);

    ImageAsset clipartAsset = myForegroundClipartAssetButton.getAsset();

    myForegroundAssetType = new SelectedRadioButtonProperty<>(DEFAULT_FOREGROUND_ASSET_TYPE, ForegroundAssetType.values(),
                                                              myForegroundImageRadioButton, myForegroundClipartRadioButton,
                                                              myForegroundTextRadioButton);
    myForegroundActiveAsset = new ObjectValueProperty<>(myForegroundImageAssetBrowser.getAsset());
    myForegroundImageAssetBrowser.getAsset().setRole("a foreground image file");
    myForegroundColorPanel.setSelectedColor(LauncherIconGenerator.DEFAULT_FOREGROUND_COLOR);

    myBackgroundAssetType = new SelectedRadioButtonProperty<>(DEFAULT_BACKGROUND_ASSET_TYPE, BackgroundAssetType.values(),
                                                              myBackgroundImageRadioButton, myBackgroundColorRadioButton);
    myBackgroundImageAsset = new OptionalValueProperty<>(myBackgroundImageAssetBrowser.getAsset());
    myBackgroundImageAssetBrowser.getAsset().setRole("a background image file");
    myBackgroundColorPanel.setSelectedColor(myIconGenerator.backgroundColor().get());

    initializeListenersAndBindings();
    initializeValidators();

    Disposer.register(disposableParent, this);
    for (AssetComponent assetComponent : myForegroundAssetPanelMap.values()) {
      Disposer.register(this, assetComponent);
    }
    Disposer.register(this, myBackgroundImageAssetBrowser);
    Disposer.register(this, myIconGenerator);

    add(myRootPanel);
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.set(FOREGROUND_ASSET_TYPE_PROPERTY, myForegroundAssetType.get(), DEFAULT_FOREGROUND_ASSET_TYPE);
    state.set(BACKGROUND_ASSET_TYPE_PROPERTY, myBackgroundAssetType.get(), DEFAULT_BACKGROUND_ASSET_TYPE);
    for (Map.Entry<ForegroundAssetType, AssetComponent> entry : myForegroundAssetPanelMap.entrySet()) {
      state.setChild("foreground" + toUpperCamelCase(entry.getKey()), entry.getValue().getAsset().getState());
    }
    state.setChild("background" + toUpperCamelCase(BackgroundAssetType.IMAGE), myBackgroundImageAssetBrowser.getAsset().getState());
    // Notice that the foreground colors that are owned by the asset components have already been saved.
    state.set(BACKGROUND_COLOR_PROPERTY, myBackgroundColor.get(), LauncherIconGenerator.DEFAULT_BACKGROUND_COLOR);
    state.set(GENERATE_LEGACY_ICON_PROPERTY, myGenerateLegacyIcon.get(), true);
    state.set(GENERATE_ROUND_ICON_PROPERTY, myGenerateRoundIcon.get(), true);
    state.set(GENERATE_WEB_ICON_PROPERTY, myGenerateWebIcon.get(), true);
    state.set(LEGACY_ICON_SHAPE_PROPERTY, myLegacyIconShape.get(), DEFAULT_ICON_SHAPE);
    state.set(WEB_ICON_SHAPE_PROPERTY, myWebIconShape.get(), DEFAULT_ICON_SHAPE);
    state.set(SHOW_GRID_PROPERTY, myShowGrid.get(), false);
    state.set(SHOW_SAFE_ZONE_PROPERTY, myShowSafeZone.get(), true);
    state.set(PREVIEW_DENSITY_PROPERTY, myPreviewDensity.get(), DEFAULT_PREVIEW_DENSITY);
    state.set(OUTPUT_NAME_PROPERTY, myOutputName.get(), DEFAULT_OUTPUT_NAME);
    state.set(FOREGROUND_LAYER_NAME_PROPERTY, myForegroundLayerName.get(), defaultForegroundLayerName());
    state.set(BACKGROUND_LAYER_NAME_PROPERTY, myBackgroundLayerName.get(), defaultBackgroundLayerName());
    state.set(FOREGROUND_IMAGE_PROPERTY, myForegroundImageAssetBrowser.getAsset().imagePath().getValueOrNull(), DEFAULT_FOREGROUND_IMAGE);
    state.set(BACKGROUND_IMAGE_PROPERTY, myBackgroundImageAssetBrowser.getAsset().imagePath().getValueOrNull(), DEFAULT_BACKGROUND_IMAGE);
    state.setChild(FOREGROUND_CLIPART_ASSET_PROPERTY, myForegroundClipartAssetButton.getState());
    state.setChild(FOREGROUND_TEXT_ASSET_PROPERTY, myForegroundTextAssetEditor.getState());
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    myForegroundAssetType.set(state.get(FOREGROUND_ASSET_TYPE_PROPERTY, DEFAULT_FOREGROUND_ASSET_TYPE));
    myBackgroundAssetType.set(state.get(BACKGROUND_ASSET_TYPE_PROPERTY, DEFAULT_BACKGROUND_ASSET_TYPE));
    for (Map.Entry<ForegroundAssetType, AssetComponent> entry : myForegroundAssetPanelMap.entrySet()) {
      PersistentStateUtil.load(entry.getValue().getAsset(), state.getChild("foreground" + toUpperCamelCase(entry.getKey())));
    }
    PersistentStateUtil.load(myBackgroundImageAssetBrowser.getAsset(),
                             state.getChild("background" + toUpperCamelCase(BackgroundAssetType.IMAGE)));
    // Notice that the foreground colors that are owned by the asset components have already been loaded.
    myBackgroundColor.set(state.get(BACKGROUND_COLOR_PROPERTY, LauncherIconGenerator.DEFAULT_BACKGROUND_COLOR));
    myGenerateLegacyIcon.set(state.get(GENERATE_LEGACY_ICON_PROPERTY, true));
    myGenerateRoundIcon.set(state.get(GENERATE_ROUND_ICON_PROPERTY, true));
    myGenerateWebIcon.set(state.get(GENERATE_WEB_ICON_PROPERTY, true));
    myLegacyIconShape.set(state.get(LEGACY_ICON_SHAPE_PROPERTY, DEFAULT_ICON_SHAPE));
    myWebIconShape.set(state.get(WEB_ICON_SHAPE_PROPERTY, DEFAULT_ICON_SHAPE));
    myShowGrid.set(state.get(SHOW_GRID_PROPERTY, false));
    myShowSafeZone.set(state.get(SHOW_SAFE_ZONE_PROPERTY, true));
    myPreviewDensity.set(state.get(PREVIEW_DENSITY_PROPERTY, DEFAULT_PREVIEW_DENSITY));
    myOutputName.set(state.get(OUTPUT_NAME_PROPERTY, DEFAULT_OUTPUT_NAME));
    myForegroundLayerName.set(state.get(FOREGROUND_LAYER_NAME_PROPERTY, defaultForegroundLayerName()));
    myBackgroundLayerName.set(state.get(BACKGROUND_LAYER_NAME_PROPERTY, defaultBackgroundLayerName()));
    myForegroundImageAssetBrowser.getAsset().imagePath().setValue(state.get(FOREGROUND_IMAGE_PROPERTY, DEFAULT_FOREGROUND_IMAGE));
    myBackgroundImageAssetBrowser.getAsset().imagePath().setValue(state.get(BACKGROUND_IMAGE_PROPERTY, DEFAULT_BACKGROUND_IMAGE));
    PersistentStateUtil.load(myForegroundClipartAssetButton, state.getChild(FOREGROUND_CLIPART_ASSET_PROPERTY));
    PersistentStateUtil.load(myForegroundTextAssetEditor, state.getChild(FOREGROUND_TEXT_ASSET_PROPERTY));
  }

  /**
   * Returns a file pointing to a resource inside template.
   */
  @NotNull
  private static File getTemplateImage(@NotNull String resourceDir, @NotNull String fileName) {
    String path = FileUtil.join(Template.CATEGORY_PROJECTS, "NewAndroidModule", "root", "res", resourceDir, fileName);
    return new File(TemplateManager.getTemplateRootFolder(), path);
  }

  private void initializeListenersAndBindings() {
    myForegroundTrimmed = new SelectedProperty(myForegroundTrimYesRadioButton);
    myBackgroundTrimmed = new SelectedProperty(myBackgroundTrimYesRadioButton);

    myForegroundResizePercent = new SliderValueProperty(myForegroundResizeSlider);
    StringProperty foregroundResizeValueString = new TextProperty(myForegroundResizeValueLabel);
    myGeneralBindings.bind(foregroundResizeValueString, new FormatExpression("%d %%", myForegroundResizePercent));

    myBackgroundResizePercent = new SliderValueProperty(myBackgroundResizeSlider);
    StringProperty backgroundResizeValueString = new TextProperty(myBackgroundResizeValueLabel);
    myGeneralBindings.bind(backgroundResizeValueString, new FormatExpression("%d %%", myBackgroundResizePercent));

    myForegroundColor = ObjectProperty.wrap(new ColorProperty(myForegroundColorPanel));
    myBackgroundColor = ObjectProperty.wrap(new ColorProperty(myBackgroundColorPanel));
    myGenerateLegacyIcon = new SelectedProperty(myGenerateLegacyIconYesRadioButton);
    myGenerateRoundIcon = new SelectedProperty(myGenerateRoundIconYesRadioButton);
    myGenerateWebIcon = new SelectedProperty(myGenerateWebIconYesRadioButton);

    myLegacyIconShape = ObjectProperty.wrap(new SelectedItemProperty<>(myLegacyIconShapeComboBox));
    myWebIconShape = ObjectProperty.wrap(new SelectedItemProperty<>(myWebIconShapeComboBox));

    updateBindingsAndUiForActiveIconType();

    // Update foreground layer asset type depending on asset type radio buttons.
    myForegroundAssetType.addListener(() -> {
      AssetComponent assetComponent = myForegroundAssetPanelMap.get(myForegroundAssetType.get());
      myForegroundActiveAsset.set(assetComponent.getAsset());
    });

    // Update background asset depending on asset type radio buttons.
    myBackgroundAssetType.addListener(() -> {
      if (myBackgroundAssetType.get() == BackgroundAssetType.IMAGE) {
        myBackgroundImageAsset.setValue(myBackgroundImageAssetBrowser.getAsset());
      } else {
        myBackgroundImageAsset.clear();
      }
    });

    // If any of our underlying asset panels change, we should pass that on to anyone listening to
    // us as well.
    ActionListener assetPanelListener = e -> fireAssetListeners();
    for (AssetComponent assetComponent : myForegroundAssetPanelMap.values()) {
      assetComponent.addAssetListener(assetPanelListener);
    }
    myBackgroundImageAssetBrowser.addAssetListener(assetPanelListener);

    Runnable onAssetModified = this::fireAssetListeners;
    myListeners
        .listenAll(myForegroundTrimmed, myForegroundResizePercent, myForegroundColor,
                   myBackgroundTrimmed, myBackgroundResizePercent, myBackgroundColor,
                   myGenerateLegacyIcon, myLegacyIconShape,
                   myGenerateRoundIcon,
                   myGenerateWebIcon, myWebIconShape)
        .with(onAssetModified);

    BoolValueProperty foregroundIsResizable = new BoolValueProperty();
    myListeners.listenAndFire(myForegroundActiveAsset, sender -> {
      myForegroundActiveAssetBindings.releaseAll();
      BaseAsset asset = myForegroundActiveAsset.get();
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundTrimmed, asset.trimmed());
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundResizePercent, asset.scalingPercent());
      OptionalValueProperty<Color> assetColor = asset.color();
      if (assetColor.getValueOrNull() == null) {
        assetColor.setValue(myForegroundColor.get());
      }
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundColor, ObjectProperty.wrap(assetColor));
      myForegroundActiveAssetBindings.bind(foregroundIsResizable, asset.isResizable());
      if (asset instanceof ImageAsset) {
        myForegroundActiveAssetBindings.bind(myForegroundAssetValidityState, ((ImageAsset)asset).getValidityState());
      }
      else {
        myForegroundAssetValidityState.set(Validator.Result.OK);
      }

      getIconGenerator().sourceAsset().setValue(asset);
      onAssetModified.run();
    });

    BoolValueProperty backgroundIsResizable = new BoolValueProperty();
    // When switching between Image/Color for background, bind corresponding properties and regenerate asset (to be sure).
    Runnable onBackgroundAssetModified = () -> {
      myBackgroundActiveAssetBindings.releaseAll();
      ImageAsset asset = myBackgroundImageAsset.getValueOrNull();
      if (asset != null) {
        myBackgroundActiveAssetBindings.bindTwoWay(myBackgroundTrimmed, asset.trimmed());
        myBackgroundActiveAssetBindings.bindTwoWay(myBackgroundResizePercent, asset.scalingPercent());
        myBackgroundActiveAssetBindings.bind(backgroundIsResizable, asset.isResizable());
        myBackgroundActiveAssetBindings.bind(myBackgroundAssetValidityState, asset.getValidityState());
      }
      else {
        backgroundIsResizable.set(false);
        myBackgroundAssetValidityState.set(Validator.Result.OK);
      }
      getIconGenerator().backgroundImageAsset().setNullableValue(asset);
      onAssetModified.run();
    };
    myListeners.listenAndFire(myBackgroundImageAsset, sender -> onBackgroundAssetModified.run());

    /*
     * Hook up a bunch of UI <- boolean expressions, so that when certain conditions are met,
     * various components show/hide. This also requires refreshing the panel explicitly, as
     * otherwise Swing doesn't realize it should trigger a relayout.
     */
    ImmutableMap.Builder<BoolProperty, ObservableBool> layoutPropertiesBuilder = ImmutableMap.builder();
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundImageAssetRowPanel), new SelectedProperty(myForegroundImageRadioButton));
    layoutPropertiesBuilder
        .put(new VisibleProperty(myForegroundClipartAssetRowPanel), new SelectedProperty(myForegroundClipartRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundTextAssetRowPanel), new SelectedProperty(myForegroundTextRadioButton));
    BooleanExpression isForegroundIsNotImage =
      (BooleanExpression)BooleanExpression.create(() -> myForegroundAssetType.get() != ForegroundAssetType.IMAGE, myForegroundAssetType);
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundColorRowPanel), isForegroundIsNotImage);

    if (HIDE_INAPPLICABLE_CONTROLS) {
      layoutPropertiesBuilder.put(new VisibleProperty(myForegroundScalingTitleSeparator), foregroundIsResizable);
      layoutPropertiesBuilder.put(new VisibleProperty(myForegroundImageOptionsPanel), foregroundIsResizable);
    }
    else {
      layoutPropertiesBuilder.put(new EnabledProperty(myForegroundTrimYesRadioButton), foregroundIsResizable);
      layoutPropertiesBuilder.put(new EnabledProperty(myForegroundTrimNoRadioButton), foregroundIsResizable);
      layoutPropertiesBuilder.put(new EnabledProperty(myForegroundResizeSlider), foregroundIsResizable);
    }

    // Show either the image or the color UI controls.
    ObservableBool backgroundIsImage = new SelectedProperty(myBackgroundImageRadioButton);
    ObservableBool backgroundIsColor = new SelectedProperty(myBackgroundColorRadioButton);
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundImageAssetRowPanel), backgroundIsImage);
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundColorRowPanel), backgroundIsColor);

    if (HIDE_INAPPLICABLE_CONTROLS) {
      layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundScalingTitleSeparator), backgroundIsResizable);
      layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundImageOptionsPanel), backgroundIsResizable);
    }
    else {
      layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundTrimYesRadioButton), backgroundIsResizable);
      layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundTrimNoRadioButton), backgroundIsResizable);
      layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundResizeSlider), backgroundIsResizable);
    }

    layoutPropertiesBuilder.put(new EnabledProperty(myLegacyIconShapeComboBox), new SelectedProperty(myGenerateLegacyIconYesRadioButton));
    layoutPropertiesBuilder.put(new EnabledProperty(myWebIconShapeComboBox), new SelectedProperty(myGenerateWebIconYesRadioButton));

    ImmutableMap<BoolProperty, ObservableBool> layoutProperties = layoutPropertiesBuilder.build();
    for (Map.Entry<BoolProperty, ObservableBool> entry : layoutProperties.entrySet()) {
      // Initialize everything off, as this makes sure the frame that uses this panel won't start
      // REALLY LARGE by default.
      entry.getKey().set(false);
      myGeneralBindings.bind(entry.getKey(), entry.getValue());
    }
    myListeners.listenAll(layoutProperties.keySet()).with(() -> {
      SwingUtilities.updateComponentTreeUI(myForegroundAllOptionsPanel);
      SwingUtilities.updateComponentTreeUI(myBackgroundAllOptionsPanel);
    });
  }

  private void initializeValidators() {
    // We use this property as a way to trigger the validation when the panel is shown/hidden
    // when the "output icon type" changes in our parent component.
    // For example, we only want to validate the API level (see below) if the user is trying
    // to create an adaptive icon (from this component).
    VisibleProperty isActive = new VisibleProperty(this);

    // Validate the API level when the panel is active.
    myValidatorPanel.registerTest(BooleanExpression.create(() -> !isActive.get() || myBuildSdkVersion.getFeatureLevel() >= 26, isActive),
                                  "Project must be built with SDK 26 or later to use adaptive icons");

    // Validate foreground and background layer names when the panel is active.
    myValidatorPanel.registerTest(nameIsNotEmptyExpression(isActive, myForegroundLayerName),
                                  "Foreground layer name must be set");
    myValidatorPanel.registerTest(nameIsNotEmptyExpression(isActive, myBackgroundLayerName),
                                  "Background layer name must be set");
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myOutputName, myForegroundLayerName),
                                  "Foreground layer must have a name distinct from the icon name");
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myOutputName, myBackgroundLayerName),
                                  "Background layer must have a name distinct from the icon name");
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myForegroundLayerName, myBackgroundLayerName),
                                  "Background and foreground layers must have distinct names");

    //myValidatorPanel.registerValidator(myForegroundAssetValidityState, validity -> truncateMessage(validity, 3));
    //myValidatorPanel.registerValidator(myBackgroundAssetValidityState, validity -> truncateMessage(validity, 3));
  }

  @NotNull
  private static BooleanExpression nameIsNotEmptyExpression(@NotNull VisibleProperty isActive, @NotNull StringProperty name) {
    return (BooleanExpression)BooleanExpression.create(() -> !isActive.get() || !StringUtil.isEmptyOrSpaces(name.get()),
                                    isActive, name);
  }

  @NotNull
  private static BooleanExpression namesAreDistinctExpression(@NotNull VisibleProperty isActive,
                                                              @NotNull StringProperty name1, @NotNull StringProperty name2) {
    return (BooleanExpression)BooleanExpression.create(() -> !isActive.get() || !StringUtil.equalsTrimWhitespaces(name1.get(), name2.get()),
                                    isActive, name1, name2);
  }

  /**
   * Returns an icon generator which will create Android icons using the panel's current settings.
   */
  @Override
  @NotNull
  public LauncherIconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  @Override
  @NotNull
  public JComponent getRootComponent() {
    return this;
  }

  /**
   * Adds a listener which will be triggered whenever the asset represented by this panel is
   * modified in any way.
   */
  @Override
  public void addAssetListener(@NotNull ActionListener listener) {
    myAssetListeners.add(listener);
  }

  @Override
  @NotNull
  public StringProperty outputName() {
    return myOutputName;
  }

  private void fireAssetListeners() {
    ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
    for (ActionListener assetListener : myAssetListeners) {
      assetListener.actionPerformed(e);
    }
  }

  private void updateBindingsAndUiForActiveIconType() {
    myOutputName.set(DEFAULT_OUTPUT_NAME);
    myForegroundLayerName.set(defaultForegroundLayerName());
    myBackgroundLayerName.set(defaultBackgroundLayerName());

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myForegroundActiveAsset));
    myGeneralBindings.bind(myIconGenerator.outputName(), myOutputName);
    myGeneralBindings.bindTwoWay(myIconGenerator.backgroundImageAsset(), myBackgroundImageAsset);

    myGeneralBindings.bindTwoWay(myBackgroundColor, myIconGenerator.backgroundColor());
    myGeneralBindings.bindTwoWay(myGenerateLegacyIcon, myIconGenerator.generateLegacyIcon());
    myGeneralBindings.bindTwoWay(myGenerateRoundIcon, myIconGenerator.generateRoundIcon());
    myGeneralBindings.bindTwoWay(myGenerateWebIcon, myIconGenerator.generateWebIcon());
    myGeneralBindings.bindTwoWay(myLegacyIconShape, myIconGenerator.legacyIconShape());
    myGeneralBindings.bindTwoWay(myWebIconShape, myIconGenerator.webIconShape());
    myGeneralBindings.bindTwoWay(myShowGrid, myIconGenerator.showGrid());
    myGeneralBindings.bindTwoWay(myShowSafeZone, myIconGenerator.showSafeZone());
    myGeneralBindings.bindTwoWay(myPreviewDensity, myIconGenerator.previewDensity());
    myGeneralBindings.bindTwoWay(myIconGenerator.foregroundLayerName(), myForegroundLayerName);
    myGeneralBindings.bindTwoWay(myIconGenerator.backgroundLayerName(), myBackgroundLayerName);
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myForegroundActiveAssetBindings.releaseAll();
    myBackgroundActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
    myAssetListeners.clear();
  }

  @NotNull
  private String defaultForegroundLayerName() {
    return myOutputName.get() + "_foreground";
  }

  @NotNull
  private String defaultBackgroundLayerName() {
    return myOutputName.get() + "_background";
  }

  private enum ForegroundAssetType {
    IMAGE,
    CLIP_ART,
    TEXT,
  }

  private enum BackgroundAssetType {
    IMAGE,
    COLOR,
  }
}
