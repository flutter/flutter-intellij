/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import icons.FlutterIcons;
import io.flutter.run.daemon.DeviceService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RestartFlutterDaemonAction extends AnAction {

  /**
   * Create a `RestartFlutterDaemonAction` for presentation in the device selector.
   */
  public static RestartFlutterDaemonAction forDeviceSelector() {
    return new RestartFlutterDaemonAction("Restart Flutter Daemon", FlutterIcons.Flutter);
  }

  /**
   * A default constructor, invoked by plugin.xml contributions.
   */
  RestartFlutterDaemonAction() {
    super();
  }

  /**
   * A constructor for dynamic invocation.
   */
  private RestartFlutterDaemonAction(@Nullable @NlsActions.ActionText String text,
                                     @Nullable Icon icon) {
    super(text, text, icon);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project == null) {
      return;
    }

    DeviceService.getInstance(project).restart();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
