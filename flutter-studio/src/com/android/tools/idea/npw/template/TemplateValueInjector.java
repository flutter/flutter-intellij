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
package com.android.tools.idea.npw.template;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_FEATURE;
import static com.android.tools.idea.gradle.npw.project.GradleBuildSettings.getRecommendedBuildToolsRevision;
import static com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion;
import static com.android.tools.idea.npw.model.JavaToKotlinHandler.getJavaToKotlinConversionProvider;
import static com.android.tools.idea.npw.model.NewProjectModel.getInitialDomain;
import static com.android.tools.idea.templates.KeystoreUtils.getDebugKeystore;
import static com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_AIDL_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_AIDL_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_APP_TITLE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BASE_FEATURE_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BASE_FEATURE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BASE_FEATURE_RES_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API_REVISION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API_STRING;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_TOOLS_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_COMPANY_DOMAIN;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_CREATE_ACTIVITY;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_DEBUG_KEYSTORE_SHA1;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_EXPLICIT_BUILD_TOOLS_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_GRADLE_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_HAS_APPLICATION_THEME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_INSTANT_APP_API_MIN_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_BASE_FEATURE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_DYNAMIC_FEATURE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_GRADLE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_INSTANT_APP;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LOW_MEMORY;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_PROJECT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_JAVA_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_EAP_REPO;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_EAP_REPO_URL;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_SUPPORT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_LANGUAGE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MANIFEST_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MANIFEST_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API_LEVEL;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MONOLITHIC_MODULE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PROJECT_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_RES_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_RES_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SDK_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SOURCE_PROVIDER_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SRC_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SRC_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API_STRING;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TEST_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TEST_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_THEME_EXISTS;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.KOTLIN_EAP_REPO_URL;
import static com.android.tools.idea.templates.TemplateMetadata.getBuildApiString;
import static com.intellij.openapi.util.io.FileUtil.join;
import static org.jetbrains.android.refactoring.MigrateToAndroidxUtil.isAndroidx;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.instantapp.InstantApps;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.npw.module.ConfigureAndroidModuleStep;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.collect.Iterables;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

/**
 * Utility class that sets common Template values used by a project Module.
 */
public final class TemplateValueInjector {
  private final Map<String, Object> myTemplateValues;

  /**
   * @param templateValues Values will be added to this Map.
   */
  public TemplateValueInjector(@NotNull Map<String, Object> templateValues) {
    this.myTemplateValues = templateValues;
  }

  /**
   * Adds, to the specified <code>templateValues</code>, common render template values like
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_BUILD_API},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_MIN_API},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_TARGET_API}, etc.
   *
   * @param facet Android Facet (existing module)
   */
  public TemplateValueInjector setFacet(@NotNull AndroidFacet facet) {
    addDebugKeyStore(myTemplateValues, facet);

    myTemplateValues.put(ATTR_IS_NEW_PROJECT, false); // Android Modules are called Gradle Projects
    myTemplateValues.put(ATTR_IS_LIBRARY_MODULE, facet.getConfiguration().isLibraryProject());

    String appTheme = MergedManifestManager.getSnapshot(facet).getManifestTheme();
    myTemplateValues.put(ATTR_HAS_APPLICATION_THEME, appTheme != null);

    Module module = facet.getModule();
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform != null) {
      IAndroidTarget target = platform.getTarget();
      myTemplateValues.put(ATTR_BUILD_API, target.getVersion().getFeatureLevel());
      myTemplateValues.put(ATTR_BUILD_API_STRING, getBuildApiString(target.getVersion()));
      // For parity with the value set in #setBuildVersion, the revision is set to 0 if the
      // release is not a preview.
      myTemplateValues.put(ATTR_BUILD_API_REVISION, target.getVersion().isPreview() ? target.getRevision() : 0);
    }

    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(facet);
    AndroidVersion minSdkVersion = moduleInfo.getMinSdkVersion();
    String minSdkName = minSdkVersion.getApiString();

    myTemplateValues.put(ATTR_MIN_API, minSdkName);
    myTemplateValues.put(ATTR_TARGET_API, moduleInfo.getTargetSdkVersion().getApiLevel());
    myTemplateValues.put(ATTR_MIN_API_LEVEL, minSdkVersion.getFeatureLevel());

