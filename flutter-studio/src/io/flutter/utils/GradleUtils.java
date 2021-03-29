/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.utils;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.Objects.requireNonNull;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.java.IKeywordElementType;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.WeakList;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import io.flutter.FlutterUtils;
import io.flutter.android.GradleSyncProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.Objects.requireNonNull;

/*
  Add-to-app notes:
  - The Flutter module must be added to the Android app via manual editing as in the add-to-app docs,
    or by using the tool in Android Studio that generates a module and does the editing.
  - The Flutter module may be in a directory nested under the Android app or it may be in a different
    location entirely. IntelliJ supports both, and is moving toward the latter.
  - Enabling co-editing means converting the Flutter module to a Gradle module and adding it to the
    Android app as a sub-project.
      o If the parent of the Flutter module root directory is the Android app
        root directory, add "include ':flutterModule'" to the parent settings.gradle;
        otherwise something like what add-to-app does to define the :flutter module is needed.
      o Create a simple build.gradle in the Flutter module root directory.
      o Add Android-Gradle and Android facets to the Idea module of the Flutter module.
 */
public class GradleUtils {

  private static final String FLUTTER_PROJECT_NAME = ":" + AndroidUtils.FLUTTER_MODULE_NAME + ":";
  private static final String FLUTTER_TASK_PREFIX = "compileFlutterBuild";
  private static final WeakList<Project> COEDIT_TRANSFORMED_PROJECTS = new WeakList<>();
  private static final int GRADLE_SYNC_TIMEOUT = 5 * 60 * 1000; // 5 minutes in millis.

  public static void addGradleListeners(@NotNull Project project) {
    if (!FlutterUtils.isAndroidStudio()) {
      // We're not supporting Gradle integration with IntelliJ currently, so these are disabled for now.
      // TODO(messick): Support Gradle in IntelliJ for add-to-app.
      //GradleSyncState.subscribe(project, new GradleSyncListener() {
      //  @Override
      //  public void syncSucceeded(@NotNull Project project) {
      //    checkDartSupport(project);
      //    if (isCoeditTransformedProject(project)) {
      //      return;
      //    }
      //    enableCoeditIfAddToAppDetected(project);
      //  }
      //});
      //GradleBuildState.subscribe(project, new GradleBuildListener.Adapter() {
      //  @Override
      //  public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
      //    checkDartSupport(project);
      //    if (isCoeditTransformedProject(project)) {
      //      return;
      //    }
      //    if (status == BuildStatus.SUCCESS && context != null && context.getGradleTasks().contains(":flutter:generateDebugSources")) {
      //      enableCoeditIfAddToAppDetected(project);
      //    }
      //  }
      //});
    }
  }

  public static void scheduleGradleSync(@NotNull Project project) {
    GradleSyncProvider provider = GradleSyncProvider.EP_NAME.getExtensionList().get(0);
    provider.scheduleSync(project);
  }

  public static void enableCoeditIfAddToAppDetected(@NotNull Project project) {
    if (isCoeditTransformedProject(project)) {
      return;
    }
    // After a Gradle sync has finished we check the tasks that were run to see if any belong to Flutter.
    Map<ProjectData, MultiMap<String, String>> tasks = getTasksMap(project);
    @NotNull String projectName = project.getName();
    for (ProjectData projectData : tasks.keySet()) {
      MultiMap<String, String> map = tasks.get(projectData);
      Collection<String> col = map.get(FLUTTER_PROJECT_NAME);
      if (col.isEmpty()) {
        col = map.get(""); // Android Studio uses this.
      }
      if (!col.isEmpty()) {
        if (col.parallelStream().anyMatch((x) -> x.startsWith(FLUTTER_TASK_PREFIX))) {
          ApplicationManager.getApplication().invokeLater(() -> enableCoEditing(project));
        }
      }
    }
  }

  private static void runAfterSyncFinishes(@NotNull Project project, @NotNull Consumer<Project> runnable) {
    new WaitFor(GRADLE_SYNC_TIMEOUT, () -> runnable.accept(project)) {
      @Override
      public boolean condition() {
        return !GradleSyncState.getInstance(project).isSyncInProgress();
      }
    };
  }

