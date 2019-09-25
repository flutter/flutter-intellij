/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeProjectChangeAction;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.parser.Repository;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacetType;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.facet.Facet;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import io.flutter.FlutterUtils;
import io.flutter.actions.FlutterBuildActionGroup;
import io.flutter.module.FlutterModuleImporter;
import io.flutter.module.FlutterProjectType;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.AndroidUtils;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

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
      FlutterSdk sdk = FlutterSdk.forPath(flutterSdk().get());
      if (sdk == null) {
        return false;
      }
      else {
        return useAndroidX().get() && sdk.getVersion().isAndroidxSupported();
      }
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

  public boolean shouldOpenNewWindow() {
    return true;
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
    public boolean shouldOpenNewWindow() {
      return false;
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
      // Create the Flutter module as if it were a normal project that just happens to be located in an Android project directory.
      new FlutterProjectCreator(this).createModule();
      // Import the Flutter module into the Android project as if it had been created without any Android dependencies.
      new FlutterModuleImporter(this).importModule();
      // Link the new module into the Gradle project, which also enables co-editing.
      new FlutterGradleLinker(this).linkNewModule();
    }

    private static class FlutterGradleLinker {
      @NotNull
      private final FlutterProjectModel model;

      private FlutterGradleLinker(@NotNull FlutterProjectModel model) {
        this.model = model;
      }

      private void linkNewModule() {
        Project hostProject = model.project().getValue();
        String hostPath = model.projectLocation().get();
        // If the module is not a direct child of the project root then this File() instance needs to be changed.
        // TODO(messick) Extend the new module wizard to allow nested directories, as Android Studio does using Gradle syntax.
        File flutterProject = new File(hostPath, model.projectName().get());
        VirtualFile flutterModuleDir = VfsUtil.findFileByIoFile(flutterProject, true);
        if (flutterModuleDir == null) {
          return; // Module creation failed; it was reported elsewhere.
        }
        addGradleToModule(flutterModuleDir);

        // Build the AAR repository, needed by Gradle linkage.
        PubRoot pubRoot = PubRoot.forDirectory(VfsUtil.findFileByIoFile(flutterProject, true));
        FlutterSdk sdk = FlutterSdk.forPath(model.flutterSdk().get());
        if (sdk == null) {
          return; // The error would have been shown in super.handleFinished().
        }
        OSProcessHandler handler =
          FlutterBuildActionGroup.build(hostProject, pubRoot, sdk, FlutterBuildActionGroup.BuildType.AAR, "Building AAR");
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {

            new ModuleCoEditHelper(hostProject, model.packageName().get(), flutterModuleDir).enable();
            //addFacetsToModules(hostProject, projectName().get());

            // Ensure Gradle sync runs to link in the new add-to-app module.
            AndroidUtils.scheduleGradleSync(hostProject);
            // TODO(messick) Generate run configs for release and debug. (If needed.)
            // TODO(messick) Load modules from .iml files (?)
          }
        });
      }
    }

    private static void addGradleToModule(VirtualFile moduleDir) {
      // Add settings.gradle to the Flutter module and change .android/build.gradle to do nothing.
      ApplicationManager.getApplication().runWriteAction(() -> {
        try (StringWriter settingsWriter = new StringWriter(); StringWriter buildWriter = new StringWriter()) {
          VirtualFile settingsFile = moduleDir.findOrCreateChildData(moduleDir, "settings.gradle");
          VirtualFile androidDir = Objects.requireNonNull(moduleDir.findChild(".android"));
          VirtualFile buildFile = androidDir.findOrCreateChildData(moduleDir, "build.gradle");
          if (settingsFile.exists()) {
            // The default module template does not have a settings.gradle file so this should not happen.
            settingsWriter.append(new String(settingsFile.contentsToByteArray(false), Charset.defaultCharset()));
            settingsWriter.append(System.lineSeparator());
          }
          if (buildFile.exists()) {
            // The default module template is built for top-level projects.
            buildFile.setBinaryContent("".getBytes(Charset.defaultCharset()));
            buildFile.refresh(false, false);
          }
          settingsWriter.append("// Generated file. Do not edit.");
          settingsWriter.append(System.lineSeparator());
          settingsWriter.append("include ':.android'");
          settingsWriter.append(System.lineSeparator());
          buildWriter.append("// Generated file. Do not edit.");
          buildWriter.append(System.lineSeparator());
          buildWriter.append("buildscript {}");
          buildWriter.append(System.lineSeparator());
          settingsFile.setBinaryContent(settingsWriter.toString().getBytes(Charset.defaultCharset()));
          buildFile.setBinaryContent(buildWriter.toString().getBytes(Charset.defaultCharset()));
        }
        catch (IOException e) {
          // Should not happen
        }
      });
    }
  }

  // Edit settings.gradle according to add-to-app docs.
  private static class ModuleCoEditHelper {
    @NotNull
    private final Project project;
    @NotNull
    private final String packageName;
    @NotNull
    private final String projectName;
    @NotNull
    private final String pathToModule;

    private ModuleCoEditHelper(@NotNull Project project, @NotNull String packageName, @NotNull VirtualFile flutterModuleDir) {
      this.project = project;
      this.packageName = packageName;
      this.projectName = flutterModuleDir.getName();
      File flutterModuleRoot = new File(flutterModuleDir.getPath());
      VirtualFile projectRoot = FlutterUtils.getProjectRoot(project);
      this.pathToModule = Objects.requireNonNull(FileUtilRt.getRelativePath(new File(projectRoot.getPath(), "app"), flutterModuleRoot));
    }

    private void enable() {
      GradleSettingsFile settingsFile = GradleSettingsFile.get(project);
      if (settingsFile == null) {
        return;
      }
      GradleBuildFile gradleBuildFile = settingsFile.getModuleBuildFile(":app");
      if (gradleBuildFile == null) {
        return;
      }

      AtomicReference<List<Repository>> repositories = new AtomicReference<>();
      ApplicationManager.getApplication().runReadAction(() -> {
        //noinspection unchecked
        List<Repository> repBlock = (List<Repository>)gradleBuildFile.getValue(BuildFileKey.LIBRARY_REPOSITORY);
        repositories.set(repBlock != null ? repBlock : new ArrayList<>());
      });
      repositories.get().add(new Repository(Repository.Type.URL, pathToModule + "/build/host/outputs/repo"));

      AtomicReference<List<Dependency>> dependencies = new AtomicReference<>();
      ApplicationManager.getApplication().runReadAction(() -> {
        //noinspection unchecked
        List<Dependency> depBlock = (List<Dependency>)gradleBuildFile.getValue(BuildFileKey.DEPENDENCIES);
        dependencies.set(depBlock != null ? depBlock : new ArrayList<>());
      });
      Dependency.Scope scope = Dependency.Scope.getDefaultScope(project);
      String fqn = packagePrefix(packageName) + "." + projectName;
      dependencies.get().add(new Dependency(scope, Dependency.Type.EXTERNAL, fqn + ":flutter_release:1.0@aar"));

      WriteCommandAction.writeCommandAction(project).run(() -> {
        gradleBuildFile.removeValue(null, BuildFileKey.DEPENDENCIES);
        gradleBuildFile.setValue(BuildFileKey.LIBRARY_REPOSITORY, repositories.get());
        gradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies.get());
      });
    }

    private static String packagePrefix(@NotNull String packageName) {
      int idx = packageName.lastIndexOf('.');
      if (idx <= 0) {
        return packageName;
      }
      return packageName.substring(0, idx);
    }
  }
}