    Project project = module.getProject();
    addGradleVersions(project);
    addKotlinVersion();
    addAndroidxSupport(project);

    int projectType = facet.getConfiguration().getProjectType();
    if (projectType == PROJECT_TYPE_FEATURE) {
      setInstantAppSupport(true, project, module.getName());
    }
    else if (projectType == PROJECT_TYPE_DYNAMIC_FEATURE) {
      setDynamicFeatureSupport(module);
    }

    return this;
  }

  /**
   * Same as {@link #setFacet(AndroidFacet)}, but uses a {link AndroidVersionsInfo.VersionItem}. This version is used when the Module is
   * not created yet.
   *
   * @param buildVersion Build version information for the new Module being created.
   * @param project      Used to find the Gradle Dependencies versions. If null, it will use the most recent values known.
   */
  public TemplateValueInjector setBuildVersion(@NotNull AndroidVersionsInfo.VersionItem buildVersion, @Nullable Project project) {
    addDebugKeyStore(myTemplateValues, null);

    myTemplateValues.put(ATTR_IS_NEW_PROJECT, true); // Android Modules are called Gradle Projects
    myTemplateValues.put(ATTR_THEME_EXISTS, true); // New modules always have a theme (unless its a library, but it will have no activity)

    myTemplateValues.put(ATTR_MIN_API_LEVEL, buildVersion.getMinApiLevel());
    myTemplateValues.put(ATTR_MIN_API, buildVersion.getMinApiLevelStr());
    myTemplateValues.put(ATTR_BUILD_API, buildVersion.getBuildApiLevel());
    myTemplateValues.put(ATTR_BUILD_API_STRING, buildVersion.getBuildApiLevelStr());
    myTemplateValues.put(ATTR_TARGET_API, buildVersion.getTargetApiLevel());
    myTemplateValues.put(ATTR_TARGET_API_STRING, buildVersion.getTargetApiLevelStr());
    IAndroidTarget target = buildVersion.getAndroidTarget();
    // Note here that the target is null for a non-preview release
    // @see VersionItem#getAndroidTarget()
    myTemplateValues.put(ATTR_BUILD_API_REVISION, target == null ? 0 : target.getRevision());
    if (target != null) { // this is a preview release
      BuildToolInfo info = target.getBuildToolInfo();
      if (info != null) {
        addBuildToolVersion(project, info.getRevision());
      }
    }
    addGradleVersions(project);
    addKotlinVersion();
    addAndroidxSupport(project);
    return this;
  }

  public TemplateValueInjector setJavaVersion(Project project) {
    // We can't JUST look at the overall project level language level, since
    // Gradle sync appears not to sync the overall project level; instead we
    // have to take the min of all the modules
    LanguageLevel min = ApplicationManager.getApplication().runReadAction((Computable<LanguageLevel>)() -> {
      LanguageLevel minResult = null;
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        LanguageLevelModuleExtension moduleLevelExt = LanguageLevelModuleExtensionImpl.getInstance(module);
        if (moduleLevelExt != null) {
          LanguageLevel moduleLevel = moduleLevelExt.getLanguageLevel();
          if (moduleLevel != null) {
            if (minResult == null || moduleLevel.compareTo(minResult) < 0) {
              minResult = moduleLevel;
            }
          }
        }
      }
      return minResult == null ? LanguageLevelProjectExtension.getInstance(project).getLanguageLevel() : minResult;
    });

    myTemplateValues.put(ATTR_JAVA_VERSION, JpsJavaSdkType.complianceOption(min.toJavaVersion()));

    return this;
  }

  /**
   * Adds, to the specified <code>templateValues</code>, common Module roots template values like
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_PROJECT_OUT},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_SRC_DIR},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_SRC_OUT}, etc.
   *
   * @param paths       Project paths
   * @param packageName Package Name for the module
   */
  public TemplateValueInjector setModuleRoots(@NotNull AndroidModuleTemplate paths, @NotNull String projectPath,
                                              @NotNull String moduleName, @NotNull String packageName) {
    File moduleRoot = paths.getModuleRoot();

    assert moduleRoot != null;

    // Register the resource directories associated with the active source provider
    myTemplateValues.put(ATTR_PROJECT_OUT, FileUtil.toSystemIndependentName(moduleRoot.getAbsolutePath()));

    File srcDir = paths.getSrcDirectory(packageName);
    if (srcDir != null) {
      myTemplateValues.put(ATTR_SRC_DIR, getRelativePath(moduleRoot, srcDir));
      myTemplateValues.put(ATTR_SRC_OUT, FileUtil.toSystemIndependentName(srcDir.getAbsolutePath()));
    }

    File testDir = paths.getTestDirectory(packageName);
    if (testDir != null) {
      myTemplateValues.put(ATTR_TEST_DIR, getRelativePath(moduleRoot, testDir));
      myTemplateValues.put(ATTR_TEST_OUT, FileUtil.toSystemIndependentName(testDir.getAbsolutePath()));
    }

    File resDir = Iterables.getFirst(paths.getResDirectories(), null);
    if (resDir != null) {
      myTemplateValues.put(ATTR_RES_DIR, getRelativePath(moduleRoot, resDir));
      myTemplateValues.put(ATTR_RES_OUT, FileUtil.toSystemIndependentName(resDir.getPath()));
    }

    File manifestDir = paths.getManifestDirectory();
    if (manifestDir != null) {
      myTemplateValues.put(ATTR_MANIFEST_DIR, getRelativePath(moduleRoot, manifestDir));
      myTemplateValues.put(ATTR_MANIFEST_OUT, FileUtil.toSystemIndependentName(manifestDir.getPath()));
    }

    File aidlDir = paths.getAidlDirectory(packageName);
    if (aidlDir != null) {
      myTemplateValues.put(ATTR_AIDL_DIR, getRelativePath(moduleRoot, aidlDir));
      myTemplateValues.put(ATTR_AIDL_OUT, FileUtil.toSystemIndependentName(aidlDir.getPath()));
    }

    if (moduleName.startsWith(":")) {
      // The templates already add an initial ":"
      moduleName = moduleName.substring(1);
    }

    myTemplateValues.put(ATTR_PROJECT_LOCATION, projectPath);
    myTemplateValues.put(ATTR_MODULE_NAME, moduleName);
    myTemplateValues.put(ATTR_PACKAGE_NAME, packageName);

    return this;
  }

  /**
   * Adds, to the specified <code>templateValues</code>, common render template values like
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_APP_TITLE},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_GRADLE_PLUGIN_VERSION},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_GRADLE_VERSION}, etc.
   */
  public TemplateValueInjector setProjectDefaults(@Nullable Project project, @NotNull String moduleTitle) {
    myTemplateValues.put(ATTR_APP_TITLE, moduleTitle);

    // For now, our definition of low memory is running in a 32-bit JVM. In this case, we have to be careful about the amount of memory we
    // request for the Gradle build.
    myTemplateValues.put(ATTR_IS_LOW_MEMORY, SystemInfo.is32Bit);

    addGradleVersions(project);
    addKotlinVersion();

    myTemplateValues.put(ATTR_IS_GRADLE, true);

    // TODO: Check if this is used at all by the templates
    myTemplateValues.put("target.files", new HashSet<>());
    myTemplateValues.put("files.to.open", new ArrayList<>());

    // TODO: Check this one with Joe. It seems to be used by the old code on Import module, but can't find it on new code
    myTemplateValues.put(ATTR_CREATE_ACTIVITY, false);

    // TODO: This seems project stuff
    if (project != null) {
      myTemplateValues.put(ATTR_TOP_OUT, project.getBasePath());
    }

    final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    ProgressIndicator progress = new StudioLoggerProgressIndicator(ConfigureAndroidModuleStep.class);

    addBuildToolVersion(project, getRecommendedBuildToolsRevision(sdkHandler, progress));

    File sdkLocation = sdkHandler.getLocation();
    if (sdkLocation != null) {
      myTemplateValues.put(ATTR_SDK_DIR, sdkLocation.getPath());
    }

    return this;
  }

  public TemplateValueInjector setLanguage(Language language) {
    myTemplateValues.put(ATTR_LANGUAGE, language.getName());
    myTemplateValues.put(ATTR_KOTLIN_SUPPORT, language == Language.KOTLIN);
    return this;
  }

  public TemplateValueInjector setInstantAppSupport(boolean isExistingProject, @NotNull Project project, @NotNull String moduleName) {
    myTemplateValues.put(ATTR_IS_INSTANT_APP, true);
    myTemplateValues.put(ATTR_INSTANT_APP_API_MIN_VERSION, InstantApps.getCompatApiMinVersion());

    myTemplateValues.put(ATTR_IS_LIBRARY_MODULE, true);

    String projectPath = project.getBasePath();
    assert projectPath != null;
    String defaultResourceSuffix = join("src", "main", "res");
    File projectRoot = new File(projectPath);
    File baseModuleRoot = new File(projectRoot, "base");
    File baseModuleResourceRoot = new File(baseModuleRoot, defaultResourceSuffix);
    Module baseFeature = null;
    if (isExistingProject) {
      baseFeature = InstantApps.findBaseFeature(project);
      if (baseFeature == null) {
        baseModuleRoot = new File(projectRoot, moduleName);
        baseModuleResourceRoot = new File(baseModuleRoot, defaultResourceSuffix);
        myTemplateValues.put(ATTR_IS_BASE_FEATURE, true);
        String monolithicModuleName = InstantApps.findMonolithicModuleName(project);
        if (monolithicModuleName != null) {
          myTemplateValues.put(ATTR_MONOLITHIC_MODULE_NAME, monolithicModuleName);
        }
      }
    }

    if (baseFeature == null) {
      myTemplateValues.put(ATTR_BASE_FEATURE_NAME, baseModuleRoot.getName());
      myTemplateValues.put(ATTR_BASE_FEATURE_DIR, baseModuleRoot.getPath());
      myTemplateValues.put(ATTR_BASE_FEATURE_RES_DIR, baseModuleResourceRoot.getPath());
    }
    else {
      setBaseFeature(baseFeature);
    }

    return this;
  }

  public TemplateValueInjector setDynamicFeatureSupport(@NotNull Module module) {
    myTemplateValues.put(ATTR_IS_DYNAMIC_FEATURE, true);

    Module baseFeature = DynamicAppUtils.getBaseFeature(module);
    if (baseFeature == null) {
      throw new RuntimeException("Dynamic Feature Module '" + module.getName() + "' has no Base Module");
    }

    return setBaseFeature(baseFeature);
  }

  public TemplateValueInjector setBaseFeature(@NotNull Module baseFeature) {
    AndroidModuleModel moduleModel = AndroidModuleModel.get(baseFeature);
    assert moduleModel != null;
    File baseModuleRoot = moduleModel.getRootDirPath();
    Collection<File> resDirectories = moduleModel.getDefaultSourceProvider().getResDirectories();
    assert !resDirectories.isEmpty();
    File baseModuleResourceRoot = resDirectories.iterator().next(); // Put the new resources in any of the available res directories

    myTemplateValues.put(ATTR_BASE_FEATURE_NAME, baseModuleRoot.getName());
    myTemplateValues.put(ATTR_BASE_FEATURE_DIR, baseModuleRoot.getPath());
    myTemplateValues.put(ATTR_BASE_FEATURE_RES_DIR, baseModuleResourceRoot.getPath());

    return this;
  }

  public void addGradleVersions(@Nullable Project project) {
    myTemplateValues.put(ATTR_GRADLE_PLUGIN_VERSION, determineGradlePluginVersion(project).toString());
    myTemplateValues.put(ATTR_GRADLE_VERSION, SdkConstants.GRADLE_LATEST_VERSION);
  }

  public TemplateValueInjector addTemplateAdditionalValues(@NotNull String packageName, boolean isInstantApp,
                                                           @NotNull ObjectProperty<NamedModuleTemplate> template) {
    myTemplateValues.put(ATTR_PACKAGE_NAME, packageName);
    myTemplateValues.put(ATTR_SOURCE_PROVIDER_NAME, template.get().getName());
    myTemplateValues.put(ATTR_IS_INSTANT_APP, isInstantApp);
    myTemplateValues.put(ATTR_COMPANY_DOMAIN, getInitialDomain(false));

    return this;
  }

  private void addKotlinVersion() {
    assert PluginManager.getPlugin(PluginId.findId(("org.jetbrains.kotlin"))) != null ||
           !GuiTestingService.getInstance().isGuiTestingMode()
      : "Run Test Configuration missing. You should set -Dplugin.path=../../../../prebuilts/tools/common/kotlin-plugin/Kotlin";

    // Always add the kotlin version attribute. If we are adding a new kotlin activity, we may need to add dependencies
    final ConvertJavaToKotlinProvider provider = getJavaToKotlinConversionProvider();
    String kotlinVersion = provider.getKotlinVersion();
    myTemplateValues.put(ATTR_KOTLIN_VERSION, kotlinVersion);
    if (isEAP(kotlinVersion)) {
      myTemplateValues.put(ATTR_KOTLIN_EAP_REPO, true);
      myTemplateValues.put(ATTR_KOTLIN_EAP_REPO_URL, KOTLIN_EAP_REPO_URL);
    }
  }

  private boolean isEAP(String version) {
    return version.contains("rc") || version.contains("eap") || version.contains("-M");
  }

  private void addBuildToolVersion(@Nullable Project project, @NotNull Revision buildToolRevision) {
    GradleVersion gradlePluginVersion = determineGradlePluginVersion(project);
    myTemplateValues.put(ATTR_BUILD_TOOLS_VERSION, buildToolRevision.toString());
    myTemplateValues.put(ATTR_EXPLICIT_BUILD_TOOLS_VERSION, needsExplicitBuildToolsVersion(gradlePluginVersion, buildToolRevision));
  }

  private void addAndroidxSupport(@Nullable Project project) {
    if (project != null) {
      myTemplateValues.put(ATTR_ANDROIDX_SUPPORT, isAndroidx(project));
    }
  }

  private static void addDebugKeyStore(@NotNull Map<String, Object> templateValues, @Nullable AndroidFacet facet) {
    try {
      File sha1File = facet == null ? getOrCreateDefaultDebugKeystore() : getDebugKeystore(facet);
      templateValues.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(sha1File));
    }
    catch (Exception e) {
      getLog().info("Could not compute SHA1 hash of debug keystore.", e);
      templateValues.put(ATTR_DEBUG_KEYSTORE_SHA1, "");
    }
  }

  /**
   * Helper method for converting two paths relative to one another into a String path, since this
   * ends up being a common pattern when creating values to put into our template's data model.
   */
  @Nullable
  private static String getRelativePath(@NotNull File base, @NotNull File file) {
    // Note: Use FileUtil.getRelativePath(String, String, char) instead of FileUtil.getRelativePath(File, File), because the second version
    // will use the base.getParent() if base directory is not yet created  (when adding a new module, the directory is created later)
    return FileUtil.getRelativePath(FileUtil.toSystemIndependentName(base.getPath()),
                                    FileUtil.toSystemIndependentName(file.getPath()), '/');
  }

  /**
   * Find the most appropriated Gradle Plugin version for the specified project.
   *
   * @param project If {@code null} (ie we are creating a new project) returns the recommended gradle version.
   */
  @NotNull
  private static GradleVersion determineGradlePluginVersion(@Nullable Project project) {
    GradleVersion defaultGradleVersion = GradleVersion.parse(AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion());
    if (project == null) {
      return defaultGradleVersion;
    }

    GradleVersion versionInUse = GradleUtil.getAndroidGradleModelVersionInUse(project);
    if (versionInUse != null) {
      return versionInUse;
    }

    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(project);
    GradleVersion pluginVersion = (androidPluginInfo == null) ? null : androidPluginInfo.getPluginVersion();
    return (pluginVersion == null) ? defaultGradleVersion : pluginVersion;
  }

  private static Logger getLog() {
    return Logger.getInstance(TemplateValueInjector.class);
  }
}
