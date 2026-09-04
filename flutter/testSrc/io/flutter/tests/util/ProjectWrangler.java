/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.tests.util;

import static com.android.tools.idea.util.ToolWindows.activateProjectView;
import static com.intellij.ide.impl.ProjectUtil.focusProjectWindow;
import static com.intellij.openapi.fileChooser.impl.FileChooserUtil.setLastOpenedFile;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.ExceptionUtil.rethrowUnchecked;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.platform.templates.github.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;

/**
 * ProjectWrangler encapsulates the project operations required by GUI tests.
 * The testData directory must be identified as a test resource in the Project
 * Structure dialog.
 * <p>
 * Sample projects are found in $MODULE_NAME/testData/PROJECT_DIR
 * During a test build everything in testData (but NOT testData itself) is
 * copied to the working directory under out/test. When a sample project is
 * opened or imported it is first copied to a temp directory, then opened.
 * <p>
 * Opening a project uses it as-is. Importing a project first deletes IntelliJ
 * meta-data, like .idea and *.iml.
 */
@SuppressWarnings("deprecation")
public class ProjectWrangler {

  // Name of the module that defines GUI tests
  public static final String MODULE_NAME = "flutter-studio";

  // Name of the directory under testData where test projects are hosted during testing
  public static final String PROJECT_DIR = "flutter_projects";

  private static final String SRC_ZIP_NAME = "src.zip";

  @NotNull final private String myTestDirectory;

  public ProjectWrangler(@NotNull String dirName) {
    myTestDirectory = dirName;
  }

  public void openProject(@NotNull VirtualFile selectedFile) {
    VirtualFile projectFolder = findProjectFolder(selectedFile);
    try {
      doOpenProject(projectFolder);
    }
    catch (Throwable e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        rethrowUnchecked(e);
      }
      showErrorDialog(e.getMessage(), "Open Project");
      getLogger().error(e);
    }
  }

  @NotNull
  private Logger getLogger() {
    return Logger.getInstance(getClass());
  }

  public File setUpProject(@NotNull String projectDirName, boolean isImport) throws IOException {
    File projectPath = copyProjectBeforeOpening(projectDirName);
    if (isImport) {
      cleanUpProjectForImport(projectPath);
    }
    return projectPath;
  }

  public File copyProjectBeforeOpening(@NotNull String projectDirName) throws IOException {
    File masterProjectPath = getMasterProjectDirPath(projectDirName);

    File projectPath = getTestProjectDirPath(projectDirName);
    if (projectPath.isDirectory()) {
      FileUtilRt.delete(projectPath);
    }
    // If masterProjectPath contains a src.zip file, unzip the file to projectPath.
    // Otherwise, copy the whole directory to projectPath.
    File srcZip = new File(masterProjectPath, SRC_ZIP_NAME);
    if (srcZip.exists() && srcZip.isFile()) {
      ZipUtil.unzip(null, projectPath, srcZip, null, null, true);
    }
    else {
      FileUtil.copyDir(masterProjectPath, projectPath);
    }
    return projectPath;
  }

  @NotNull
  private File getTestProjectDirPath(@NotNull String projectDirName) {
    assert (myTestDirectory != null);
    return new File(GuiTests.getProjectCreationDirPath(myTestDirectory), projectDirName);
  }

  public void cleanUpProjectForImport(@NotNull File projectPath) {
    File dotIdeaFolderPath = new File(projectPath, Project.DIRECTORY_STORE_FOLDER);
    if (dotIdeaFolderPath.isDirectory()) {
      File modulesXmlFilePath = new File(dotIdeaFolderPath, "modules.xml");
      if (modulesXmlFilePath.isFile()) {
        SAXBuilder saxBuilder = new SAXBuilder();
        try {
          Document document = saxBuilder.build(modulesXmlFilePath);
          XPath xpath = XPath.newInstance("//*[@fileurl]");
          //noinspection unchecked
          List<Element> modules = xpath.selectNodes(document);
          int urlPrefixSize = "file://$PROJECT_DIR$/".length();
          for (Element module : modules) {
            String fileUrl = module.getAttributeValue("fileurl");
            if (!StringUtil.isEmpty(fileUrl)) {
              String relativePath = toSystemDependentName(fileUrl.substring(urlPrefixSize));
              File imlFilePath = new File(projectPath, relativePath);
              if (imlFilePath.isFile()) {
                FileUtilRt.delete(imlFilePath);
              }
              // It is likely that each module has a "build" folder. Delete it as well.
              File buildFilePath = new File(imlFilePath.getParentFile(), "build");
              if (buildFilePath.isDirectory()) {
                FileUtilRt.delete(buildFilePath);
              }
            }
          }
        }
        catch (Throwable ignored) {
          // if something goes wrong, just ignore. Most likely it won't affect project import in any way.
        }
      }
      FileUtilRt.delete(dotIdeaFolderPath);
    }
  }

  @NotNull
  private static File getMasterProjectDirPath(@NotNull String projectDirName) {
    return new File(ProjectWrangler.getTestProjectsRootDirPath(), projectDirName);
  }

  @NotNull
  private static File getTestProjectsRootDirPath() {
    // It is important that the testData directory be marked as a test resource so its content is copied to out/test dir
    String testDataPath = PathManager.getHomePathFor(ProjectWrangler.class);
    // "out/test" is defined by IntelliJ but we may want to change the module or root dir of the test projects.
    testDataPath = Paths.get(testDataPath, "out", "test", MODULE_NAME).toString();
    testDataPath = toCanonicalPath(toSystemDependentName(testDataPath));
    return new File(testDataPath, PROJECT_DIR);
  }

  @NotNull
  private static VirtualFile findProjectFolder(@NotNull VirtualFile selectedFile) {
    return selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
  }

  private static void afterProjectOpened(@NotNull VirtualFile projectFolder, @NotNull Project project) {
    TransactionGuard.getInstance().submitTransactionLater(project, () -> {
      setLastOpenedFile(project, projectFolder);
      focusProjectWindow(project, false);
      activateProjectView(project);
    });
  }

  private static void doOpenProject(VirtualFile baseDir) {
    // Open the project window.
    EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
    Project project = PlatformProjectOpenProcessor.doOpenProject(baseDir, null, -1, null, options);
    afterProjectOpened(baseDir, project);
  }
}
