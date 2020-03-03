/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import static com.android.tools.idea.flags.StudioFlags.NELE_USE_ANDROIDX_DEFAULT;
import static com.android.tools.idea.npw.model.NewProjectModel.toPackagePart;
import static com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor;
import static java.lang.String.format;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.NewProjectModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.template.components.LanguageComboProvider;
import com.android.tools.idea.npw.ui.ActivityGallery;
import com.android.tools.idea.npw.validator.ProjectNameValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.ImageUtil;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * First page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends ModelWizardStep<NewProjectModuleModel> {
  private final NewProjectModel myProjectModel;

  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final List<UpdatablePackage> myInstallRequests = new ArrayList<>();
  private final List<RemotePackage> myInstallLicenseRequests = new ArrayList<>();

  private JPanel myPanel;
  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JTextField myPackageName;
  private JComboBox<Language> myProjectLanguage;
  private JCheckBox myInstantAppCheck;
  private JCheckBox myWearCheck;
  private JCheckBox myTvCheck;
  private JCheckBox myOfflineRepoCheck;
  private JLabel myTemplateIconTitle;
  private JLabel myTemplateIconDetail;
  private JPanel myFormFactorSdkControlsPanel;
  private JCheckBox myUseAndroidxCheck;
  private FormFactorSdkControls myFormFactorSdkControls;


  public ConfigureAndroidProjectStep(@NotNull NewProjectModuleModel newProjectModuleModel, @NotNull NewProjectModel projectModel) {
    super(newProjectModuleModel, message("android.wizard.project.new.configure"));

    myProjectModel = projectModel;
    myValidatorPanel = new ValidatorPanel(this, wrappedWithVScroll(myPanel));
    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    LicenseAgreementStep licenseAgreementStep =
      new LicenseAgreementStep(new LicenseAgreementModel(AndroidVersionsInfo.getSdkManagerLocalPath()), myInstallLicenseRequests);

    InstallSelectedPackagesStep installPackagesStep =
      new InstallSelectedPackagesStep(myInstallRequests, new HashSet<>(), AndroidSdks.getInstance().tryToChooseSdkHandler(), false);

    return Lists.newArrayList(licenseAgreementStep, installPackagesStep);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myBindings.bindTwoWay(new TextProperty(myAppName), myProjectModel.applicationName());

    // TODO: http://b/76205038 - The new UI no longer ask the user for the company domain. We should stop using it, and save the package
    // instead. Keep in mind that we need to remove the last segment, that is specific to the application name.
    StringProperty companyDomain = new StringValueProperty(NewProjectModel.getInitialDomain(false));
    String basePackage = new DomainToPackageExpression(companyDomain, new StringValueProperty("")).get();

    Expression<String> computedPackageName = myProjectModel.applicationName()
                                                           .transform(appName -> format("%s.%s", basePackage, toPackagePart(appName)));
    TextProperty packageNameText = new TextProperty(myPackageName);
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(myProjectModel.packageName(), packageNameText);
    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    Expression<String> computedLocation = myProjectModel.applicationName().transform(ConfigureAndroidProjectStep::findProjectLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getTextField());
    BoolProperty isLocationSynced = new BoolValueProperty(true);
    myBindings.bind(locationText, computedLocation, isLocationSynced);
    myBindings.bind(myProjectModel.projectLocation(), locationText);
    myListeners.receive(locationText, value -> isLocationSynced.set(value.equals(computedLocation.get())));

    OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo = getModel().androidSdkInfo();
    myFormFactorSdkControls.init(androidSdkInfo, this);

    if (StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.get()) {
      myBindings.bindTwoWay(getModel().dynamicInstantApp(), new SelectedProperty(myInstantAppCheck));
    }
    else {
      myBindings.bindTwoWay(getModel().instantApp(), new SelectedProperty(myInstantAppCheck));
    }
    myBindings.bindTwoWay(myProjectModel.useAndroidx(), new SelectedProperty(myUseAndroidxCheck));


    myValidatorPanel.registerValidator(myProjectModel.applicationName(), new ProjectNameValidator());

    Expression<File> locationFile = myProjectModel.projectLocation().transform(File::new);
    myValidatorPanel.registerValidator(locationFile, PathValidator.createDefault("project location"));

    myValidatorPanel.registerValidator(myProjectModel.packageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));

    myValidatorPanel.registerValidator(androidSdkInfo, value ->
      value.isPresent() ? Validator.Result.OK : new Validator.Result(Validator.Severity.ERROR, message("select.target.dialog.text")));

    myProjectLocation.addBrowseFolderListener(null, null, null, createSingleFolderDescriptor());

    myProjectLanguage.setSelectedItem(myProjectModel.enableKotlinSupport().get() ? Language.KOTLIN : Language.JAVA);

    myListeners.listenAll(getModel().formFactor(), myProjectModel.enableCppSupport()).withAndFire(() -> {
      FormFactor formFactor = getModel().formFactor().get();
      boolean isCppTemplate = myProjectModel.enableCppSupport().get();

      myInstantAppCheck.setVisible(formFactor == FormFactor.MOBILE && !isCppTemplate);
      myFormFactorSdkControls.showStatsPanel(formFactor == FormFactor.MOBILE);
      myWearCheck.setVisible(formFactor == FormFactor.WEAR);
      myTvCheck.setVisible(formFactor == FormFactor.TV);
      myOfflineRepoCheck.setVisible(StudioFlags.NPW_OFFLINE_REPO_CHECKBOX.get());
      myUseAndroidxCheck.setVisible(NELE_USE_ANDROIDX_DEFAULT.get() && myProjectModel.isAndroidxAvailable());
    });
  }

  @Override
  protected void onEntering() {
    FormFactor formFactor = getModel().formFactor().get();
    TemplateHandle templateHandle = getModel().renderTemplateHandle().getValueOrNull();
    int minSdk = templateHandle == null ? formFactor.getMinOfflineApiLevel() : templateHandle.getMetadata().getMinSdk();

    myFormFactorSdkControls.startDataLoading(formFactor, minSdk);
    setTemplateThumbnail(templateHandle);
  }

  @Override
  protected void onProceeding() {
    myProjectModel.enableKotlinSupport().set(myProjectLanguage.getSelectedItem() == Language.KOTLIN);
    getModel().hasCompanionApp().set(
      (myWearCheck.isVisible() && myWearCheck.isSelected()) ||
      (myTvCheck.isVisible() && myTvCheck.isSelected()) ||
      getModel().formFactor().get() == FormFactor.CAR // Auto is not a standalone module (but rather a modification to a mobile module)
    );

    myProjectModel.useOfflineRepo().set(myOfflineRepoCheck.isVisible() && myOfflineRepoCheck.isSelected());

    myInstallRequests.clear();
    myInstallLicenseRequests.clear();

    myInstallRequests.addAll(myFormFactorSdkControls.getSdkInstallPackageList());
    myInstallLicenseRequests.addAll(myInstallRequests.stream().map(UpdatablePackage::getRemote).collect(Collectors.toList()));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppName;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @NotNull
  private static String findProjectLocation(@NotNull String applicationName) {
    applicationName = NewProjectModel.sanitizeApplicationName(applicationName);
    File baseDirectory = WizardUtils.getProjectLocationParent();
    File projectDirectory = new File(baseDirectory, applicationName);

    // Try appName, appName2, appName3, ...
    int counter = 2;
    while (projectDirectory.exists()) {
      projectDirectory = new File(baseDirectory, format("%s%d", applicationName, counter++));
    }

    return projectDirectory.getPath();
  }

  private void setTemplateThumbnail(@Nullable TemplateHandle templateHandle) {
    boolean isCppTemplate = myProjectModel.enableCppSupport().get();
    Image image = ActivityGallery.getTemplateImage(templateHandle, isCppTemplate);
    if (image != null) {
      // Template Icons have an invisible pixel border that stops them from aligning top and right, as specified by the design
      BufferedImage buffImg = ImageUtil.toBufferedImage(image);
      Rectangle imageExtents = ImageUtils.getCropBounds(buffImg, (img, x, y) -> (img.getRGB(x, y) & 0xFF000000) == 0, null);

      if (imageExtents != null) {
        // Crop away empty space to left and right of the image.
        buffImg = buffImg.getSubimage(imageExtents.x, 0, imageExtents.width, buffImg.getHeight());
      }
      Icon icon = new ImageIcon(buffImg.getScaledInstance((256 * buffImg.getWidth()) / buffImg.getHeight(), 256, Image.SCALE_SMOOTH));

      myTemplateIconTitle.setIcon(icon);
      myTemplateIconTitle.setText(ActivityGallery.getTemplateImageLabel(templateHandle, isCppTemplate));
      myTemplateIconDetail.setText("<html>" + ActivityGallery.getTemplateDescription(templateHandle, isCppTemplate) + "</html>");
    }
    myTemplateIconTitle.setVisible(image != null);
    myTemplateIconDetail.setVisible(image != null);
  }

  private void createUIComponents() {
    myProjectLanguage = new LanguageComboProvider().createComponent();
    myFormFactorSdkControls = new FormFactorSdkControls();
    myFormFactorSdkControlsPanel = myFormFactorSdkControls.getRoot();
  }
}
