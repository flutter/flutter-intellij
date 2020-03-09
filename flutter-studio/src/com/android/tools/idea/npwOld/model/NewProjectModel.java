/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npwOld.model;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_CPP_FLAGS;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_CPP_SUPPORT;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.repository.io.FileOpUtils;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.instantapp.InstantApps;
//import com.android.tools.idea.npwOld.project.AndroidGradleModuleUtils;
//import com.android.tools.idea.npwOld.project.AndroidPackageUtils;
//import com.android.tools.idea.npwOld.project.DomainToPackageExpression;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

//import static com.android.tools.idea.flags.StudioFlags.NELE_USE_ANDROIDX_DEFAULT;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_SUPPORT;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_OFFLINE_REPO_PATH;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_USE_OFFLINE_REPO;
//import org.apache.commons.io.FilenameUtils;

public class NewProjectModel extends WizardModel {
  private static final String PROPERTIES_DOMAIN_KEY = "SAVED_COMPANY_DOMAIN";
  private static final String PROPERTIES_ANDROID_PACKAGE_KEY = "SAVED_ANDROID_PACKAGE";
  private static final String PROPERTIES_CPP_SUPPORT_KEY = "SAVED_PROJECT_CPP_SUPPORT";
  private static final String PROPERTIES_KOTLIN_SUPPORT_KEY = "SAVED_PROJECT_KOTLIN_SUPPORT";
  private static final String EXAMPLE_DOMAIN = "example.com";
  private static final Pattern DISALLOWED_IN_DOMAIN = Pattern.compile("[^a-zA-Z0-9_]");

  private final StringProperty myApplicationName = new StringValueProperty(message("android.wizard.module.config.new.application"));
  private final StringProperty myCompanyDomain = new StringValueProperty(getInitialDomain(true));
  private final StringProperty myPackageName = new StringValueProperty();
  private final StringProperty myProjectLocation = new StringValueProperty();
  private final BoolProperty myEnableCppSupport = new BoolValueProperty();
  private final StringProperty myCppFlags = new StringValueProperty();
  private final OptionalProperty<Project> myProject = new OptionalValueProperty<>();
  private final Map<String, Object> myTemplateValues = Maps.newHashMap();
  private final Set<NewModuleModel> myNewModels = new HashSet<>();
  private final ProjectSyncInvoker myProjectSyncInvoker;
  private final MultiTemplateRenderer myMultiTemplateRenderer;
  private final BoolProperty myEnableKotlinSupport = new BoolValueProperty();
  private final BoolProperty myUseOfflineRepo = new BoolValueProperty();
  private final BoolProperty myUseAndroidx = new BoolValueProperty();

  private static Logger getLogger() {
    return Logger.getInstance(NewProjectModel.class);
  }

  public NewProjectModel() {
    this(new ProjectSyncInvoker.DefaultProjectSyncInvoker());
  }

  public NewProjectModel(@NotNull ProjectSyncInvoker projectSyncInvoker) {
    myProjectSyncInvoker = projectSyncInvoker;
    myMultiTemplateRenderer = new MultiTemplateRenderer(null, myProjectSyncInvoker);
    // Save entered company domain
    myCompanyDomain.addListener(() -> {
      String domain = myCompanyDomain.get();
      if (AndroidUtils.isValidAndroidPackageName(domain)) {
        PropertiesComponent.getInstance().setValue(PROPERTIES_DOMAIN_KEY, domain);
      }
    });

    // Save entered android package
    myPackageName.addListener(() -> {
      String androidPackage = myPackageName.get();
      int lastDotIdx = androidPackage.lastIndexOf('.');
      if (lastDotIdx >= 0) {
        androidPackage = androidPackage.substring(0, lastDotIdx);
      }
      if (AndroidUtils.isValidAndroidPackageName(androidPackage)) {
        PropertiesComponent.getInstance().setValue(PROPERTIES_ANDROID_PACKAGE_KEY, androidPackage);
      }
    });

    myApplicationName.addConstraint(String::trim);

    myEnableCppSupport.set(getInitialCppSupport());
    myEnableKotlinSupport.set(getInitialKotlinSupport());
    myUseAndroidx.set(getInitialUseAndroidxSupport());
  }

  @NotNull
  public ProjectSyncInvoker getProjectSyncInvoker() { return myProjectSyncInvoker; }