  public static void checkDartSupport(@NotNull Project project) {
    runAfterSyncFinishes(project, (p) -> {
      // Gradle sync-finished events are triggered before new modules have been committed. Once the condition
      // is met we still have to wait for a short while.
      AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
        Stream<Module> modules =
          Arrays.stream(FlutterModuleUtils.getModules(p)).filter(FlutterModuleUtils::declaresFlutter);
        modules.forEach((module) -> {
          if (!DartSdkLibUtil.isDartSdkEnabled(module)) {
            new EnableDartSupportForModule(module).run();
          }
        });
      }, 1, TimeUnit.SECONDS);
    });
  }

  private static boolean isVanillaAddToApp(@NotNull Project project, @Nullable VirtualFile file, @NotNull String name) {
    if (file == null) {
      return false;
    }
    VirtualFile dir = file.getParent();
    if (dir.findChild(".ios") == null) {
      dir = dir.getParent();
    }
    if (dir.getName().equals(".android")) {
      dir = dir.getParent();
    }
    if (dir.findChild(".ios") == null && dir.findChild("ios") == null) {
      return false;
    }
    if (doesBuildFileExist(dir)) {
      return true;
    }
    GradleSettingsFile parsedSettings = parseSettings(project);
    if (parsedSettings == null) {
      return false;
    }
    // Check settings for "include :name".
    return findInclude(requireNonNull(parsedSettings), name) == null;
  }

  private static boolean doesBuildFileExist(VirtualFile dir) {
    return new File(dir.getPath(), SdkConstants.FN_BUILD_GRADLE).exists();
  }

  @Nullable
  private static GradleSettingsFile parseSettings(Project project) {
    // Get the PSI for settings.gradle. In IntelliJ it is just this, but Android Studio uses a VirtualFile.
    //GradleSettingsFile parsedSettings =
    //  BuildModelContext.create(project).getOrCreateSettingsFile(project);
    // We need to use reflection to create the expression so this code can be compiled (and run) in all the
    // code bases that are currently supported.
    boolean isAndroidStudio = FlutterUtils.isAndroidStudio();
    VirtualFile projectDir = requireNonNull(FlutterUtils.getProjectRoot(project));
    Object param = isAndroidStudio ? projectDir.findChild(SdkConstants.FN_SETTINGS_GRADLE) : project;
    if (param == null) {
      return null;
    }
    Method method =
      ReflectionUtil.getMethod(BuildModelContext.class, "getOrCreateSettingsFile", isAndroidStudio ? VirtualFile.class : Project.class);
    if (method == null) {
      return null;
    }
    try {
      return (GradleSettingsFile)method.invoke(makeBuildModelContext(project), param);
    }
    catch (InvocationTargetException | IllegalAccessException e) {
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  private static BuildModelContext makeBuildModelContext(Project project) {
    // return BuildModelContext.create(project);
    Method method = ReflectionUtil.getDeclaredMethod(BuildModelContext.class, "create", Project.class);
    if (method != null) {
      try {
        return (BuildModelContext)method.invoke(null, project);
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
    // If we get here we're using the 4.1 API.
    // return BuildModelContext.create(project, new AndroidLocationProvider());
    Class locationProviderClass = AndroidLocationProvider.class.getInterfaces()[0];
    // Class.forName("com.android.tools.idea.gradle.dsl.model.BuildModelContext.ResolvedConfigurationFileLocationProvider");
    // does not work in the debugger. That's why we get it from the interfaces of AndroidLocationProvider.
    method = ReflectionUtil.getDeclaredMethod(BuildModelContext.class, "create", Project.class, locationProviderClass);
    assert method != null;
    try {
      return (BuildModelContext)method.invoke(null, project, new AndroidLocationProvider());
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  // The project is an Android project that contains a Flutter module.
  private static void enableCoEditing(@NotNull Project project) {
    Module module = FlutterUtils.findFlutterGradleModule(project);
    if (module == null) return;
    VirtualFile root = FlutterUtils.locateModuleRoot(module);
    if (root == null) return;
    VirtualFile androidDir = root.getParent();
    VirtualFile flutterModuleDir = androidDir.getParent();
    String flutterModuleName = flutterModuleDir.getName();
    if (!isVanillaAddToApp(project, androidDir, flutterModuleName)) return;
    new CoEditHelper(project, flutterModuleDir).enable();
  }

  private static void addCoeditTransformedProject(@NotNull Project project) {
    if (!project.isDisposed()) {
      COEDIT_TRANSFORMED_PROJECTS.add(project);
    }
  }

  private static boolean isCoeditTransformedProject(@NotNull Project project) {
    if (project.isDisposed()) {
      return true;
    }
    Iterator<Project> iter = COEDIT_TRANSFORMED_PROJECTS.iterator();
    //noinspection WhileLoopReplaceableByForEach
    while (iter.hasNext()) { // See WeakList.iterator().
      Project p = iter.next();
      if (project.equals(p) && !p.isDisposed()) {
        return true;
      }
    }
    return false;
  }

  // Copied from org.jetbrains.plugins.gradle.execution.GradleRunAnythingProvider.
  @NotNull
  @SuppressWarnings("DuplicatedCode")
  private static Map<ProjectData, MultiMap<String, String>> getTasksMap(Project project) {
    Map<ProjectData, MultiMap<String, String>> tasks = new LinkedHashMap<>();
    for (GradleProjectSettings setting : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
      final ExternalProjectInfo projectData =
        ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, setting.getExternalProjectPath());
      if (projectData == null || projectData.getExternalProjectStructure() == null) continue;

      MultiMap<String, String> projectTasks = MultiMap.createOrderedSet();
      for (DataNode<ModuleData> moduleDataNode : getChildren(projectData.getExternalProjectStructure(), ProjectKeys.MODULE)) {
        String gradlePath;
        String moduleId = moduleDataNode.getData().getId();
        if (moduleId.charAt(0) != ':') {
          int colonIndex = moduleId.indexOf(':');
          gradlePath = colonIndex > 0 ? moduleId.substring(colonIndex) : ":";
        }
        else {
          gradlePath = moduleId;
        }
        for (DataNode<TaskData> node : getChildren(moduleDataNode, ProjectKeys.TASK)) {
          TaskData taskData = node.getData();
          String taskName = taskData.getName();
          if (isNotEmpty(taskName)) {
            String taskPathPrefix = ":".equals(gradlePath) || taskName.startsWith(gradlePath) ? "" : (gradlePath + ':');
            projectTasks.putValue(taskPathPrefix, taskName);
          }
        }
      }
      tasks.put(projectData.getExternalProjectStructure().getData(), projectTasks);
    }
    return tasks;
  }

  @Nullable
  private static PsiElement findInclude(GradleSettingsFile parsedSettings, String name) {
    Map<String, GradleDslElement> elements = parsedSettings.getPropertyElements();
    GradleDslElement includes = elements.get("include");
    if (includes == null) {
      return null;
    }
    for (GradleDslElement include : includes.getChildren()) {
      if (include instanceof GradleDslExpressionList) {
        for (GradleDslExpression expr : ((GradleDslExpressionList)include).getExpressions()) {
          if (expr instanceof GradleDslLiteral) {
            PsiElement lit = expr.getExpression();
            if (matchesName(lit, name)) {
              return lit;
            }
          }
        }
      }
      if (include instanceof GradleDslLiteral) {
        PsiElement expr = ((GradleDslLiteral)include).getExpression();
        if (matchesName(expr, name)) {
          return expr;
        }
      }
    }
    return null;
  }

  private static boolean matchesName(PsiElement expr, String name) {
    if (expr == null) return false;
    String text = expr.getText();
    return name.equals(text.substring(2, text.length() - 1));
  }

  private static class CoEditHelper {
    // TODO(messick) Rewrite this to use ProjectBuildModel. See FlutterModuleImporter for an example.

    @NotNull
    private final Project project;
    @NotNull
    private VirtualFile flutterModuleDir;
    @NotNull
    private final File flutterModuleRoot;
    @NotNull
    private final String flutterModuleName;

    private boolean hasIncludeFlutterModuleStatement;
    private boolean buildFileIsValid;
    private boolean inSameDir;
    private VirtualFile buildFile;
    private VirtualFile settingsFile;
    private VirtualFile projectRoot;
    private String pathToModule;
    private AtomicBoolean errorDuringOperation = new AtomicBoolean(false);

    private CoEditHelper(@NotNull Project project, @NotNull VirtualFile flutterModuleDir) {
      this.project = project;
      this.flutterModuleDir = flutterModuleDir;
      this.flutterModuleRoot = new File(flutterModuleDir.getPath());
      this.flutterModuleName = flutterModuleDir.getName();
    }

    private void enable() {
      // Look for "include ':flutterModuleName'". If not found, add it and create build.gradle in the Flutter module. Then sync.
      if (verifyEligibility()) {
        makeBuildFile();
        if (errorDuringOperation.get()) return;
        addIncludeStatement();
        if (errorDuringOperation.get()) return;
        addCoeditTransformedProject(project);
        // We may have multiple Gradle sync listeners. Write the files to disk synchronously so we won't edit them twice.
        projectRoot.refresh(false, true);
        if (!projectRoot.equals(flutterModuleDir.getParent())) {
          flutterModuleDir.refresh(false, true);
        }
        AppExecutorUtil.getAppExecutorService().execute(() -> scheduleGradleSyncAfterSyncFinishes(project));
      }
    }

    private static void scheduleGradleSyncAfterSyncFinishes(@NotNull Project project) {
      runAfterSyncFinishes(project, (p) -> scheduleGradleSync((project)));
    }

    private boolean verifyEligibility() {
      projectRoot = FlutterUtils.getProjectRoot(project);
      requireNonNull(projectRoot);
      settingsFile = projectRoot.findChild(SdkConstants.FN_SETTINGS_GRADLE);
      if (settingsFile == null) {
        return false;
      }
      GradleSettingsFile parsedSettings = parseSettings(project);
      if (parsedSettings == null) {
        return false;
      }
      PsiElement includeFlutterModuleStmt = findInclude(parsedSettings, flutterModuleName);
      hasIncludeFlutterModuleStatement = includeFlutterModuleStmt != null;
      buildFile = GradleUtil.getGradleBuildFile(flutterModuleRoot);
      buildFileIsValid = buildFile != null && doesBuildFileExist(flutterModuleDir) && buildFile.getLength() > 0;
      return !(hasIncludeFlutterModuleStatement && buildFileIsValid);
    }

    private void makeBuildFile() {
      if (buildFileIsValid) {
        return;
      }
      createBuildFile();
      if (errorDuringOperation.get()) return;
      writeBuildFile();
    }

    private void createBuildFile() {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          buildFile = flutterModuleDir.findOrCreateChildData(this, SdkConstants.FN_BUILD_GRADLE);
        }
        catch (IOException e) {
          cleanupAfterError();
        }
      });
    }

    private void writeBuildFile() {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try (OutputStream out = new BufferedOutputStream(buildFile.getOutputStream(this))) {
          out.write("buildscript {}".getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ex) {
          cleanupAfterError();
        }
      });
    }

    private void addIncludeStatement() {
      if (hasIncludeFlutterModuleStatement) {
        return;
      }
      String originalContent = readSettingsFile();
      if (errorDuringOperation.get()) return;
      String newContent = addStatements(originalContent);
      //if (errorDuringOperation.get()) return;
      writeSettingsFile(newContent, originalContent);
    }

    private String readSettingsFile() {
      inSameDir = flutterModuleDir.getParent().equals(projectRoot);
      pathToModule = FileUtilRt.getRelativePath(new File(projectRoot.getPath()), flutterModuleRoot);
      try {
        requireNonNull(pathToModule);
        requireNonNull(settingsFile);
        @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed", "resource"})
        BufferedInputStream str = new BufferedInputStream(settingsFile.getInputStream());
        return FileUtil.loadTextAndClose(new InputStreamReader(str, CharsetToolkit.UTF8_CHARSET));
      }
      catch (NullPointerException | IOException e) {
        cleanupAfterError();
      }
      return "";
    }

    public String addStatements(String originalContent) {
      StringBuilder content = new StringBuilder();
      content.append(originalContent);
      content.append('\n');
      content.append("include ':");
      content.append(flutterModuleName);
      content.append("'\n");
      // project(':flutter').projectDir = new File('pathToModule')
      if (!inSameDir) {
        content.append("project(':");
        content.append(flutterModuleName);
        content.append("').projectDir = new File('");
        content.append(FileUtil.normalize(pathToModule));
        content.append("')\n");
      }
      return content.toString();
    }

    private void writeSettingsFile(String newContent, String originalContent) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try (OutputStream out = new BufferedOutputStream(settingsFile.getOutputStream(this))) {
          out.write(newContent.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ex) {
          cleanupAfterError();
          try (OutputStream out = new BufferedOutputStream(settingsFile.getOutputStream(this))) {
            out.write(originalContent.getBytes(StandardCharsets.UTF_8));
          }
          catch (IOException ignored) {
          }
        }
      });
    }

    private void cleanupAfterError() {
      errorDuringOperation.set(true);
      addCoeditTransformedProject(project);
      try {
        if (buildFile != null) {
          buildFile.delete(this);
        }
      }
      catch (IOException ignored) {
      }
    }
  }
}

class SdkConstants {
  /**
   * An SDK Project's build.gradle file
   */
  public static final String FN_BUILD_GRADLE = "build.gradle";

  /**
   * An SDK Project's settings.gradle file
   */
  public static final String FN_SETTINGS_GRADLE = "settings.gradle";
}
