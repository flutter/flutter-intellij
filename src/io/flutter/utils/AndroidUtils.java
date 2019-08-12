/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.gradle.project.build.GradleBuildListener;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
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
import com.intellij.openapi.module.ModuleManager;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.WeakList;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_GRADLEDEPENDENCY_ADDED;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

// based on: org.jetbrains.android.util.AndroidUtils
public class AndroidUtils {

  private static final Lexer JAVA_LEXER = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5);

  // Flutter internal implementation dependencies.
  private static String FLUTTER_MODULE_NAME = ":flutter:";
  private static String FLUTTER_TASK_PREFIX = "compileFlutterBuild";
  private static final WeakList<Project> IGNORED_PROJECTS = new WeakList<>();

  /**
   * Validates a potential package name and returns null if the package name is valid, and otherwise
   * returns a description for why it is not valid.
   * <p>
   * Note that Android package names are more restrictive than general Java package names;
   * we require at least two segments, limit the character set to [a-zA-Z0-9_] (Java allows any
   * {@link Character#isLetter(char)} and require that each segment start with a letter (Java allows
   * underscores at the beginning).
   * <p>
   * For details, see core/java/android/content/pm/PackageParser.java#validateName
   *
   * @param name the package name
   * @return null if the package is valid as an Android package name, and otherwise a description for why not
   */
  @Nullable
  public static String validateAndroidPackageName(@NotNull String name) {
    if (name.isEmpty()) {
      return "Package name is missing";
    }

    String packageManagerCheck = validateName(name, true);
    if (packageManagerCheck != null) {
      return packageManagerCheck;
    }

    // In addition, we have to check that none of the segments are Java identifiers, since
    // that will lead to compilation errors, which the package manager doesn't need to worry about
    // (the code wouldn't have compiled)
    int index = 0;
    while (true) {
      int index1 = name.indexOf('.', index);
      if (index1 < 0) {
        index1 = name.length();
      }
      String error = isReservedKeyword(name.substring(index, index1));
      if (error != null) return error;
      if (index1 == name.length()) {
        break;
      }
      index = index1 + 1;
    }

    return null;
  }

  @Nullable
  public static String isReservedKeyword(@NotNull String string) {
    Lexer lexer = JAVA_LEXER;
    lexer.start(string);
    if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) {
      if (lexer.getTokenType() instanceof IKeywordElementType) {
        return "Package names cannot contain Java keywords like '" + string + "'";
      }
      if (string.isEmpty()) {
        return "Package segments must be of non-zero length";
      }
      return string + " is not a valid identifier";
    }
    return null;
  }

  // This method is a copy of android.content.pm.PackageParser#validateName with the
  // error messages tweaked
  @Nullable
  private static String validateName(String name, @SuppressWarnings("SameParameterValue") boolean requiresSeparator) {
    final int N = name.length();
    boolean hasSep = false;
    boolean front = true;
    for (int i = 0; i < N; i++) {
      final char c = name.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        front = false;
        continue;
      }
      if ((c >= '0' && c <= '9') || c == '_') {
        if (!front) {
          continue;
        }
        else {
          if (c == '_') {
            return "The character '_' cannot be the first character in a package segment";
          }
          else {
            return "A digit cannot be the first character in a package segment";
          }
        }
      }
      if (c == '.') {
        hasSep = true;
        front = true;
        continue;
      }
      return "The character '" + c + "' is not allowed in Android application package names";
    }
    return hasSep || !requiresSeparator ? null : "The package must have at least one '.' separator";
  }

  /*
    Add-to-app notes:
    - The Flutter module must be added to the Android app via manual editing as in the add-to-app docs,
      or by using the forthcoming tool in Android Studio that generates a module and does the editing.
    - The Flutter module may be in a directory nested under the Android app or it may be in a different
      location entirely. IntelliJ supports both, and is moving toward the latter.
    - Enabling co-editing means converting the Flutter module to an Gradle module and adding it to the
      Android app as a sub-project.
        o If the parent of the Flutter module root directory is the Android app
          root directory, add "include ':flutterModule'" to the parent settings.gradle;
          otherwise something like what add-to-app does to define the :flutter module is needed.
        o Create a simple build.gradle in the Flutter module root directory.
        o Add Android-Gradle and Android facets to the Idea module of the Flutter module.
   */

  public static boolean isAndroidProject(@Nullable Project project) {
    // Note: IntelliJ does not set the project type. When a Flutter module is added to an
    // IntelliJ-created Android project we need to set it. We need to allow an alternative
    // name. GradleResourceCompilerConfigurationGenerator depends on "Android".
    // TODO(messick) Recognize both native Android Studio and IntelliJ Android projects.
    ProjectType projectType = ProjectTypeService.getProjectType(project);
    return projectType != null && "Android".equals(projectType.getId());
  }

  public static void addGradleListeners(@NotNull Project project) {
    if (isProjectIgnored(project)) {
      return;
    }
    if (FlutterUtils.isAndroidStudio()) {
      MessageBusConnection connection = project.getMessageBus().connect(project);
      connection.subscribe(GradleSyncState.GRADLE_SYNC_TOPIC, new GradleSyncListener() {
        @Override
        public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
          finish();
        }

        @Override
        public void syncSkipped(@NotNull Project project) {
          finish();
        }

        @Override
        public void sourceGenerationFinished(@NotNull Project project) {
          finish();
        }

        private void finish() {
          connection.disconnect();
        }
      });
    } else {
      GradleSyncState.subscribe(project, new GradleSyncListener() {
        @Override
        public void syncSucceeded(@NotNull Project project) {
          checkDartSupport(project);
          if (isProjectIgnored(project)) {
            return;
          }
          // After a Gradle sync has finished we check the tasks that were run to see if any belong to Flutter.
          Map<ProjectData, MultiMap<String, String>> tasks = getTasksMap(project);
          @NotNull String projectName = project.getName();
          for (ProjectData projectData : tasks.keySet()) {
            String dataName = projectData.getExternalName();
            if (projectName.equals(dataName)) {
              MultiMap<String, String> map = tasks.get(projectData);
              if (map.containsKey(FLUTTER_MODULE_NAME)) {
                if (map.get(FLUTTER_MODULE_NAME).parallelStream().anyMatch((x) -> x.startsWith(FLUTTER_TASK_PREFIX))) {
                  ApplicationManager.getApplication().invokeLater(() -> enableCoEditing(project));
                }
              }
            }
          }
        }
      });
    }
    GradleBuildState.subscribe(project, new GradleBuildListener.Adapter() {
      @Override
      public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
        if (isProjectIgnored(project)) {
          return;
        }
        // When an Androd project is re-opened it only does a sync if needed. We have to check
        // for signs that a Flutter module has been added and trigger a sync if so.
        String ideaName = FLUTTER_MODULE_NAME.substring(1, FLUTTER_MODULE_NAME.length() - 1);
        if (status == BuildStatus.SUCCESS) {
          if (context != null && context.getGradleTasks().contains(":flutter:generateDebugSources")) {
            Module module = FlutterUtils.findModuleNamed(project, "flutter");
            if (module != null && isVanillaAddToApp(project, module.getModuleFile(), module.getName())) {
              scheduleGradleSync(project);
              return;
            }
          }
        }
        boolean mayNeedSync = FlutterUtils.findModuleNamed(project, ideaName) != null;
        if (!mayNeedSync) return;
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          if (FlutterModuleUtils.declaresFlutter(module)) {
            // untested -- might occur if the module is added manually (or by AS module tool)
            if (isVanillaAddToApp(project, module.getModuleFile(), module.getName())) {
              scheduleGradleSync(project);
              return;
            }
          }
        }
        Module module = FlutterUtils.findFlutterGradleModule(project);
        if (module != null) {
          // This is what we expect from following the add-to-app docs.
          if (isVanillaAddToApp(project, module.getModuleFile(), module.getName())) {
            scheduleGradleSync(project);
          }
        }
      }
    });
  }

  public static void scheduleGradleSync(@NotNull Project project) {
    GradleSyncInvoker.getInstance().requestProjectSync(
      project,
      new GradleSyncInvoker.Request(TRIGGER_GRADLEDEPENDENCY_ADDED));
  }

  private static void checkDartSupport(@NotNull Project project) {
    Stream<Module> modules =
      Arrays.stream(FlutterModuleUtils.getModules(project)).filter(FlutterModuleUtils::declaresFlutter);
    modules.forEach((module) -> {
      if (!DartSdkLibUtil.isDartSdkEnabled(module)) {
        new EnableDartSupportForModule(module).run();
      }
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
    // Using VFS to check existence of build.gradle is returning false positives.
    if (doesBuildFileExist(dir)) {
      return true;
    }
    VirtualFile projectDir = FlutterUtils.getProjectRoot(project);
    VirtualFile settings = GradleUtil.getGradleSettingsFile(new File(projectDir.getPath()));
    if (settings == null) {
      return false;
    }
    // Check settings for "include :name".
    GradleSettingsFile parsedSettings =
      BuildModelContext.create(project).getSettingsFile(project);
    if (parsedSettings == null) {
      return false;
    }
    return findInclude(parsedSettings, name) == null;
  }

  private static boolean doesBuildFileExist(VirtualFile dir) {
    // Using VFS to check existence of build.gradle is returning false positives.
    return new File(dir.getPath(), SdkConstants.FN_BUILD_GRADLE).exists();
  }

  // The project is an Android project that contains a Flutter module.
  private static void enableCoEditing(@NotNull Project project) {
    IGNORED_PROJECTS.add(project); // This may not be a good idea.
    Module module = FlutterUtils.findFlutterGradleModule(project);
    if (module == null) return;
    VirtualFile moduleFile = module.getModuleFile();
    if (moduleFile == null) return;
    VirtualFile androidDir = moduleFile.getParent().getParent();
    VirtualFile flutterModuleDir = androidDir.getParent();
    String flutterModuleName = flutterModuleDir.getName();
    if (!isVanillaAddToApp(project, androidDir, flutterModuleName)) return;
    new CoEditHelper(project, flutterModuleDir).enable();
  }

  private static void disableGradleListeners(@NotNull Project project) {
    if (!project.isDisposed()) {
      IGNORED_PROJECTS.add(project);
    }
  }

  private static boolean isProjectIgnored(@NotNull Project project) {
    if (project.isDisposed()) {
      return true;
    }
    Iterator<Project> iter = IGNORED_PROJECTS.iterator();
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
    Map<ProjectData, MultiMap<String, String>> tasks = ContainerUtil.newLinkedHashMap();
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

  private static PsiElement findInclude(GradleSettingsFile parsedSettings, String name) {
    Map<String, GradleDslElement> elements = parsedSettings.getPropertyElements();
    GradleDslElement includes = elements.get("include");
    if (includes == null) {
      return null;
    }
    for (GradleDslElement include : includes.getChildren()) {
      PsiElement expr = ((GradleDslLiteral)include).getExpression();
      if (expr == null) {
        continue;
      }
      String text = expr.getText();
      if (name.equals(text.substring(2, text.length() - 1))) {
        return expr;
      }
    }
    return null;
  }

  private static class CoEditHelper {
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
        scheduleGradleSync(project);
      }
    }

    private boolean verifyEligibility() {
      GradleSettingsFile parsedSettings = BuildModelContext.create(project).getSettingsFile(project);
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
      try {
        buildFile = flutterModuleDir.findOrCreateChildData(this, SdkConstants.FN_BUILD_GRADLE);
      }
      catch (IOException e) {
        cleanupAfterError();
      }
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
      VirtualFile projectRoot = FlutterUtils.getProjectRoot(project);
      inSameDir = flutterModuleDir.getParent().equals(projectRoot);
      pathToModule = FileUtilRt.getRelativePath(new File(projectRoot.getPath()), flutterModuleRoot);
      try {
        Objects.requireNonNull(pathToModule);
        settingsFile = projectRoot.findOrCreateChildData(this, SdkConstants.FN_SETTINGS_GRADLE);
        Objects.requireNonNull(settingsFile);
        @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
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
        content.append(pathToModule);
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
      disableGradleListeners(project);
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
