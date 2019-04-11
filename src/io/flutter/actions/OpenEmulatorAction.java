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
import io.flutter.android.AndroidSdk;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class OpenEmulatorAction extends AnAction {
  public static List<OpenEmulatorAction> getEmulatorActions(Project project) {
    final AndroidSdk sdk = AndroidSdk.createFromProject(project);
    if (sdk == null) {
      return Collections.emptyList();
    }

    final List<AndroidEmulator> emulators = sdk.getEmulators();
    emulators.sort((emulator1, emulator2) -> emulator1.getName().compareToIgnoreCase(emulator2.getName()));
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
