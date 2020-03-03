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

import static com.android.tools.adtui.validation.ValidatorPanel.truncateMessage;

import com.android.resources.ResourceFolderType;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.validation.validators.PositiveIntegerValidator;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.npw.assetstudio.VectorIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.ui.VectorAssetBrowser;
import com.android.tools.idea.npw.assetstudio.ui.VectorIconButton;
import com.android.tools.idea.npw.project.AndroidPackageUtils;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.adapters.StringToIntAdapterProperty;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.IntValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.observable.expressions.string.FormatExpression;
import com.android.tools.idea.observable.ui.ColorProperty;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.SliderValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.ui.VectorImageComponent;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

/**
 * A wizard step for generating Android vector drawable icons.
 */
@SuppressWarnings("UseJBColor") // Colors are used for the graphics generator, not the plugin UI.
public final class NewVectorAssetStep extends ModelWizardStep<GenerateIconsModel> implements PersistentStateComponent<PersistentState> {
  private static final int DEFAULT_MATERIAL_ICON_SIZE = 24;
  private static final String ICON_PREFIX = "ic_";
  private static final String DEFAULT_OUTPUT_NAME = "ic_vector_name";
  // Start with the Clip Art radio button selected, because the clip art icons are easy to browse
  // and play around with right away.
  private static final AssetSourceType DEFAULT_ASSET_SOURCE_TYPE = AssetSourceType.CLIP_ART;
  @SuppressWarnings("UseJBColor") // Intentionally not using JBColor for Android icons.
  private static final Color DEFAULT_COLOR = Color.BLACK;

  private static final String VECTOR_ASSET_STEP_PROPERTY = "vectorAssetStep";
  private static final String OUTPUT_NAME_PROPERTY = "outputName";
  private static final String ASSET_SOURCE_TYPE_PROPERTY = "assetSourceType";
  private static final String CLIPART_ASSET_PROPERTY = "clipartAsset";
  private static final String SOURCE_FILE_PROPERTY = "sourceFile";
  private static final String OVERRIDE_SIZE_PROPERTY = "overrideSize";
  private static final String WIDTH_PROPERTY = "width";
  private static final String HEIGHT_PROPERTY = "height";
  private static final String COLOR_PROPERTY = "color";
  private static final String OPACITY_PERCENT_PROPERTY = "opacityPercent";
  private static final String AUTO_MIRRORED_PROPERTY = "autoMirrored";

  private final ObjectProperty<AssetSourceType> myAssetSourceType;
  private final ObjectProperty<VectorAsset> myActiveAsset;
  private final OptionalProperty<Dimension> myOriginalSize = new OptionalValueProperty<>();
  private final StringProperty myOutputName;
  private final BoolProperty myOverrideSize;
  private final IntProperty myWidth = new IntValueProperty();
  private final IntProperty myHeight = new IntValueProperty();
  private final ObjectProperty<Color> myColor;
  private final IntProperty myOpacityPercent;
  private final BoolProperty myAutoMirrored;

