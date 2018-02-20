/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterWelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.WaitTimedOutError;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.GuiTests.setUpDefaultProjectCreationLocationPath;
import static com.google.common.truth.TruthJUnit.assume;
import static com.intellij.openapi.util.io.FileUtil.sanitizeFileName;
import static org.fest.reflect.core.Reflection.*;

/**
 * A GUI test rule is used to drive GUI tests. It provides access to top-level
 * UI elements, such as dialogs, IDE frame, and welcome screen (when no projects
 * are open).
 *
 * For example:
 * FlutterGuiTestRule myGuiTest = new FlutterGuiTestRule();
 * WizardUtils.createNewApplication(myGuiTest);
 * FlutterFrameFixture ideFrame = myGuiTest.ideFrame();
 * EditorFixture editor = ideFrame.getEditor();
 * editor.waitUntilErrorAnalysisFinishes();
 * ...
 *
 * {@link TestRule}s can do everything that could be done previously with
 * methods annotated with {@link org.junit.Before},
 * {@link org.junit.After}, {@link org.junit.BeforeClass}, or
 * {@link org.junit.AfterClass}, but they are more powerful, and more easily
 * shared between projects and classes.
 */
@SuppressWarnings("Duplicates") // Adapted from com.android.tools.idea.tests.gui.framework.GuiTestRule
public class FlutterGuiTestRule implements TestRule {

