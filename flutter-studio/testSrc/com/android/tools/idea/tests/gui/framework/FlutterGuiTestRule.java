/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework;

import static com.android.testutils.TestUtils.getWorkspaceRoot;
import static com.android.testutils.TestUtils.runningFromBazel;
import static com.android.tools.idea.tests.gui.framework.GuiTests.setUpDefaultProjectCreationLocationPath;
import static com.google.common.truth.TruthJUnit.assume;
import static com.intellij.openapi.util.io.FileUtil.sanitizeFileName;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.reflect.core.Reflection.type;

import com.android.tools.idea.tests.gui.framework.fixture.FlutterFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterWelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
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
@SuppressWarnings({"Duplicates", "unused", "deprecation"})
// Adapted from com.android.tools.idea.tests.gui.framework.GuiTestRule
public class FlutterGuiTestRule implements TestRule {

  /**
   * Hack to solve focus issue when running with no window manager
   */
  private static final boolean HAS_EXTERNAL_WINDOW_MANAGER = Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH);
  /* By nesting a pair of timeouts (one around just the test, one around the entire rule chain), we ensure that Rule code executing
   * before/after the test gets a chance to run, while preventing the whole rule chain from running forever.
   */
  private static final int DEFAULT_TEST_TIMEOUT_MINUTES = 3;
  private final RobotTestRule myRobotTestRule = new RobotTestRule();
  private final LeakCheck myLeakCheck = new LeakCheck();
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
  private FlutterFrameFixture myIdeFrameFixture;
  @Nullable private String myTestDirectory;
  private Timeout myInnerTimeout = new DebugFriendlyTimeout(DEFAULT_TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES);
  private Timeout myOuterTimeout = new DebugFriendlyTimeout(DEFAULT_TEST_TIMEOUT_MINUTES + 2, TimeUnit.MINUTES);
  private IdeFrameFixture myOldIdeFrameFixture;

  public FlutterGuiTestRule withLeakCheck() {
    myLeakCheck.setEnabled(true);
    return this;
  }

  @NotNull
  @Override
  public Statement apply(final Statement base, final Description description) {
    // TODO(messick) Update this.
    RuleChain chain = RuleChain.emptyRuleChain()
      .around(new LogStartAndStop())
      .around(myRobotTestRule)
      .around(myOuterTimeout) // Rules should be inside this timeout when possible
      .around(new IdeControl(myRobotTestRule::getRobot))
      .around(new BlockReloading())
      .around(new BazelUndeclaredOutputs())
      .around(myLeakCheck)
      .around(new IdeHandling())
      .around(new ScreenshotOnFailure())
      .around(myInnerTimeout);

    // Perf logging currently writes data to the Bazel-specific TEST_UNDECLARED_OUTPUTS_DIR. Skipp logging if running outside of Bazel.
    if (runningFromBazel()) {
      chain = chain.around(new GuiPerfLogger(description));
    }

    return chain.apply(base, description);
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

    // Compute the workspace root before any IDE code starts messing with user.dir:
    getWorkspaceRoot();

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
    errors.addAll(checkForPopupMenus());
    errors.addAll(checkForModalDialogs());
    errors.addAll(thrownFromRunning(this::tearDownProject));
    if (!HAS_EXTERNAL_WINDOW_MANAGER) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(myGlobalFocusListener);
    }
    errors.addAll(
      FluentIterable
        .from(GuiTests.fatalErrorsFromIde())
        // A hack to allow tests to pass despite duplicate JavaScript plugins.
        // TODO(messick) Fix the JDK so this isn't required.
        .filter(error -> !error.getCause().getMessage().contains("Duplicate plugin id:JavaScript")).toList());
    fixMemLeaks();
    return errors.build();
  }

  private List<AssertionError> checkForModalDialogs() {
    List<AssertionError> errors = new ArrayList<>();
    // We close all modal dialogs left over, because they block the AWT thread and could trigger a deadlock in the next test.
    Dialog modalDialog;

    // Loop can be infinite loop when a modal dialog opens itself again after closing.
    // Prevent infinite loop without a timeout
    long startTime = System.currentTimeMillis();
    long endTime = startTime + TimeUnit.SECONDS.toMillis(10);
    while ((modalDialog = getActiveModalDialog()) != null && System.currentTimeMillis() < endTime) {
      robot().close(modalDialog);
      errors.add(new AssertionError(
        String.format("Modal dialog showing: %s with title '%s'", modalDialog.getClass().getName(), modalDialog.getTitle())));
    }
    if (System.currentTimeMillis() >= endTime) {
      errors.add(new AssertionError("Potential modal dialog infinite loop"));
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

  private void fixMemLeaks() {
    myIdeFrameFixture = null;

    // Work-around for https://youtrack.jetbrains.com/issue/IDEA-153492
    Class<?> keyboardManagerType = type("javax.swing.KeyboardManager").load();
    Object manager = method("getCurrentManager").withReturnType(Object.class).in(keyboardManagerType).invoke();
    field("componentKeyStrokeMap").ofType(Hashtable.class).in(manager).get().clear();
    field("containerMap").ofType(Hashtable.class).in(manager).get().clear();
  }

  public FlutterFrameFixture importSimpleApplication() throws IOException {
    return importProject("simple_app");
  }

  public FlutterFrameFixture openSimpleApplication() throws IOException {
    return openProject("simple_app");
  }

  public FlutterFrameFixture importProject(@NotNull String name) throws IOException {
    return importProjectAndWaitForProjectSyncToFinish(name);
  }

  public FlutterFrameFixture openProject(@NotNull String name) throws IOException {
    return openProjectAndWaitForProjectSyncToFinish(name);
  }

  public FlutterFrameFixture importProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName) throws IOException {
    importOrOpenProject(projectDirName, true).waitForProjectSyncToFinish();
    return ideFrame();
  }

  public FlutterFrameFixture openProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName) throws IOException {
    importOrOpenProject(projectDirName, false).waitForProjectSyncToFinish();
    return ideFrame();
  }

  public FlutterFrameFixture importOrOpenProject(@NotNull String projectDirName, boolean isImport) throws IOException {
    ProjectWrangler wrangler = new ProjectWrangler(myTestDirectory);
    VirtualFile toSelect = VfsUtil.findFileByIoFile(wrangler.setUpProject(projectDirName, isImport), true);
    ApplicationManager.getApplication().invokeAndWait(() -> wrangler.openProject(toSelect));

    Wait.seconds(5).expecting("Project to be open").until(() -> ProjectManager.getInstance().getOpenProjects().length != 0);
    // TODO(messick) Find a way to start the IDE without the tip-of-the-day showing -- this is flaky, fails if dialog has focus.
    ideFrame().dismissTipDialog();
    // After the project is opened there will be an indexing and an analysis phase, and these can happen in any order.
    // Waiting for indexing to finish, makes sure analysis will start next or all analysis was done already.
    GuiTests.waitForProjectIndexingToFinish(ProjectManager.getInstance().getOpenProjects()[0]);
    return ideFrame();
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

  @NotNull
  public IdeFrameFixture baseIdeFrame() {
    if (myOldIdeFrameFixture == null || myOldIdeFrameFixture.isClosed()) {
      // This call to find() creates a new IdeFrameFixture object every time. Each of these Objects creates a new gradleProjectEventListener
      // and registers it with GradleSyncState. This keeps adding more and more listeners, and the new recent listeners are only updated
      // with gradle State when that State changes. This means the listeners may have outdated info.
      myOldIdeFrameFixture = IdeFrameFixture.find(robot());
      myOldIdeFrameFixture.requestFocusIfLost();
    }
    return myOldIdeFrameFixture;
  }

  public FlutterGuiTestRule withTimeout(long timeout, @NotNull TimeUnit timeUnits) {
    myInnerTimeout = new Timeout(timeout, timeUnits);
    myOuterTimeout = new Timeout(timeUnits.toSeconds(timeout) + 120, TimeUnit.SECONDS);
    return this;
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

  private class IdeHandling implements TestRule {
    @NotNull
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          if (!runningFromBazel()) {
            // when state can be bad from previous tests, check and skip in that case
            assume().that(GuiTests.fatalErrorsFromIde()).named("IDE errors").isEmpty();
            assumeOnlyWelcomeFrameShowing();
          }
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
