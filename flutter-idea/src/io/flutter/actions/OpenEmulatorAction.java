/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.flutter.android.AndroidEmulator;
import io.flutter.sdk.AndroidEmulatorManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class OpenEmulatorAction extends AnAction {
  /**
   * Retrieve a list of {@link OpenEmulatorAction}s.
   * <p>
   * This list is based off of cached information from the {@link AndroidEmulatorManager} class. Callers
   * who wanted notifications for updates should listen the {@link AndroidEmulatorManager} for changes
   * to the list of emulators.
   */
  public static List<OpenEmulatorAction> getEmulatorActions(Project project) {
    if (project == null || project.isDisposed()) {
      return new ArrayList<>();
    }
    final AndroidEmulatorManager emulatorManager = AndroidEmulatorManager.getInstance(project);

    final List<AndroidEmulator> emulators = emulatorManager.getCachedEmulators();
    return emulators.stream().map(OpenEmulatorAction::new).collect(toList());
  }

  final AndroidEmulator emulator;

  public OpenEmulatorAction(AndroidEmulator emulator) {
    super("Open Android Emulator: " + emulator.getName());

    this.emulator = emulator;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    emulator.startEmulator();
  }
}