  /**
   * Hack to solve focus issue when running with no window manager
   */
  private static final boolean HAS_EXTERNAL_WINDOW_MANAGER = Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH);
  private final RobotTestRule myRobotTestRule = new RobotTestRule();
  private final LeakCheck myLeakCheck = new LeakCheck();
  private final PropertyChangeListener myGlobalFocusListener = e -> {
    Object oldValue = e.getOldValue();
    if ("permanentFocusOwner".equals(e.getPropertyName()) && oldValue instanceof Component && e.getNewValue() == null) {
      Window parentWindow = oldValue instanceof Window ? (Window)oldValue : SwingUtilities.getWindowAncestor((Component)oldValue);
      if (parentWindow instanceof Dialog && ((Dialog)parentWindow).isModal()) {
        Container parent = parentWindow.getParent();
        if (parent != null && parent.isVisible()) {
          System.out.println("Focus Listener: Request focus!");
          parent.requestFocus();
        }
      }
    }
  };
  private FlutterFrameFixture myIdeFrameFixture;
  @Nullable private String myTestDirectory;
  private Timeout myTimeout = new Timeout(5, TimeUnit.MINUTES);

  private static ImmutableList<Throwable> thrownFromRunning(Runnable r) {
    try {
      r.run();
      return ImmutableList.of();
    }
    catch (Throwable e) {
      return ImmutableList.of(e);
    }
  }

  // Note: this works with a cooperating window manager that returns focus properly. It does not work on bare Xvfb.
  private static Dialog getActiveModalDialog() {
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (activeWindow instanceof Dialog) {
      Dialog dialog = (Dialog)activeWindow;
      if (dialog.getModalityType() == Dialog.ModalityType.APPLICATION_MODAL) {
        return dialog;
      }
    }
    return null;
  }

  public FlutterGuiTestRule withLeakCheck() {
    myLeakCheck.setEnabled(true);
    return this;
  }

  @NotNull
  @Override
  public Statement apply(final Statement base, final Description description) {
    return RuleChain.emptyRuleChain()
      .around(new BlockReloading())
      .around(myRobotTestRule)
      .around(myLeakCheck)
      .around(new IdeHandling())
      .around(new ScreenshotOnFailure())
      .around(myTimeout)
      .apply(base, description);
  }

  private void assumeOnlyWelcomeFrameShowing() {
    try {
      FlutterWelcomeFrameFixture.find(robot());
    }
    catch (WaitTimedOutError e) {
      throw new AssumptionViolatedException("didn't find welcome frame", e);
    }
    assume().that(GuiTests.windowsShowing()).named("windows showing").hasSize(1);
  }

  private void setUp(@NotNull String methodName) {
    myTestDirectory = methodName != null ? sanitizeFileName(methodName) : null;
    setUpDefaultProjectCreationLocationPath(myTestDirectory);
    GuiTests.setIdeSettings();
    GuiTests.setUpSdks();

    if (!HAS_EXTERNAL_WINDOW_MANAGER) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(myGlobalFocusListener);
    }
  }

  protected void tearDownProject() {
    if (!robot().finder().findAll(Matchers.byType(IdeFrameImpl.class).andIsShowing()).isEmpty()) {
      ideFrame().closeProject();
    }
    myIdeFrameFixture = null;
  }

  private ImmutableList<Throwable> tearDown() {
    ImmutableList.Builder<Throwable> errors = ImmutableList.builder();
    errors.addAll(thrownFromRunning(this::waitForBackgroundTasks));
    errors.addAll(checkForModalDialogs());
    errors.addAll(thrownFromRunning(this::tearDownProject));
    if (!HAS_EXTERNAL_WINDOW_MANAGER) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(myGlobalFocusListener);
    }
    errors.addAll(GuiTests.fatalErrorsFromIde());
    fixMemLeaks();
    return errors.build();
  }

  private List<AssertionError> checkForModalDialogs() {
    List<AssertionError> errors = new ArrayList<>();
    // We close all modal dialogs left over, because they block the AWT thread and could trigger a deadlock in the next test.
    Dialog modalDialog;
    while ((modalDialog = getActiveModalDialog()) != null) {
      robot().close(modalDialog);
      errors.add(new AssertionError(
        String.format("Modal dialog showing: %s with title '%s'", modalDialog.getClass().getName(), modalDialog.getTitle())));
    }
    return errors;
  }

  private void fixMemLeaks() {
    myIdeFrameFixture = null;

    // Work-around for https://youtrack.jetbrains.com/issue/IDEA-153492
    Class<?> keyboardManagerType = type("javax.swing.KeyboardManager").load();
    Object manager = method("getCurrentManager").withReturnType(Object.class).in(keyboardManagerType).invoke();
    field("componentKeyStrokeMap").ofType(Hashtable.class).in(manager).get().clear();
    field("containerMap").ofType(Hashtable.class).in(manager).get().clear();
  }

  public IdeaFrameFixture importProject(@NotNull String projectDirName) throws IOException {
    VirtualFile toSelect = VfsUtil.findFileByIoFile(setUpProject(projectDirName), true);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      /* TODO(messick): Open the project in a new window. */
    });
    waitForBackgroundTasks();
    return ideFrame();
  }

  /**
   * Sets up a project before using it in a UI test:
   * <ul>
   * <li>Makes a copy of the project in testData/guiTests/newProjects (deletes any existing copy of the project first.) This copy is
   * the one the test will use.</li>
   * <li>Creates a Gradle wrapper for the test project.</li>
   * <li>Updates the version of the Android Gradle plug-in used by the project, if applicable</li>
   * <li>Creates a local.properties file pointing to the Android SDK path specified by the system property (or environment variable)
   * 'ANDROID_HOME'</li>
   * <li>Copies over missing files to the .idea directory (if the project will be opened, instead of imported.)</li>
   * <li>Deletes .idea directory, .iml files and build directories, if the project will be imported.</li>
   * <p/>
   * </ul>
   *
   * @param projectDirName the name of the project's root directory. Tests are located in testData/guiTests.
   * @throws IOException if an unexpected I/O error occurs.
   */
  private File setUpProject(@NotNull String projectDirName) throws IOException {
    File projectPath = copyProjectBeforeOpening(projectDirName);

    updateLocalProperties(projectPath);
    cleanUpProjectForImport(projectPath);
    return projectPath;
  }

  public File copyProjectBeforeOpening(@NotNull String projectDirName) throws IOException {
    File masterProjectPath = getMasterProjectDirPath(projectDirName);

    File projectPath = getTestProjectDirPath(projectDirName);
    if (projectPath.isDirectory()) {
      FileUtilRt.delete(projectPath);
    }
    FileUtil.copyDir(masterProjectPath, projectPath);
    System.out.println(String.format("Copied project '%1$s' to path '%2$s'", projectDirName, projectPath.getPath()));
    return projectPath;
  }

  protected void updateLocalProperties(File projectPath) throws IOException {
    LocalProperties localProperties = new LocalProperties(projectPath);
    localProperties.setAndroidSdkPath(IdeSdks.getInstance().getAndroidSdkPath());
    localProperties.save();
  }

  @NotNull
  protected File getMasterProjectDirPath(@NotNull String projectDirName) {
    return new File(GuiTests.getTestProjectsRootDirPath(), projectDirName);
  }

  @NotNull
  protected File getTestProjectDirPath(@NotNull String projectDirName) {
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
              String relativePath = FileUtil.toSystemDependentName(fileUrl.substring(urlPrefixSize));
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

  public void waitForBackgroundTasks() {
    GuiTests.waitForBackgroundTasks(robot());
  }

  public Robot robot() {
    return myRobotTestRule.getRobot();
  }

  @NotNull
  public File getProjectPath() {
    return ideFrame().getProjectPath();
  }

  @NotNull
  public FlutterWelcomeFrameFixture welcomeFrame() {
    return FlutterWelcomeFrameFixture.find(robot());
  }

  @NotNull
  public FlutterFrameFixture ideFrame() {
    if (myIdeFrameFixture == null || myIdeFrameFixture.isClosed()) {
      // This call to find() creates a new IdeFrameFixture object every time. Each of these Objects creates a new gradleProjectEventListener
      // and registers it with GradleSyncState. This keeps adding more and more listeners, and the new recent listeners are only updated
      // with gradle State when that State changes. This means the listeners may have outdated info.
      myIdeFrameFixture = FlutterFrameFixture.find(robot());
      myIdeFrameFixture.requestFocusIfLost();
    }
    return myIdeFrameFixture;
  }

  public FlutterGuiTestRule withTimeout(long timeout, @NotNull TimeUnit timeUnits) {
    myTimeout = new Timeout(timeout, timeUnits);
    return this;
  }

  private class IdeHandling implements TestRule {
    @NotNull
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          System.out.println("Starting " + description.getDisplayName());
          assume().that(GuiTests.fatalErrorsFromIde()).named("IDE errors").isEmpty();
          assumeOnlyWelcomeFrameShowing();
          setUp(description.getMethodName());
          List<Throwable> errors = new ArrayList<>();
          try {
            base.evaluate();
          }
          catch (MultipleFailureException e) {
            errors.addAll(e.getFailures());
          }
          catch (Throwable e) {
            errors.add(e);
          }
          finally {
            try {
              boolean hasTestPassed = errors.isEmpty();
              errors.addAll(tearDown());  // shouldn't throw, but called inside a try-finally for defense in depth
              if (hasTestPassed && !errors.isEmpty()) { // If we get a problem during tearDown, take a snapshot.
                new ScreenshotOnFailure().failed(errors.get(0), description);
              }
            }
            finally {
              //noinspection ThrowFromFinallyBlock; assertEmpty is intended to throw here
              MultipleFailureException.assertEmpty(errors);
            }
          }
        }
      };
    }
  }
}