  public StringProperty packageName() {
    return myPackageName;
  }

  public StringProperty applicationName() {
    return myApplicationName;
  }

  public StringProperty companyDomain() {
    return myCompanyDomain;
  }

  public StringProperty projectLocation() {
    return myProjectLocation;
  }

  public BoolProperty enableCppSupport() {
    return myEnableCppSupport;
  }

  public StringProperty cppFlags() {
    return myCppFlags;
  }

  public BoolProperty enableKotlinSupport() {
    return myEnableKotlinSupport;
  }

  @NotNull
  public BoolProperty useOfflineRepo() {
    return myUseOfflineRepo;
  }

  /**
   * Returns whether AndroidX is available considering the installed API levels. If anything >= 28 is available, then
   * we make AndroidX available.
   */
  public boolean isAndroidxAvailable() {
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (sdkData == null) {
      return false;
    }

    for (IAndroidTarget target : sdkData.getTargets()) {
      if (target.getVersion().getApiLevel() >= 28) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public BoolProperty useAndroidx() {
    return myUseAndroidx;
  }

  public OptionalProperty<Project> project() {
    return myProject;
  }

  @NotNull
  public Map<String, Object> getTemplateValues() {
    return myTemplateValues;
  }

  /**
   * When the project is created, it contains the list of new Module that should also be created.
   */
  // TODO: is not yet clear what the project needs from the modules. At the moment gets the module hash table, but different modules may
  // have the same key/values... and some of these key values should actually be the same... for example, if one module needs a gradle plugin
  // version, shouldn't all the modules use the same version?
  public Set<NewModuleModel> getNewModuleModels() {
    return myNewModels;
  }

  public MultiTemplateRenderer getMultiTemplateRenderer() {
    return myMultiTemplateRenderer;
  }

  /**
   * Loads saved company domain, or generates a dummy one if no domain has been saved
   *
   * @param includeUserName This is used to implement legacy behaviour. When creating a new project the package name includes the user name
   *                        (if available), but when creating a new Module, the user name is not used.
   */
  @NotNull
  public static String getInitialDomain(boolean includeUserName) {
    String androidPackage = PropertiesComponent.getInstance().getValue(PROPERTIES_ANDROID_PACKAGE_KEY);
    if (androidPackage != null) {
      //return new DomainToPackageExpression(new StringValueProperty(androidPackage), new StringValueProperty("")).get();
    }

    String domain = PropertiesComponent.getInstance().getValue(PROPERTIES_DOMAIN_KEY);
    if (domain != null) {
      return domain;
    }

    // TODO: Figure out if this legacy behaviour, of including User Name, can be removed.
    String userName = includeUserName ? System.getProperty("user.name") : null;
    return userName == null ? EXAMPLE_DOMAIN : toPackagePart(userName) + '.' + EXAMPLE_DOMAIN;
  }

  /**
   * Tries to get a valid package suggestion for the specifies Project. For instant apps, the base feature module package is used, for
   * other modules, the saved user domain is used.
   */
  //@NotNull
  //public static String getSuggestedProjectPackage(@NotNull Project project, boolean isInstantApp) {
  //  String basePackage = null;
  //  if (isInstantApp) {
  //    Module baseFeatureModule = InstantApps.findBaseFeature(project);
  //    AndroidFacet androidFacet = baseFeatureModule == null ? null : AndroidFacet.getInstance(baseFeatureModule);
  //    if (androidFacet != null && androidFacet.getConfiguration().getModel() != null) {
  //      basePackage = AndroidPackageUtils.getPackageForApplication(androidFacet);
  //    }
  //  }
  //
  //  if (basePackage == null) {
  //    StringProperty companyDomain = new StringValueProperty(getInitialDomain(false));
  //    basePackage = new DomainToPackageExpression(companyDomain, new StringValueProperty("")).get();
  //  }
  //  return basePackage;
  //}

  /**
   * Loads saved value for Cpp support.
   */
  private static boolean getInitialCppSupport() {
    return PropertiesComponent.getInstance().isTrueValue(PROPERTIES_CPP_SUPPORT_KEY);
  }

  /**
   * Loads saved value for Kotlin support.
   */
  private static boolean getInitialKotlinSupport() {
    PropertiesComponent props = PropertiesComponent.getInstance();
    // If the value is not defined, we default to recommended (kotlin as nov-2018)
    return !props.isValueSet(PROPERTIES_KOTLIN_SUPPORT_KEY) || props.isTrueValue(PROPERTIES_KOTLIN_SUPPORT_KEY);
  }

  /**
   * Returns the initial value of the androidx support property. The value is true if we allow androidx to be the default
   * and if androidx is available.
   */
  @NotNull
  private Boolean getInitialUseAndroidxSupport() {
    return isAndroidxAvailable();
  }

  public void onWizardFinished(@NotNull ModelWizard.WizardResult wizardResult) {
    if (wizardResult == ModelWizard.WizardResult.FINISHED) {
      // Set the property value
      PropertiesComponent.getInstance().setValue(PROPERTIES_CPP_SUPPORT_KEY, myEnableCppSupport.get());
      PropertiesComponent.getInstance().setValue(PROPERTIES_KOTLIN_SUPPORT_KEY, myEnableKotlinSupport.get());
    }
  }

  @NotNull
  public static String toPackagePart(@NotNull String s) {
    s = s.replace('-', '_');
    String name = DISALLOWED_IN_DOMAIN.matcher(s).replaceAll("").toLowerCase(Locale.US);
    if (!name.isEmpty() && AndroidUtils.isReservedKeyword(name) != null) {
      name = StringUtil.fixVariableNameDerivedFromPropertyName(name).toLowerCase(Locale.US);
    }
    return name;
  }

  @NotNull
  public static String sanitizeApplicationName(@NotNull String s) {
    return DISALLOWED_IN_DOMAIN.matcher(s).replaceAll("");
  }

  @Override
  protected void handleFinished() {
    final String projectLocation = projectLocation().get();
    final String projectName = applicationName().get();

    boolean couldEnsureLocationExists = WriteCommandAction.runWriteCommandAction(null, (Computable<Boolean>)() -> {
      // We generally assume that the path has passed a fair amount of pre-validation checks
      // at the project configuration step before. Write permissions check can be tricky though in some cases,
      // e.g., consider an unmounted device in the middle of wizard execution or changed permissions.
      // Anyway, it seems better to check that we were really able to create the target location and are able to
      // write to it right here when the wizard is about to close, than running into some internal IDE errors
      // caused by these problems downstream
      // Note: this change was originally caused by http://b.android.com/219851, but then
      // during further discussions that a more important bug was in path validation in the old wizards,
      // where File.canWrite() always returned true as opposed to the correct Files.isWritable(), which is
      // already used in new wizard's PathValidator.
      // So the change below is therefore a more narrow case than initially supposed (however it still needs to be handled)
      try {
        if (VfsUtil.createDirectoryIfMissing(projectLocation) != null && FileOpUtils.create().canWrite(new File(projectLocation))) {
          return true;
        }
      }
      catch (Exception e) {
        getLogger().error(String.format("Exception thrown when creating target project location: %1$s", projectLocation), e);
      }
      return false;
    });
    if (couldEnsureLocationExists) {
      Project project = UIUtil.invokeAndWaitIfNeeded(() -> ProjectManager.getInstance().createProject(projectName, projectLocation));
      project().setValue(project);
      myMultiTemplateRenderer.setProject(project);
    }
    else {
      String msg =
        "Could not ensure the target project location exists and is accessible:\n\n%1$s\n\nPlease try to specify another path.";
      Messages.showErrorDialog(String.format(msg, projectLocation), "Error Creating Project");
    }

    myMultiTemplateRenderer.requestRender(new ProjectTemplateRenderer());
  }

  private class ProjectTemplateRenderer implements MultiTemplateRenderer.TemplateRenderer {

    @Override
    public boolean doDryRun() {
      if (project().getValueOrNull() == null) {
        return false;
      }

      performCreateProject(true);
      return true;
    }

    @Override
    public void render() {
      performCreateProject(false);

      try {
        File projectRoot = VfsUtilCore.virtualToIoFile(project().getValue().getBaseDir());
        //AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot);
      }
      catch (Exception e) {
        getLogger().warn("Failed to update Gradle wrapper permissions", e);
      }

      // Allow all other Wizard models to run handleFinished() (and the Wizard to close), before starting the (slow) import process.
      ApplicationManager.getApplication().invokeLater(this::performGradleImport);
    }

    private void performCreateProject(boolean dryRun) {
      Project project = project().getValue();

      // Cpp Apps attributes are needed to generate the Module and to generate the Render Template files (activity and layout)
      //myTemplateValues.put(ATTR_CPP_SUPPORT, myEnableCppSupport.get());
      //myTemplateValues.put(ATTR_CPP_FLAGS, myCppFlags.get());
      //myTemplateValues.put(ATTR_TOP_OUT, project.getBasePath());
      //myTemplateValues.put(ATTR_KOTLIN_SUPPORT, myEnableKotlinSupport.get());
      //
      //if (StudioFlags.NPW_OFFLINE_REPO_CHECKBOX.get()) {
      //  String offlineReposString = getOfflineReposString();
      //
      //  myTemplateValues.put(ATTR_OFFLINE_REPO_PATH, offlineReposString);
      //  if (myUseOfflineRepo.get()) {
      //    myTemplateValues.put(ATTR_USE_OFFLINE_REPO, true);
      //  }
      //}

      //myTemplateValues.put(ATTR_ANDROIDX_SUPPORT, myUseAndroidx.get());

      Map<String, Object> params = Maps.newHashMap(myTemplateValues);
      for (NewModuleModel newModuleModel : getNewModuleModels()) {
        params.putAll(newModuleModel.getTemplateValues());

        // Set global parameters
        Map<String, Object> renderTemplateValues = newModuleModel.getRenderTemplateValues().getValue();
        renderTemplateValues.putAll(myTemplateValues);
        newModuleModel.getTemplateValues().putAll(myTemplateValues);
      }

      Template projectTemplate = Template.createFromName(Template.CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME);
      // @formatter:off
      final RenderingContext context = RenderingContext.Builder.newContext(projectTemplate, project)
        .withCommandName("New Project")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withParams(params)
        .build();
      // @formatter:on

      projectTemplate.render(context, dryRun);
    }

    private void performGradleImport() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();
      File rootLocation = new File(projectLocation().get());
      File wrapperPropertiesFilePath = GradleWrapper.getDefaultPropertiesFilePath(rootLocation);
      try {
        File gradleDistFile = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionFile(GRADLE_LATEST_VERSION);
        if (gradleDistFile == null) {
          GradleWrapper.get(wrapperPropertiesFilePath).updateDistributionUrl(GRADLE_LATEST_VERSION);
        }
        else {
          GradleWrapper.get(wrapperPropertiesFilePath).updateDistributionUrl(gradleDistFile);
        }
      }
      catch (IOException e) {
        // Unlikely to happen. Continue with import, the worst-case scenario is that sync fails and the error message has a "quick fix".
        getLogger().warn("Failed to update Gradle wrapper file", e);
      }

      // This is required for Android plugin in IDEA
      if (!IdeInfo.getInstance().isAndroidStudio()) {
        final Sdk jdk = IdeSdks.getInstance().getJdk();
        if (jdk != null) {
          ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManager.getInstance(project().getValue()).setProjectSdk(jdk));
        }
      }

      //try {
        // Java language level; should be 7 for L and above
        LanguageLevel initialLanguageLevel = null;
        AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
        if (sdkData != null) {
          JavaSdk jdk = JavaSdk.getInstance();
          Sdk sdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdk);
          if (sdk != null) {
            JavaSdkVersion version = jdk.getVersion(sdk);
            if (version != null && version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
              initialLanguageLevel = LanguageLevel.JDK_1_7;
            }
          }
        }

        GradleProjectImporter.Request request = new GradleProjectImporter.Request(project().getValue());
        request.isNewProject = true;
        request.javaLanguageLevel = initialLanguageLevel;

        // The GradleSyncListener will take care of creating the Module top level module and opening Android Studio if gradle sync fails,
        // otherwise the project will be created but Android studio will not open - http://b.android.com/335265
        //projectImporter.importProjectNoSync(request);
      //}
      //catch (IOException e) {
      //  Messages.showErrorDialog(e.getMessage(), message("android.wizard.project.create.error"));
      //  getLogger().error(e);
      //}
    }
  }

  //private static String getOfflineReposString() {
  //  return EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths().stream()
  //                                  .map(f -> FilenameUtils.normalize(f.toString()))
  //                                  .collect(Collectors.joining(","));
  //}
}