  private final ObjectValueProperty<Validator.Result> myAssetValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  private final IdeResourceNameValidator myNameValidator = IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE);

  private final BindingsManager myGeneralBindings = new BindingsManager();
  private final BindingsManager myActiveAssetBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  @NotNull private final VectorIconGenerator myIconGenerator;
  @NotNull private final AndroidFacet myFacet;

  private final ValidatorPanel myValidatorPanel;

  private JPanel myPanel;
  private JPanel myImagePreviewPanel;
  private VectorImageComponent myImagePreview;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myResourceNamePanel;
  private JTextField myOutputNameTextField;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel mySourceAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel mySourceAssetRadioButtons;
  private JRadioButton myClipartRadioButton;
  private JRadioButton myLocalFileRadioButton;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myIconPickerPanel;
  private VectorIconButton myClipartAssetButton;
  private JPanel myFileBrowserPanel;
  private VectorAssetBrowser myFileBrowser;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myResizePanel;
  private JTextField myWidthTextField;
  private JTextField myHeightTextField;
  private JCheckBox myOverrideSizeCheckBox;
  private JPanel myColorRowPanel;
  private ColorPanel myColorPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOpacityPanel;
  private JSlider myOpacitySlider;
  private JBLabel myOpacityValueLabel;
  private JCheckBox myEnableAutoMirroredCheckBox;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myPreviewPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myLeftPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myRightPanel;
  private JBScrollPane myScrollPane;

  public NewVectorAssetStep(@NotNull GenerateIconsModel model, @NotNull AndroidFacet facet) {
    super(model, "Configure Vector Asset");
    myFacet = facet;

    int minSdkVersion = AndroidModuleInfo.getInstance(myFacet).getMinSdkVersion().getApiLevel();
    myIconGenerator = new VectorIconGenerator(myFacet.getModule().getProject(), minSdkVersion);
    Disposer.register(this, myIconGenerator);

    myImagePreviewPanel.setBorder(JBUI.Borders.customLine(JBColor.border()));

    myAssetSourceType = new SelectedRadioButtonProperty<>(DEFAULT_ASSET_SOURCE_TYPE, AssetSourceType.values(),
                                                          myClipartRadioButton, myLocalFileRadioButton);
    myActiveAsset = new ObjectValueProperty<>(myClipartAssetButton.getAsset());
    myOutputName = new TextProperty(myOutputNameTextField);
    myOverrideSize = new SelectedProperty(myOverrideSizeCheckBox);
    myColor = ObjectProperty.wrap(new ColorProperty(myColorPanel));
    myOpacityPercent = new SliderValueProperty(myOpacitySlider);
    myAutoMirrored = new SelectedProperty(myEnableAutoMirroredCheckBox);

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    ActionListener assetListener = actionEvent -> renderPreviews();
    myClipartAssetButton.addAssetListener(assetListener);
    myFileBrowser.addAssetListener(assetListener);

    myListeners.receiveAndFire(myAssetSourceType, sourceType -> {
      myIconPickerPanel.setVisible(sourceType == AssetSourceType.CLIP_ART);
      myColorRowPanel.setVisible(sourceType == AssetSourceType.CLIP_ART);
      myFileBrowserPanel.setVisible(sourceType == AssetSourceType.FILE);
      myActiveAsset.set(sourceType == AssetSourceType.CLIP_ART ? myClipartAssetButton.getAsset() : myFileBrowser.getAsset());
    });

    myGeneralBindings.bindTwoWay(new StringToIntAdapterProperty(new TextProperty(myWidthTextField)), myWidth);
    myGeneralBindings.bindTwoWay(new StringToIntAdapterProperty(new TextProperty(myHeightTextField)), myHeight);
    myGeneralBindings.bind(new EnabledProperty(myWidthTextField), myOverrideSize);
    myGeneralBindings.bind(new EnabledProperty(myHeightTextField), myOverrideSize);
    myListeners.listenAll(myOverrideSize, myOriginalSize).withAndFire(() -> {
      if (!myOverrideSize.get() || !myOriginalSize.get().isPresent()) {
        myWidth.set(DEFAULT_MATERIAL_ICON_SIZE);
        myHeight.set(DEFAULT_MATERIAL_ICON_SIZE);
      }
      else {
        myWidth.set(myOriginalSize.getValue().width);
        myHeight.set(myOriginalSize.getValue().height);
      }
    });

    myGeneralBindings.bind(new TextProperty(myOpacityValueLabel), new FormatExpression("%d %%", myOpacityPercent));

    myListeners.listenAll(myActiveAsset, myOverrideSize, myWidth, myHeight, myColor, myOpacityPercent, myAutoMirrored)
        .with(this::renderPreviews);

    myListeners.listenAndFire(myActiveAsset, sender -> {
      myActiveAssetBindings.releaseAll();

      myActiveAssetBindings.bind(myOutputName, new Expression<String>(myActiveAsset.get().path()) {
        @Override
        @NotNull
        public String get() {
          File file = myActiveAsset.get().path().get();
          if (!file.exists() || file.isDirectory()) {
            return DEFAULT_OUTPUT_NAME;
          }

          String name = FileUtil.getNameWithoutExtension(file).toLowerCase(Locale.getDefault());
          if (!name.startsWith(ICON_PREFIX)) {
            name = ICON_PREFIX + AndroidResourceUtil.getValidResourceFileName(name);
          }
          return AndroidResourceUtil.getValidResourceFileName(name);
        }
      });

      myValidatorPanel.registerValidator(myOutputName, name -> Validator.Result.fromNullableMessage(myNameValidator.getErrorText(name)));
      myValidatorPanel.registerValidator(myWidth, new PositiveIntegerValidator("Width should be a positive value"));
      myValidatorPanel.registerValidator(myHeight, new PositiveIntegerValidator("Height should be a positive value"));
      myValidatorPanel.registerValidator(myAssetValidityState, validity -> truncateMessage(validity, 3));

      if (myAssetSourceType.get() == AssetSourceType.CLIP_ART) {
        myActiveAssetBindings.bind(ObjectProperty.wrap(myActiveAsset.get().color()), myColor);
      }
      myActiveAssetBindings.bind(myActiveAsset.get().opacityPercent(), myOpacityPercent);
      myActiveAssetBindings.bind(myActiveAsset.get().autoMirrored(), myAutoMirrored);
      myActiveAssetBindings.bind(myActiveAsset.get().outputWidth(), myWidth);
      myActiveAssetBindings.bind(myActiveAsset.get().outputHeight(), myHeight);
    });

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myActiveAsset));
    myGeneralBindings.bind(myIconGenerator.outputName(), myOutputName);

    PersistentStateUtil.load(this, getModel().getPersistentState().getChild(VECTOR_ASSET_STEP_PROPERTY));

    // Refresh the asset preview.
    renderPreviews();
  }

  @Override
  @NotNull
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Collections.singletonList(new ConfirmGenerateIconsStep(getModel(), AndroidPackageUtils.getModuleTemplates(myFacet, null)));
  }

  @Override
  @NotNull
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Override
  @NotNull
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setIconGenerator(myIconGenerator);
  }

  @Override
  public void onWizardFinished() {
    getModel().getPersistentState().setChild(VECTOR_ASSET_STEP_PROPERTY, getState());
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.set(OUTPUT_NAME_PROPERTY, myOutputName.get(), DEFAULT_OUTPUT_NAME);
    state.set(ASSET_SOURCE_TYPE_PROPERTY, myAssetSourceType.get(), DEFAULT_ASSET_SOURCE_TYPE);
    state.setChild(CLIPART_ASSET_PROPERTY, myClipartAssetButton.getState());
    File file = myFileBrowser.getAsset().path().get();
    state.set(SOURCE_FILE_PROPERTY, file.getPath(), getProjectPath());
    state.set(OVERRIDE_SIZE_PROPERTY, myOverrideSize.get(), false);
    state.set(WIDTH_PROPERTY, myWidth.get(), DEFAULT_MATERIAL_ICON_SIZE);
    state.set(HEIGHT_PROPERTY, myHeight.get(), DEFAULT_MATERIAL_ICON_SIZE);
    state.set(COLOR_PROPERTY, myColor.get(), DEFAULT_COLOR);
    state.set(OPACITY_PERCENT_PROPERTY, myOpacityPercent.get(), 100);
    state.set(AUTO_MIRRORED_PROPERTY, myAutoMirrored.get(), false);
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    // Load persistent state of controls after dust settles.
    ApplicationManager.getApplication().invokeLater(
      () -> {
        myOutputName.set(state.get(OUTPUT_NAME_PROPERTY, DEFAULT_OUTPUT_NAME));
        myAssetSourceType.set(state.get(ASSET_SOURCE_TYPE_PROPERTY, DEFAULT_ASSET_SOURCE_TYPE));
        PersistentStateUtil.load(myClipartAssetButton, state.getChild(CLIPART_ASSET_PROPERTY));
        String path = state.get(SOURCE_FILE_PROPERTY, getProjectPath());
        myFileBrowser.getAsset().path().set(new File(path));
        myOverrideSize.set(state.get(OVERRIDE_SIZE_PROPERTY, false));
        myWidth.set(state.get(WIDTH_PROPERTY, DEFAULT_MATERIAL_ICON_SIZE));
        myHeight.set(state.get(HEIGHT_PROPERTY, DEFAULT_MATERIAL_ICON_SIZE));
        myColor.set(state.get(COLOR_PROPERTY, DEFAULT_COLOR));
        myOpacityPercent.set(state.get(OPACITY_PERCENT_PROPERTY, 100));
        myAutoMirrored.set(state.get(AUTO_MIRRORED_PROPERTY, false));
      },
      ModalityState.any());
  }

  @SystemIndependent
  @NotNull
  private String getProjectPath() {
    String projectPath = myFacet.getModule().getProject().getBasePath();
    assert projectPath != null;
    return projectPath;
  }

  private void renderPreviews() {
    // This method is often called as the result of a UI property changing which may also cause
    // some other properties to change. Due to asynchronous nature of some property changes, it
    // is necessary to use two invokeLater calls to make sure that everything settles before
    // icons generation is attempted.
    VectorPreviewUpdater previewUpdater = new VectorPreviewUpdater();
    invokeVeryLate(previewUpdater::enqueueUpdate, ModalityState.any(), o -> Disposer.isDisposed(this));
  }

  /**
   * Executes the given runnable after a double 'invokeLater' delay.
   */
  private static void invokeVeryLate(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired) {
    Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> application.invokeLater(runnable, state, expired), state, expired);
  }

  /**
   * Parsing and generating a vector preview is not always a lightweight operation, and if we try to
   * do it synchronously, especially with a larger vector file, it can stutter the UI. So instead, we
   * enqueue the request to run on a background thread. If several requests are made in a row while
   * an existing worker is still in progress, they will only generate a single update, run as soon
   * as the current update finishes.
   * <p>
   * Call {@link #enqueueUpdate()} in order to kick-start the generation of a new preview.
   */
  private final class VectorPreviewUpdater {
    @Nullable private SwingWorker<Void, Void> myCurrentWorker;
    @Nullable private SwingWorker<Void, Void> myEnqueuedWorker;

    /**
     * Starts parsing the current file in {@link #myActiveAsset} and, if it's valid, updates the UI
     * (particularly, the image preview and errors area). If an update is already in process, then
     * this will enqueue another request to run as soon as the current one is over.
     * <p>
     * The width of {@link #myImagePreview} is used when calculating a preview image, so be sure
     * the layout manager has finished laying out your UI before calling this method.
     * <p>
     * This method must be called on the dispatch thread.
     */
    public void enqueueUpdate() {
      ApplicationManager.getApplication().assertIsDispatchThread();

      if (myCurrentWorker == null) {
        myCurrentWorker = createWorker();
        myCurrentWorker.execute();
      }
      else if (myEnqueuedWorker == null) {
        myEnqueuedWorker = createWorker();
      }
    }

    private SwingWorker<Void, Void> createWorker() {
      return new SwingWorker<Void, Void>() {
        VectorAsset.ParseResult myParseResult;

        @Override
        protected Void doInBackground() {
          try {
            myParseResult = myActiveAsset.get().parse(myImagePreview.getWidth(), true);
          } catch (Throwable t) {
            Logger.getInstance(getClass()).error(t);
            myParseResult = new VectorAsset.ParseResult("Internal error parsing " + myActiveAsset.get().path().get().getName());
          }
          return null;
        }

        @Override
        protected void done() {
          assert myParseResult != null;
          myAssetValidityState.set(myParseResult.getValidityState());
          if (myParseResult.isValid()) {
            BufferedImage image = myParseResult.getImage();
            myImagePreview.setIcon(image == null ? null : new ImageIcon(image));
            myOriginalSize.setValue(
                new Dimension(Math.round(myParseResult.getOriginalWidth()), Math.round(myParseResult.getOriginalHeight())));
          }
          else {
            myImagePreview.setIcon(null);
            myOriginalSize.clear();
          }

          myCurrentWorker = null;
          if (myEnqueuedWorker != null) {
            myCurrentWorker = myEnqueuedWorker;
            myEnqueuedWorker = null;
            ApplicationManager.getApplication().invokeLater(() -> myCurrentWorker.execute(), ModalityState.any());
          }
        }
      };
    }
  }

  private enum AssetSourceType {
    CLIP_ART,
    FILE,
  }
}