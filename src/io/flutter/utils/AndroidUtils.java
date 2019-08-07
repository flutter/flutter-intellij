/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.java.IKeywordElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

// based on: org.jetbrains.android.util.AndroidUtils
public class AndroidUtils {

  private static final Lexer JAVA_LEXER = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5);

  // Flutter internal implementation dependencies.
  private static String FLUTTER_MODULE_NAME = ":flutter:";
  private static String FLUTTER_TASK_PREFIX = "compileflutter";


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
  private static String validateName(String name, boolean requiresSeparator) {
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

  public static boolean isAndroidProject(@Nullable Project project) {
    // Note: IntelliJ does not set the project type. When a Flutter module is added to an
    // IntelliJ-created Android project we need to set it. We need to allow an alternative
    // name. GradleResourceCompilerConfigurationGenerator depends on "Android".
    // TODO(messick) Recognize native Android Studio and IntelliJ Android projects.
    ProjectType projectType = ProjectTypeService.getProjectType(project);
    return projectType != null && "Android".equals(projectType.getId());
  }

  public static void addGradleListeners(@NotNull Project project) {
    GradleSyncState.subscribe(project, new GradleSyncListener() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
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
    GradleBuildState.subscribe(project, new GradleBuildListener.Adapter() {
      @Override
      public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
        String ideaName = FLUTTER_MODULE_NAME.substring(1, FLUTTER_MODULE_NAME.length() - 1);
        boolean mayNeedSync = false;
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          if (module.getName().equals(ideaName)) {
            mayNeedSync = true;
            break;
          }
        }
        if (!mayNeedSync) return;
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          if (FlutterModuleUtils.declaresFlutter(module)) {
            // untested
            if (isVanillaAddToApp(project, module.getModuleFile(), module.getName())) {
              GradleSyncInvoker.getInstance().requestProjectSync(
                project,
                GradleSyncInvoker.Request.userRequest());
              return;
            }
          }
        }
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          if (module.getName().equals(ideaName)) {

          }
        }
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
    if (dir.findChild(".ios") == null && dir.findChild("ios") == null) {
      return false;
    }
    if (dir.findChild(("build.gradle")) == null) {
      return true;
    }
    dir = dir.getParent();
    VirtualFile settings = dir.findChild("settings.gradle");
    if (settings == null) {
      return true;
    }
    // Check settings for "include :name".
    GradleSettingsFile parsedSettings =
      BuildModelContext.create(project).getOrCreateSettingsFile(project);
    if (parsedSettings == null) {
      return false;
    }
    Map<String, GradleDslElement> elements = parsedSettings.getPropertyElements();
    GradleDslElement includes = elements.get("include");
    if (includes == null) {
      return false;
    }
    for (GradleDslElement include : includes.getChildren()) {
      PsiElement expr = ((GradleDslLiteral)include).getExpression();
      if (expr == null) {
        return false;
      }
      String text = expr.getText();
      if (name.equals(text.substring(2, text.length() - 1))) {
        return true;
      }
    }
    return false;
  }

  // The project is an Android project that contains a Flutter module.
  private static void enableCoEditing(@NotNull Project project) {
    // find flutter module and use its module file, not the project's
    if (!isVanillaAddToApp(project, project.getProjectFile(), project.getName())) return;
    GradleSettingsFile parsedSettings =
      BuildModelContext.create(project).getOrCreateSettingsFile(project);
    if (parsedSettings == null) {
      return;
    }
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
}
