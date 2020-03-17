/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework;

import static com.android.tools.idea.tests.gui.framework.GuiTests.refreshFiles;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.io.Files.asCharSource;
import static com.intellij.openapi.util.io.FileUtil.sanitizeFileName;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.reflect.core.Reflection.type;

import com.android.testutils.TestUtils;
import com.android.tools.idea.tests.gui.framework.aspects.AspectsAgentLogger;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterWelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.testGuiFramework.impl.GuiTestThread;
import com.intellij.testGuiFramework.remote.transport.RestartIdeMessage;
import io.flutter.tests.util.ProjectWrangler;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

/**
 * A GUI test rule is used to drive GUI tests. It provides access to top-level
 * UI elements, such as dialogs, IDE frame, and welcome screen (when no projects
 * are open).
 * <p>
 * For example:
 * FlutterGuiTestRule myGuiTest = new FlutterGuiTestRule();
 * WizardUtils.createNewApplication(myGuiTest);
 * FlutterFrameFixture ideFrame = myGuiTest.ideFrame();
 * EditorFixture editor = ideFrame.getEditor();
 * editor.waitUntilErrorAnalysisFinishes();
 * ...
 * <p>
 * {@link TestRule}s can do everything that could be done previously with
 * methods annotated with {@link org.junit.Before},
 * {@link org.junit.After}, {@link org.junit.BeforeClass}, or
 * {@link org.junit.AfterClass}, but they are more powerful, and more easily
 * shared between projects and classes.
 */
@SuppressWarnings({"UnusedReturnValue", "Duplicates", "unused"})
// Adapted from com.android.tools.idea.tests.gui.framework.GuiTestRule
// Changes
// Changed field type: IdeFrameFixture -> FlutterFrameFixture
// Changed method return type: IdeFrameFixture -> FlutterFrameFixture
// Delete line containing NpwControl, which performs an update for Gradle based on finding build.gradle
// Change type ref: WelcomeFrameFixture -> FlutterWelcomeFrameFixture (but not in restartIdeIfWelcomeFrameNotShowing())
// Rewrite project open and import methods
// Add TestUtils.getWorkspaceRoot(); to setUp()
public class FlutterGuiTestRule implements TestRule {

