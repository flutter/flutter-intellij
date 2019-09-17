/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.AndroidUtils;
import java.io.File;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Note that a single instance of this class is shared among all the steps in the wizard.
 * <p>
 * There is some inconsistency in which values are saved and which reset when switching from
 * one project type to another. The AS New Module wizard has similar inconsistencies (as of beta 5).
 * <p>
 * TODO(messick): Add tests to simulate clicking Next/Previous buttons and choosing different project types.
 */
public class FlutterProjectModel extends WizardModel {
  private static final String DEFAULT_DOMAIN = "example.com"; // Keep this in (reversed) sync with 'flutter create'.
  private static final String PROPERTIES_DOMAIN_KEY = "FLUTTER_COMPANY_DOMAIN";
  private static final String PROPERTIES_KOTLIN_SUPPORT_KEY = "FLUTTER_PROJECT_KOTLIN_SUPPORT";
  private static final String PROPERTIES_SWIFT_SUPPORT_KEY = "FLUTTER_PROJECT_SWIFT_SUPPORT";
  private static final String PROPERTIES_ANDROIDX_SUPPORT_KEY = "FLUTTER_PROJECT_ANDROIDX_SUPPORT";

  @NotNull final private OptionalValueProperty<FlutterProjectType> myProjectType = new OptionalValueProperty<>();
  @NotNull final private StringProperty myFlutterSdk = new StringValueProperty();
  @NotNull final private StringProperty myProjectName = new StringValueProperty();
  @NotNull final private StringProperty myPackageName = new StringValueProperty();
  @NotNull final private StringProperty myProjectLocation = new StringValueProperty();
  @NotNull final private StringProperty myCompanyDomain = new StringValueProperty(getInitialDomain());
  @NotNull final private StringProperty myDescription = new StringValueProperty();
  @NotNull final private BoolValueProperty myKotlin = new BoolValueProperty(getInitialKotlinSupport());
  @NotNull final private BoolValueProperty mySwift = new BoolValueProperty(getInitialSwiftSupport());
  @NotNull final private OptionalProperty<Project> myProject = new OptionalValueProperty<>();
  @NotNull final private BoolValueProperty myIsOfflineSelected = new BoolValueProperty();
  @NotNull final private BoolValueProperty myAndroidX = new BoolValueProperty();

  public FlutterProjectModel(@NotNull FlutterProjectType type) {
    myProjectType.set(new OptionalValueProperty<>(type));

    myCompanyDomain.addListener(() -> {
      String domain = myCompanyDomain.get();
      if (domain.isEmpty()) {
        domain = null; // Keys with null values are deleted.
      }
      PropertiesComponent.getInstance().setValue(PROPERTIES_DOMAIN_KEY, domain);
    });

    myProjectName.addConstraint(String::trim);

    myKotlin.set(getInitialKotlinSupport());
    myKotlin.addListener(() -> setInitialKotlinSupport(myKotlin.get()));

    mySwift.set(getInitialSwiftSupport());
    mySwift.addListener(() -> setInitialSwiftSupport(mySwift.get()));

    myAndroidX.set(getInitialAndroidxSupport());
    myAndroidX.addListener(() -> setInitialAndroidxSupport(myAndroidX.get()));
  }

  @NotNull
  public OptionalValueProperty<FlutterProjectType> projectType() {
    return myProjectType;
  }

  @NotNull
  public StringProperty projectName() {
    return myProjectName;
  }

  @NotNull
  public StringProperty projectLocation() {
    return myProjectLocation;
  }

  @NotNull
  public StringProperty companyDomain() {
    return myCompanyDomain;
  }

  @NotNull
  public StringProperty packageName() {
    return myPackageName;
  }

  @NotNull
  public StringProperty description() {
    return myDescription;
  }

  @NotNull
  public BoolValueProperty useKotlin() {
    return myKotlin;
  }

  @NotNull
  public BoolValueProperty useSwift() {
    return mySwift;
  }

  @NotNull
  public BoolValueProperty useAndroidX() {
    return myAndroidX;
  }

  public boolean isGeneratingAndroidX() {
    if (project().getValueOrNull() == null) {
      return useAndroidX().get() && FlutterSdk.forPath(flutterSdk().get()).getVersion().isAndroidxSupported();
    }
    else {
      return FlutterUtils.isAndroidxProject(project().getValue());
    }
  }

  @NotNull
  public StringProperty flutterSdk() {
    return myFlutterSdk;
  }

