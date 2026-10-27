/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import io.flutter.android.AndroidEmulator;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that actions explicitly define {@link ActionUpdateThread#BGT}
 * for modern IntelliJ Platform (2024.1+) threading compliance.
 */
public class ActionUpdateThreadTest {

  @Test
  public void testFlutterGettingStartedAction_usesBgt() {
    final FlutterGettingStartedAction action = new FlutterGettingStartedAction();
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testFlutterSubmitFeedback_usesBgt() {
    final FlutterSubmitFeedback action = new FlutterSubmitFeedback();
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testRefreshToolWindowAction_usesBgt() {
    final RefreshToolWindowAction defaultAction = new RefreshToolWindowAction();
    assertEquals(ActionUpdateThread.BGT, defaultAction.getActionUpdateThread());

    final RefreshToolWindowAction idAction = new RefreshToolWindowAction("Flutter Inspector");
    assertEquals(ActionUpdateThread.BGT, idAction.getActionUpdateThread());
  }

  @Test
  public void testFlutterSdkActionBase_usesBgt() {
    final FlutterSdkAction action = new FlutterSdkAction() {
      @Override
      public void startCommand(@NotNull Project project,
                               @NotNull FlutterSdk sdk,
                               @Nullable PubRoot root,
                               @NotNull DataContext context) {
      }
    };
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testFlutterSdkSubclasses_inheritBgt() {
    assertEquals(ActionUpdateThread.BGT, new FlutterCleanAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterDoctorAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterPackagesAddAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterPackagesGetAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterPackagesUpgradeAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterUpgradeAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new AttachDebuggerAction().getActionUpdateThread());
  }

  @Test
  public void testOtherRegisteredActions_useBgt() {
    assertEquals(ActionUpdateThread.BGT, new DeviceSelectorAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new DeviceSelectorRefresherAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new ExtractWidgetAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new RestartFlutterDaemonAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new RunProfileFlutterApp().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new RunReleaseFlutterApp().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new ReloadFlutterAppRetarget().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new RestartFlutterAppRetarget().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new ReloadAllFlutterAppsRetarget().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new RestartAllFlutterAppsRetarget().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new OpenInXcodeAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new OpenInAppCodeAction().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new OpenSimulatorAction(true).getActionUpdateThread());
  }

  @Test
  public void testFlutterBuildActions_useBgt() {
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.AAR().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.APK().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.AppBundle().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.Bundle().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.Ios().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.Linux().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.Macos().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.Web().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterBuildActionGroup.Windows().getActionUpdateThread());
  }

  @Test
  public void testActionGroups_specifyExplicitUpdateThread() {
    assertEquals(ActionUpdateThread.BGT, new FlutterToolsActionGroup().getActionUpdateThread());
    assertEquals(ActionUpdateThread.BGT, new FlutterPackagesExplorerActionGroup().getActionUpdateThread());
    assertEquals(ActionUpdateThread.EDT, new FlutterExternalIdeActionGroup().getActionUpdateThread());
  }

  @Test
  public void testOpenEmulatorAction_usesBgt() {
    final AndroidEmulator emulator = mock(AndroidEmulator.class);
    when(emulator.getName()).thenReturn("Test_Emulator");
    final OpenEmulatorAction action = new OpenEmulatorAction(emulator);
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testRunFlutterAction_usesBgt() {
    final RunFlutterAction action = new RunFlutterAction("key", FlutterLaunchMode.DEBUG, "id") {};
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testFlutterAppActionHierarchy_usesBgt() {
    final FlutterAppAction action = mock(FlutterAppAction.class);
    when(action.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());

    final ReloadFlutterApp reloadAction = mock(ReloadFlutterApp.class);
    when(reloadAction.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, reloadAction.getActionUpdateThread());

    final RestartFlutterApp restartAction = mock(RestartFlutterApp.class);
    when(restartAction.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, restartAction.getActionUpdateThread());

    final ReloadAllFlutterApps reloadAllAction = mock(ReloadAllFlutterApps.class);
    when(reloadAllAction.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, reloadAllAction.getActionUpdateThread());

    final RestartAllFlutterApps restartAllAction = mock(RestartAllFlutterApps.class);
    when(restartAllAction.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, restartAllAction.getActionUpdateThread());
  }
}