  /** Hack to solve focus issue when running with no window manager */
  private static final boolean HAS_EXTERNAL_WINDOW_MANAGER = Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH);

  private FlutterFrameFixture myIdeFrameFixture;
  @Nullable private String myTestDirectory;
  private boolean mySetNdkPath = false;

  private final RobotTestRule myRobotTestRule = new RobotTestRule();
  private final LeakCheck myLeakCheck = new LeakCheck();

  /* By nesting a pair of timeouts (one around just the test, one around the entire rule chain), we ensure that Rule code executing
   * before/after the test gets a chance to run, while preventing the whole rule chain from running forever.
   */
  private static final int DEFAULT_TEST_TIMEOUT_MINUTES = 3;
  private Timeout myInnerTimeout = new DebugFriendlyTimeout(DEFAULT_TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES).withThreadDumpOnTimeout();
  private Timeout myOuterTimeout = new DebugFriendlyTimeout(DEFAULT_TEST_TIMEOUT_MINUTES + 2, TimeUnit.MINUTES);

  private final PropertyChangeListener myGlobalFocusListener = e -> {
    Object oldValue = e.getOldValue();
    if ("permanentFocusOwner".equals(e.getPropertyName()) && oldValue instanceof Component && e.getNewValue() == null) {
      Window parentWindow = oldValue instanceof Window ? (Window)oldValue : SwingUtilities.getWindowAncestor((Component)oldValue);
      if (parentWindow instanceof Dialog && ((Dialog)parentWindow).isModal()) {
        Container parent = parentWindow.getParent();
        if (parent != null && parent.isVisible()) {
          parent.requestFocus();
        }
      }
    }
  };

  public FlutterGuiTestRule withLeakCheck() {
    myLeakCheck.setEnabled(true);
    return this;
  }

  public FlutterGuiTestRule settingNdkPath() {
    mySetNdkPath = true;
    return this;
  }

  @NotNull
  @Override
  public Statement apply(final Statement base, final Description description) {
    RuleChain chain = RuleChain.emptyRuleChain()
      .around(new AspectsAgentLogger())
      .around(new LogStartAndStop())
      .around(myRobotTestRule)
      .around(myOuterTimeout) // Rules should be inside this timeout when possible
      .around(new IdeControl(myRobotTestRule::getRobot))
      .around(new BlockReloading())
      .around(new BazelUndeclaredOutputs())
      .around(myLeakCheck)
      .around(new FlutterGuiTestRule.IdeHandling())
      .around(new ScreenshotOnFailure())
      .around(myInnerTimeout);

    // Perf logging currently writes data to the Bazel-specific TEST_UNDECLARED_OUTPUTS_DIR. Skipp logging if running outside of Bazel.
    if (TestUtils.runningFromBazel()) {
      chain = chain.around(new GuiPerfLogger(description));
    }

    return chain.apply(base, description);
  }

  private class IdeHandling implements TestRule {
    @NotNull
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          if (!TestUtils.runningFromBazel()) {
            restartIdeIfWelcomeFrameNotShowing();
          }
          setUp(description.getMethodName());
          List<Throwable> errors = new ArrayList<>();
          try {
            base.evaluate();
          } catch (MultipleFailureException e) {
            errors.addAll(e.getFailures());
          } catch (Throwable e) {
            errors.add(e);
          } finally {
            try {
              boolean hasTestPassed = errors.isEmpty();
              errors.addAll(tearDown());  // shouldn't throw, but called inside a try-finally for defense in depth
              if (hasTestPassed && !errors.isEmpty()) { // If we get a problem during tearDown, take a snapshot.
                new ScreenshotOnFailure().failed(errors.get(0), description);
              }
            } finally {
              //noinspection ThrowFromFinallyBlock; assertEmpty is intended to throw here
              MultipleFailureException.assertEmpty(errors);
            }
          }
        }
      };
    }
  }

  private void restartIdeIfWelcomeFrameNotShowing() {
    boolean welcomeFrameNotShowing = false;
    try {
      WelcomeFrameFixture.find(robot());
    } catch (WaitTimedOutError e) {
      welcomeFrameNotShowing = true;
    }
    if (welcomeFrameNotShowing || GuiTests.windowsShowing().size() != 1) {
      GuiTestThread.Companion.getClient().send(new RestartIdeMessage());
    }
  }

  private void setUp(@Nullable String methodName) {
    myTestDirectory = methodName != null ? sanitizeFileName(methodName) : null;
    GeneralSettings.getInstance().setReopenLastProject(false);
    GeneralSettings.getInstance().setShowTipsOnStartup(false);
    GuiTests.setUpDefaultProjectCreationLocationPath(myTestDirectory);
    GuiTests.setIdeSettings();
    GuiTests.setUpSdks();

    // Compute the workspace root before any IDE code starts messing with user.dir:
    TestUtils.getWorkspaceRoot();

    if (!HAS_EXTERNAL_WINDOW_MANAGER) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(myGlobalFocusListener);
    }
  }

  private static ImmutableList<Throwable> thrownFromRunning(Runnable r) {
    try {
      r.run();
      return ImmutableList.of();
    }
    catch (Throwable e) {
      return ImmutableList.of(e);
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
    errors.addAll(checkForPopupMenus());
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
      errors.add(new AssertionError(
        String.format(
          "Modal dialog %s: %s with title '%s'",
          modalDialog.isShowing() ? "showing" : "not showing",
          modalDialog.getClass().getName(),
          modalDialog.getTitle())));
      if (!modalDialog.isShowing()) break; // this assumes when the active dialog is not showing, none are showing
      robot().close(modalDialog);
    }
    return errors;
  }

  private List<AssertionError> checkForPopupMenus() {
    List<AssertionError> errors = new ArrayList<>();

    // Close all opened popup menu items (File > New > etc) before continuing.
    Collection<JPopupMenu> popupMenus = robot().finder().findAll(Matchers.byType(JPopupMenu.class).andIsShowing());
    if (!popupMenus.isEmpty()) {
      errors.add(new AssertionError(String.format("%d Popup Menus left open", popupMenus.size())));
      for (int i = 0; i <= popupMenus.size(); i++) {
        robot().pressAndReleaseKey(KeyEvent.VK_ESCAPE);
      }
    }
    return errors;
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

  private void fixMemLeaks() {
    myIdeFrameFixture = null;

    // Work-around for https://youtrack.jetbrains.com/issue/IDEA-153492
    Class<?> keyboardManagerType = type("javax.swing.KeyboardManager").load();
    Object manager = method("getCurrentManager").withReturnType(Object.class).in(keyboardManagerType).invoke();
    field("componentKeyStrokeMap").ofType(Hashtable.class).in(manager).get().clear();
    field("containerMap").ofType(Hashtable.class).in(manager).get().clear();
  }

  @NotNull
  public Project openProject(@NotNull String projectDirName) throws Exception {
    File projectDir = copyProjectBeforeOpening(projectDirName);
    VirtualFile fileToSelect = VfsUtil.findFileByIoFile(projectDir, true);
    ProjectManager.getInstance().loadAndOpenProject(fileToSelect.getPath());

    Wait.seconds(5).expecting("Project to be open").until(() -> ProjectManager.getInstance().getOpenProjects().length == 1);

    Project project = ProjectManager.getInstance().getOpenProjects()[0];
    GuiTests.waitForProjectIndexingToFinish(project);
    ideFrame().updateToolbars();
    return project;
  }

  public FlutterFrameFixture importSimpleApplication() throws IOException {
    return importProjectAndWaitForProjectSyncToFinish("SimpleApplication");
  }

  public FlutterFrameFixture importMultiModule() throws IOException {
    return importProjectAndWaitForProjectSyncToFinish("MultiModule");
  }

  public FlutterFrameFixture importProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName) throws IOException {
    importProject(projectDirName);
    return ideFrame().waitForGradleProjectSyncToFinish();
  }

  public FlutterFrameFixture importProject(@NotNull String projectDirName) throws IOException {
    File projectDir = setUpProject(projectDirName);
    return openProject(projectDir);
  }

  @NotNull
  public FlutterFrameFixture openProject(@NotNull File projectDir) {
    ApplicationManager.getApplication().invokeAndWait(() -> ProjectUtil.openOrImport(projectDir.getAbsolutePath(), null, true));

    Wait.seconds(5).expecting("Project to be open").until(() -> ProjectManager.getInstance().getOpenProjects().length != 0);

    // After the project is opened there will be an Index and a Gradle Sync phase, and these can happen in any order.
    // Waiting for indexing to finish, makes sure Sync will start next or all Sync was done already.
    GuiTests.waitForProjectIndexingToFinish(ProjectManager.getInstance().getOpenProjects()[0]);
    return ideFrame();
  }

  /**
   * Sets up a project before using it in a UI test:
   * <ul>
   * <li>Makes a copy of the project in testData/guiTests/newProjects (deletes any existing copy of the project first.) This copy is
   * the one the test will use.</li>
   * <li>Deletes .idea directory, .iml files and build directories, if the project will be imported.</li>
   * </ul>
   *
   * @param projectDirName             the name of the project's root directory. Tests are located in testData/flutter_projects.
   * @throws IOException if an unexpected I/O error occurs.
   */
  public File setUpProject(@NotNull String projectDirName) throws IOException {
    File projectPath = copyProjectBeforeOpening(projectDirName);

    cleanUpProjectForImport(projectPath);
    refreshFiles();
    return projectPath;
  }

  public File copyProjectBeforeOpening(@NotNull String projectDirName) throws IOException {
    File masterProjectPath = getMasterProjectDirPath(projectDirName);

    File projectPath = getTestProjectDirPath(projectDirName);
    if (projectPath.isDirectory()) {
      FileUtilRt.delete(projectPath);
    }
    FileUtil.copyDir(masterProjectPath, projectPath);
    return projectPath;
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
          List<Element> modules = (List<Element>)xpath.selectNodes(document);
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
  public File getProjectPath(@NotNull String child) {
    return new File(ideFrame().getProjectPath(), child);
  }

  @NotNull
  public String getProjectFileText(@NotNull String fileRelPath) {
    try {
      return asCharSource(getProjectPath(fileRelPath), UTF_8).read();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
    myInnerTimeout = new Timeout(timeout, timeUnits);
    myOuterTimeout = new Timeout(timeUnits.toSeconds(timeout) + 120, TimeUnit.SECONDS);
    return this;
  }
}