  @NotNull
  public OptionalProperty<Project> project() {
    return myProject;
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  public boolean isModule() {
    return projectType().getValue() == FlutterProjectType.MODULE;
  }

  @Override
  protected void handleFinished() {
    String location = myProjectLocation.get();
    assert (!myProjectName.get().isEmpty());
    assert (!myFlutterSdk.get().isEmpty());
    assert (!location.isEmpty());
    if (location.endsWith("/")) {
      location = location.substring(0, location.length() - 1);
    }
    if (!FlutterProjectCreator.finalValidityCheckPassed(location)) {
      // TODO(messick): Navigate to the step that sets location (if that becomes possible in the AS wizard framework).
      // See NewProjectModel.doDryRun();
      return;
    }
    new FlutterProjectCreator(this).createProject();
  }

  public BoolValueProperty isOfflineSelected() {
    return myIsOfflineSelected;
  }

  @NotNull
  private static String getInitialDomain() {
    String domain = PropertiesComponent.getInstance().getValue(PROPERTIES_DOMAIN_KEY);
    return domain == null ? DEFAULT_DOMAIN : domain;
  }

  private static boolean getInitialKotlinSupport() {
    return PropertiesComponent.getInstance().getBoolean(PROPERTIES_KOTLIN_SUPPORT_KEY, true);
  }

  private static void setInitialKotlinSupport(boolean isSupported) {
    PropertiesComponent.getInstance().setValue(PROPERTIES_KOTLIN_SUPPORT_KEY, isSupported, true);
  }

  private static boolean getInitialSwiftSupport() {
    return PropertiesComponent.getInstance().getBoolean(PROPERTIES_SWIFT_SUPPORT_KEY, true);
  }

  private static void setInitialSwiftSupport(boolean isSupported) {
    PropertiesComponent.getInstance().setValue(PROPERTIES_SWIFT_SUPPORT_KEY, isSupported, true);
  }

  private static boolean getInitialAndroidxSupport() {
    return PropertiesComponent.getInstance().getBoolean(PROPERTIES_ANDROIDX_SUPPORT_KEY, true);
  }

  private static void setInitialAndroidxSupport(boolean isSupported) {
    PropertiesComponent.getInstance().setValue(PROPERTIES_ANDROIDX_SUPPORT_KEY, isSupported, true);
  }

  public static class AddToApp extends FlutterProjectModel {
    public AddToApp(@NotNull FlutterProjectType type) {
      super(type);
    }

    @Override
    protected void handleFinished() {
      useAndroidX().set(true);
      // The host project is an Android app. This module should be created in its root directory.
      // Android Studio supports a colon-separated convention to specify sub-directories, which is not yet supported here.
      Project hostProject = project().getValue();
      String hostPath = hostProject.getBasePath();
      if (hostPath == null) {
        throw new InvalidDataException(); // Can't happen
      }
      projectLocation().set(hostPath);
      super.handleFinished();
      // TODO flutter build aar --debug
      // If the module is not a direct subdir of the project root then this File() instance needs to be changed.
      VirtualFile flutterModuleDir = VfsUtil.findFileByIoFile(new File(hostPath, projectName().get()), true);
      if (flutterModuleDir == null) {
        return; // Module creation failed; it was reported elsewhere.
      }
      new ModuleCoEditHelper(hostProject, flutterModuleDir).enable();
      // Ensure Gradle sync runs to link in the new add-to-app module.
      AndroidUtils.scheduleGradleSync(hostProject);
      // TODO(messick) Generate run configs for release and debug. (If needed.)
    }
  }

  // Edit settings.gradle according to add-to-app docs.
  private static class ModuleCoEditHelper {
    @NotNull
    private final Project project;
    @NotNull
    private final VirtualFile flutterModuleDir;
    @NotNull
    private final File flutterModuleRoot;
    @NotNull
    private final VirtualFile projectRoot;
    @NotNull
    private final String pathToModule;

    private VirtualFile buildFile;

    private ModuleCoEditHelper(@NotNull Project project, @NotNull VirtualFile flutterModuleDir) {
      this.project = project;
      this.flutterModuleDir = flutterModuleDir;
      this.flutterModuleRoot = new File(flutterModuleDir.getPath());
      this.projectRoot = FlutterUtils.getProjectRoot(project);
      this.pathToModule = Objects.requireNonNull(FileUtilRt.getRelativePath(new File(projectRoot.getPath()), flutterModuleRoot));
    }

    private void enable() {
      buildFile = GradleUtil.getGradleBuildFile(new File(projectRoot.getPath(), "app"));
      if (buildFile == null) {
        return;
      }
      GradleBuildFile gradleBuildFile = parseBuildFile();
    }

    private GradleBuildFile parseBuildFile() {
      // See AndroidUtils.parseSettings() if API skew causes a need for reflection.
      return BuildModelContext.create(project).getOrCreateBuildFile(projectRoot, true);
    }
  }
}
