/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.testing.Modules;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleProjectEventListener;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleToolWindowFixture;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.collect.Lists;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.ThreeState;
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
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.ui.GuiTestingService.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.META_MASK;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("Duplicates") // Adapted from IdeFrameFixture in uitest-framework module, due to private constructor.
public class IdeaFrameFixture extends ComponentFixture<IdeaFrameFixture, IdeFrameImpl> {
  @NotNull private final GradleProjectEventListener myGradleProjectEventListener;
  @NotNull private final Modules myModules;
  @NotNull private final IdeFrameFixture myIdeFrameFixture; // Replaces 'this' when creating component fixtures.

  private TestProjectSystem myTestProjectSystem;
  private EditorFixture myEditor;
  private boolean myIsClosed;

  IdeaFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target) {
    super(IdeaFrameFixture.class, robot, target);
    myIdeFrameFixture = IdeFrameFixture.find(robot);
    Project project = getProject();
    myModules = new Modules(project);

    Disposable disposable = new NoOpDisposable();
    Disposer.register(project, disposable);

    myGradleProjectEventListener = new GradleProjectEventListener();

    GradleSyncState.subscribe(project, myGradleProjectEventListener);
    GradleBuildState.subscribe(project, myGradleProjectEventListener);
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
    throw new AssertionError("Unable to find AndroidGradleModel for module '" + name + "'");
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
  public GradleInvocationResult invokeProjectMake(@Nullable Wait wait) {
    myGradleProjectEventListener.reset();

    AtomicReference<GradleInvocationResult> resultRef = new AtomicReference<>();
    AndroidProjectBuildNotifications.subscribe(
      getProject(), context -> {
        if (context instanceof GradleBuildContext) {
          resultRef.set(((GradleBuildContext)context).getBuildResult());
        }
      });
    selectProjectMakeAction();

    waitForBuildToFinish(ASSEMBLE, wait);

    Wait.seconds(10)
      .expecting("Listeners to be notified of build-finished event")
      .until(()-> resultRef.get() != null);
    return resultRef.get();
  }

  @NotNull
  public IdeaFrameFixture invokeProjectMakeAndSimulateFailure(@NotNull String failure) {
    Runnable failTask = () -> {
      throw new ExternalSystemException(failure);
    };
    ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, failTask);
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
    return findActionButtonByActionId("Run", 30);
  }

  @NotNull
  public ActionButtonFixture findApplyChangesButton() {
    return findActionButtonByText("Apply Changes");
  }

  @NotNull
  public ActionButtonFixture findAttachDebuggerToAndroidProcessButton() {
    return findActionButtonByText("Attach debugger to Android process");
  }

  public DeployTargetPickerDialogFixture debugApp(@NotNull String appName) {
    selectApp(appName);
    findDebugApplicationButton().click();
    return DeployTargetPickerDialogFixture.find(robot());
  }

  public DeployTargetPickerDialogFixture runApp(@NotNull String appName) {
    selectApp(appName);
    findRunApplicationButton().waitUntilEnabledAndShowing().click();
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
  public IdeaFrameFixture waitForBuildToFinish(@NotNull BuildMode buildMode) {
    return waitForBuildToFinish(buildMode, null);
  }

  @NotNull
  public IdeaFrameFixture waitForBuildToFinish(@NotNull BuildMode buildMode, @Nullable Wait wait) {
    Project project = getProject();
    if (buildMode == SOURCE_GEN && !GradleProjectBuilder.getInstance(project).isSourceGenerationEnabled()) {
      return this;
    }

    if (wait == null) {
      // http://b/72834057 - If we keep tweaking this value we should consider a different way of waiting for this.
      wait = Wait.seconds(60);
    }
    wait.expecting("Build (" + buildMode + ") for project '" + project.getName() + "' to finish'")
      .until(() -> {
        if (buildMode == SOURCE_GEN) {
          PostProjectBuildTasksExecutor tasksExecutor = PostProjectBuildTasksExecutor.getInstance(project);
          if (tasksExecutor.getLastBuildTimestamp() != null) {
            // This will happen when creating a new project. Source generation happens before the IDE frame is found and build listeners
            // are created. It is fairly safe to assume that source generation happened if we have a timestamp for a "last performed build".
            return true;
          }
        }
        return myGradleProjectEventListener.isBuildFinished(buildMode);
      });

    GuiTests.waitForBackgroundTasks(robot());

    return this;
  }

  @NotNull
  public FileFixture findExistingFileByRelativePath(@NotNull String relativePath) {
    VirtualFile file = findFileByRelativePath(relativePath, true);
    return new FileFixture(getProject(), file);
  }

  /**
   * Returns the virtual file corresponding to the given path. The path must be relative to the project root directory
   * (the top-level directory containing all source files associated with the project).
   *
   * @param relativePath a file path relative to the project root directory
   * @param requireExists if true, this method asserts that the given path corresponds to an existing file
   * @return the virtual file corresponding to the given path, or null if requireExists is false and the file does not exist
   */
  @Nullable
  @Contract("_, true -> !null")
  public VirtualFile findFileByRelativePath(@NotNull String relativePath, boolean requireExists) {
    //noinspection Contract
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));

    VirtualFile projectRootDir = getProject().getBaseDir();
    projectRootDir.refresh(false, true);
    VirtualFile file = projectRootDir.findFileByRelativePath(relativePath);
    if (requireExists) {
      //noinspection Contract
      assertNotNull("Unable to find file with relative path '" + relativePath + "'", file);
    }
    return file;
  }

  public void setTestProjectSystem(TestProjectSystem testProjectSystem) {
    myTestProjectSystem = testProjectSystem;
  }

  @NotNull
  public IdeaFrameFixture requestProjectSync() {
    return requestProjectSync(null);
  }

  @NotNull
  public IdeaFrameFixture requestProjectSync(@Nullable Wait wait) {
    myGradleProjectEventListener.reset();

    waitForSyncAction(wait);
    invokeMenuPath("File", "Sync Project with Gradle Files");

    return this;
  }

  private void waitForSyncAction(@Nullable Wait wait) {
    GuiTests.waitForBackgroundTasks(robot(), wait);
  }

  @NotNull
  public IdeaFrameFixture waitForGradleProjectSyncToFail() {
    return waitForGradleProjectSyncToFail(Wait.seconds(10));
  }

  @NotNull
  public IdeaFrameFixture waitForGradleProjectSyncToFail(@NotNull Wait waitForSync) {
    try {
      waitForGradleProjectSyncToFinish(waitForSync, true);
      fail("Expecting project sync to fail");
    }
    catch (RuntimeException expected) {
      // expected failure.
    }
    GuiTests.waitForBackgroundTasks(robot());
    return this;
  }

  @NotNull
  public IdeaFrameFixture waitForGradleProjectSyncToStart() {
    Project project = getProject();
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    if (!syncState.isSyncInProgress()) {
      Wait.seconds(10).expecting("Syncing project '" + project.getName() + "' to finish")
        .until(myGradleProjectEventListener::isSyncStarted);
    }
    return this;
  }

  @NotNull
  public IdeaFrameFixture waitForGradleProjectSyncToFinish() {
    // Workaround: b/72654538: Gradle Project Sync takes longer time
    return waitForGradleProjectSyncToFinish(Wait.seconds(60));
  }

  @NotNull
  public IdeaFrameFixture waitForGradleProjectSyncToFinish(@NotNull Wait waitForSync) {
    waitForGradleProjectSyncToFinish(waitForSync, false);
    return this;
  }

  private void waitForGradleProjectSyncToFinish(@NotNull Wait waitForSync, boolean expectSyncFailure) {
    Project project = getProject();

    // ensure GradleInvoker (in-process build) is always enabled.
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = true;

    waitForSync.expecting("syncing project '" + project.getName() + "' to finish")
      .until(() -> {
        GradleSyncState syncState = GradleSyncState.getInstance(project);
        boolean syncFinished =
          (myGradleProjectEventListener.isSyncFinished() || syncState.isSyncNeeded() != ThreeState.YES) && !syncState.isSyncInProgress();
        if (expectSyncFailure) {
          syncFinished = syncFinished && myGradleProjectEventListener.hasSyncError();
        }
        return syncFinished;
      });

    waitForSyncAction(null);

    if (myGradleProjectEventListener.hasSyncError()) {
      RuntimeException syncError = myGradleProjectEventListener.getSyncError();
      myGradleProjectEventListener.reset();
      throw syncError;
    }

    if (!myGradleProjectEventListener.isSyncSkipped()) {
      waitForBuildToFinish(SOURCE_GEN);
    }

    GuiTests.waitForBackgroundTasks(robot());
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId, long secondsToWait) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target(), secondsToWait);
  }

  @NotNull
  private ActionButtonFixture findActionButtonByText(@NotNull String text) {
    return ActionButtonFixture.findByText(text, robot(), target());
  }

  @NotNull
  public BuildVariantsToolWindowFixture getBuildVariantsWindow() {
    return new BuildVariantsToolWindowFixture(myIdeFrameFixture);
  }

  @NotNull
  public BuildToolWindowFixture getBuildToolWindow() {
    return new BuildToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public GradleToolWindowFixture getGradleToolWindow() {
    return new GradleToolWindowFixture(getProject(), robot());
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
  public IdeaFrameFixture useLocalGradleDistribution(@NotNull File gradleHomePath) {
    return useLocalGradleDistribution(gradleHomePath.getPath());
  }

  @NotNull
  public IdeaFrameFixture useLocalGradleDistribution(@NotNull String gradleHome) {
    GradleProjectSettings settings = getGradleSettings();
    settings.setDistributionType(LOCAL);
    settings.setGradleHome(gradleHome);
    return this;
  }

  @NotNull
  public GradleProjectSettings getGradleSettings() {
    return GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(getProject());
  }

  @NotNull
  public AvdManagerDialogFixture invokeAvdManager() {
    // The action button is prone to move during rendering so that robot.click() could miss.
    // So, we use component's click here directly.
    ActionButtonFixture actionButtonFixture = findActionButtonByActionId("Android.RunAndroidAvdManager", 30);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        actionButtonFixture.target().click();
      }
    });
    return AvdManagerDialogFixture.find(robot(), myIdeFrameFixture);
  }

  @NotNull
  public IdeSettingsDialogFixture invokeSdkManager() {
    robot().click(robot().finder().find(Matchers.byTooltip(JComponent.class, "SDK Manager").andIsShowing()));
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
    requestFocusIfLost(); // "Close Project" can be disabled if no component has focus
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

  @NotNull
  public DialogFixture waitForDialog(@NotNull String title, long secondsToWait) {
    return new DialogFixture(robot(), GuiTests.waitUntilShowing(robot(), null, Matchers.byTitle(JDialog.class, title), secondsToWait));
  }

  @NotNull
  public IdeaFrameFixture setGradleJvmArgs(@NotNull String jvmArgs) {
    Project project = getProject();

    GradleSettings settings = GradleSettings.getInstance(project);
    settings.setGradleVmOptions(jvmArgs);

    Wait.seconds(1).expecting("Gradle settings to be set").until(() -> jvmArgs.equals(settings.getGradleVmOptions()));

    return this;
  }

  @NotNull
  public IdeaFrameFixture updateGradleWrapperVersion(@NotNull String version) throws IOException {
    GradleWrapper.find(getProject()).updateDistributionUrlAndDisplayFailure(version);
    return this;
  }

  @NotNull
  public IdeaFrameFixture updateAndroidGradlePluginVersion(@NotNull String version) throws IOException {
    ApplicationManager.getApplication().invokeAndWait(
      () -> {
        AndroidPluginVersionUpdater versionUpdater = AndroidPluginVersionUpdater.getInstance(getProject());
        AndroidPluginVersionUpdater.UpdateResult result = versionUpdater.updatePluginVersion(GradleVersion.parse(version), null);
        assertTrue("Android Gradle plugin version was not updated", result.isPluginVersionUpdated());
      });
    return this;
  }

  @NotNull
  public GradleBuildModelFixture parseBuildFileForModule(@NotNull String moduleName) {
    Module module = getModule(moduleName);
    VirtualFile buildFile = getGradleBuildFile(module);
    Ref<GradleBuildModel> buildModelRef = new Ref<>();
    new ReadAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        buildModelRef.set(GradleBuildModel.parseBuildFile(buildFile, getProject()));
      }
    }.execute();
    return new GradleBuildModelFixture(buildModelRef.get());
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
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

  @NotNull
  public Dimension getIdeFrameSize() {
    return target().getSize();
  }

  @NotNull
  @SuppressWarnings("UnusedReturnValue")
  public IdeaFrameFixture setIdeFrameSize(@NotNull Dimension size) {
    target().setSize(size);
    return this;
  }
}
