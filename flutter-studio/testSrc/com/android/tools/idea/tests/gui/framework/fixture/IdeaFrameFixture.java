/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.Modules;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.collect.Lists;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.Platform;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.META_MASK;
import static junit.framework.Assert.assertNotNull;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertFalse;

@SuppressWarnings("Duplicates") // Adapted from IdeFrameFixture in uitest-framework module, due to private constructor.
public class IdeaFrameFixture extends ComponentFixture<IdeaFrameFixture, IdeFrameImpl> {
  @NotNull private final Modules myModules;
  @NotNull private final IdeFrameFixture myIdeFrameFixture; // Replaces 'this' when creating component fixtures.

  private EditorFixture myEditor;
  private boolean myIsClosed;

  IdeaFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target) {
    super(IdeaFrameFixture.class, robot, target);
    myIdeFrameFixture = IdeFrameFixture.find(robot);
    Project project = getProject();
    myModules = new Modules(project);

    Disposable disposable = new NoOpDisposable();
    Disposer.register(project, disposable);
  }

  @NotNull
  public static IdeaFrameFixture find(@NotNull final Robot robot) {
    return new IdeaFrameFixture(robot, GuiTests.waitUntilShowing(robot, Matchers.byType(IdeFrameImpl.class)));
  }

  @NotNull
  public File getProjectPath() {
    return new File(target().getProject().getBasePath());
  }

  @NotNull
  public List<String> getModuleNames() {
    List<String> names = Lists.newArrayList();
    for (Module module : getModuleManager().getModules()) {
      names.add(module.getName());
    }
    return names;
  }

  @NotNull
  public AndroidModuleModel getAndroidProjectForModule(@NotNull String name) {
    Module module = getModule(name);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.requiresAndroidModel()) {
      // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
      AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
      if (androidModel != null) {
        return androidModel;
      }
    }
    throw new AssertionError("Unable to find AndroidGradleModel for module " + quote(name));
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    return myModules.getModule(name);
  }

  public boolean hasModule(@NotNull String name) {
    return myModules.hasModule(name);
  }

  @NotNull
  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(getProject());
  }

  @NotNull
  public EditorFixture getEditor() {
    if (myEditor == null) {
      myEditor = new EditorFixture(robot(), myIdeFrameFixture);
    }

    return myEditor;
  }

  @NotNull
  public String getTitle() {
    return target().getTitle();
  }

  @NotNull
  public String getRevision() {
    String revisionReg = "\\d\\.\\d";
    String title = getTitle();
    Pattern pattern = Pattern.compile(revisionReg);
    Matcher matcher = pattern.matcher(title);
    if (matcher.find()) {
      return matcher.group();
    }
    else {
      throw new RuntimeException("Cannot find digital pattern like " + revisionReg);
    }
  }

  @NotNull
  public IdeaFrameFixture invokeProjectMakeAndSimulateFailure(@NotNull String failure) {
    Runnable failTask = () -> {
      throw new ExternalSystemException(failure);
    };
    ApplicationManager.getApplication().putUserData(GuiTestingService.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, failTask);
    selectProjectMakeAction();
    return this;
  }

  /**
   * Finds the Run button in the IDE interface.
   *
   * @return ActionButtonFixture for the run button.
   */
  @NotNull
  public ActionButtonFixture findDebugApplicationButton() {
    return findActionButtonByActionId("Debug");
  }

  @NotNull
  public ActionButtonFixture findRunApplicationButton() {
    return findActionButtonByActionId("Run");
  }

  @NotNull
  public ActionButtonFixture findApplyChangesButton() {
    return findActionButtonByText("Apply Changes");
  }

  @NotNull
  public ActionButtonFixture findAttachDebuggerToAndroidProcessButton() {
    return findActionButtonByText("Attach debugger to Android process");
  }

  public DeployTargetPickerDialogFixture debugApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findDebugApplicationButton().click();
    return DeployTargetPickerDialogFixture.find(robot());
  }

  public DeployTargetPickerDialogFixture runApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findRunApplicationButton().click();
    return DeployTargetPickerDialogFixture.find(robot());
  }

  @NotNull
  public IdeaFrameFixture stopApp() {
    return invokeMenuPath("Run", "Stop \'app\'");
  }

  @NotNull
  public IdeaFrameFixture stepOver() {
    return invokeMenuPath("Run", "Step Over");
  }

  @NotNull
  public IdeaFrameFixture smartStepInto() {
    return invokeMenuPath("Run", "Smart Step Into");
  }

  @NotNull
  public IdeaFrameFixture resumeProgram() {
    return invokeMenuPath("Run", "Resume Program");
  }

  @NotNull
  public RunToolWindowFixture getRunToolWindow() {
    return new RunToolWindowFixture(myIdeFrameFixture);
  }

  @NotNull
  public DebugToolWindowFixture getDebugToolWindow() {
    return new DebugToolWindowFixture(myIdeFrameFixture);
  }

  protected void selectProjectMakeAction() {
    invokeMenuPath("Build", "Make Project");
  }

  /**
   * Selects the item at {@code menuPath} and returns the result of {@code fixtureFunction} applied to this {@link IdeaFrameFixture}.
   */
  public <T> T openFromMenu(Function<IdeaFrameFixture, T> fixtureFunction, @NotNull String... menuPath) {
    getMenuFixture().invokeMenuPath(menuPath);
    return fixtureFunction.apply(this);
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public IdeaFrameFixture invokeMenuPath(@NotNull String... path) {
    getMenuFixture().invokeMenuPath(path);
    return this;
  }

  /**
   * Wait till an path is enabled then invokes the action. Used for menu options that might be disabled or not available at first
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public IdeaFrameFixture waitAndInvokeMenuPath(@NotNull String... path) {
    Wait.seconds(10).expecting("Wait until the path " + Arrays.toString(path) + " is ready.")
      .until(() -> getMenuFixture().isMenuPathEnabled(path));
    getMenuFixture().invokeMenuPath(path);
    return this;
  }

  @NotNull
  private MenuFixture getMenuFixture() {
    return Platform.isMacintosh() ? new MacMenuFixture(robot(), target()) : new MenuFixture(robot(), target());
  }

  @NotNull
  public FileFixture findExistingFileByRelativePath(@NotNull String relativePath) {
    VirtualFile file = findFileByRelativePath(relativePath, true);
    return new FileFixture(getProject(), file);
  }

  @Nullable
  @Contract("_, true -> !null")
  public VirtualFile findFileByRelativePath(@NotNull String relativePath, boolean requireExists) {
    //noinspection Contract
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));
    Project project = getProject();
    VirtualFile file = project.getBaseDir().findFileByRelativePath(relativePath);
    if (requireExists) {
      //noinspection Contract
      assertNotNull("Unable to find file with relative path " + quote(relativePath), file);
    }
    return file;
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  private ActionButtonFixture findActionButtonByText(@NotNull String text) {
    return ActionButtonFixture.findByText(text, robot(), target());
  }

  @NotNull
  public CapturesToolWindowFixture getCapturesToolWindow() {
    return new CapturesToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public BuildVariantsToolWindowFixture getBuildVariantsWindow() {
    return new BuildVariantsToolWindowFixture(myIdeFrameFixture);
  }

  @NotNull
  public MessagesToolWindowFixture getMessagesToolWindow() {
    return new MessagesToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public IdeSettingsDialogFixture openIdeSettings() {
    // Using invokeLater because we are going to show a *modal* dialog via API (instead of clicking a button, for example.) If we use
    // GuiActionRunner the test will hang until the modal dialog is closed.
    ApplicationManager.getApplication().invokeLater(
      () -> {
        Project project = getProject();
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ShowSettingsUtilImpl.getConfigurableGroups(project, true));
      });
    return IdeSettingsDialogFixture.find(robot());
  }

  @NotNull
  public ProjectViewFixture getProjectView() {
    return new ProjectViewFixture(myIdeFrameFixture);
  }

  @NotNull
  public Project getProject() {
    return target().getProject();
  }

  public FlutterWelcomeFrameFixture closeProject() {
    myIsClosed = true;
    return openFromMenu(FlutterWelcomeFrameFixture::find, "File", "Close Project");
  }

  public boolean isClosed() {
    return myIsClosed;
  }

  @NotNull
  public LibraryPropertiesDialogFixture showPropertiesForLibrary(@NotNull String libraryName) {
    return getProjectView().showPropertiesForLibrary(libraryName);
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), title);
  }

  @NotNull
  public DialogFixture findDialog(@NotNull String title) {
    return new DialogFixture(robot(), robot().finder().find(Matchers.byTitle(JDialog.class, title)));
  }

  @NotNull
  public DialogFixture waitForDialog(@NotNull String title) {
    return new DialogFixture(robot(), GuiTests.waitUntilShowing(robot(), Matchers.byTitle(JDialog.class, title)));
  }

  public void selectApp(@NotNull String appName) {
    ActionButtonFixture runButton = findRunApplicationButton();
    Container actionToolbarContainer = GuiQuery.getNonNull(() -> runButton.target().getParent());
    ComboBoxActionFixture comboBoxActionFixture = ComboBoxActionFixture.findComboBox(robot(), actionToolbarContainer);
    comboBoxActionFixture.selectItem(appName);
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    Wait.seconds(1).expecting("ComboBox to be selected").until(() -> appName.equals(comboBoxActionFixture.getSelectedItemText()));
  }

  /**
   * Gets the focus back to Android Studio if it was lost
   */
  public void requestFocusIfLost() {
    KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Wait.seconds(5).expecting("a component to have the focus").until(() -> {
      // Keep requesting focus until it is obtained by a component which is showing. Since there is no guarantee that the request focus will
      // be granted keep asking until it is. This problem has appeared at least when not using a window manager when running tests. The focus
      // can sometimes temporarily be held by a component that is not showing, when closing a dialog for example. This is a transition state
      // and we want to make sure to keep going until the focus is held by a stable component.
      Component focusOwner = keyboardFocusManager.getFocusOwner();
      if (focusOwner == null || !focusOwner.isShowing()) {
        if (SystemInfo.isMac) {
          robot().click(target(), new Point(1, 1)); // Simulate title bar click
        }
        GuiTask.execute(() -> target().requestFocus());
        return false;
      }
      return true;
    });
  }

  public void selectPreviousEditor() {
    robot().pressAndReleaseKey(KeyEvent.VK_E, SystemInfo.isMac ? META_MASK : CTRL_MASK);
    GuiTests.waitUntilShowing(robot(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel header) {
        return Objects.equals(header.getText(), "Recent Files");
      }
    });
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER, 0);
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }
}
