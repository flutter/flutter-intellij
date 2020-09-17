/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A wrapper around an Android SDK on disk.
 */
public class AndroidSdk {
  private static final Logger LOG = Logger.getInstance(AndroidSdk.class);

  @Nullable
  public static AndroidSdk createFromProject(@NotNull Project project) {
    final String sdkPath = IntelliJAndroidSdk.chooseAndroidHome(project, true);
    if (sdkPath == null) {
      return null;
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(sdkPath);
    if (file == null) {
      return null;
    }
    return new AndroidSdk(file);
  }

  @NotNull
  private final VirtualFile home;

  AndroidSdk(@NotNull VirtualFile home) {
    this.home = home;
  }

  /**
   * Returns android home directory for this SDK.
   */
  @NotNull
  public VirtualFile getHome() {
    return home;
  }

  @Nullable
  public VirtualFile getEmulatorToolExecutable() {
    // Look for $ANDROID_HOME/emulator/emulator.
    final VirtualFile file = home.findFileByRelativePath("emulator/" + (SystemInfo.isWindows ? "emulator.exe" : "emulator"));
    if (file != null) {
      return file;
    }

    // Look for $ANDROID_HOME/tools/emulator.
    return home.findFileByRelativePath("tools/" + (SystemInfo.isWindows ? "emulator.exe" : "emulator"));
  }

  @NotNull
  public List<AndroidEmulator> getEmulators() {
    // Execute $ANDROID_HOME/emulator/emulator -list-avds and parse the results.
    final VirtualFile emulator = getEmulatorToolExecutable();
    if (emulator == null) {
      return Collections.emptyList();
    }

    final String emulatorPath = emulator.getCanonicalPath();
    assert (emulatorPath != null);

    final GeneralCommandLine cmd = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(home.getCanonicalPath())
      .withExePath(emulatorPath)
      .withParameters("-list-avds");

    try {
      final StringBuilder stringBuilder = new StringBuilder();
      final OSProcessHandler process = new OSProcessHandler(cmd);
      process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          if (outputType == ProcessOutputTypes.STDOUT) {
            stringBuilder.append(event.getText());
          }
        }
      });
      process.startNotify();

      // We wait a maximum of 10s.
      if (!process.waitFor(10000)) {
        return Collections.emptyList();
      }

      final Integer exitCode = process.getExitCode();
      if (exitCode == null || process.getExitCode() != 0) {
        return Collections.emptyList();
      }

      // 'emulator -list-avds' results are in the form "foo\nbar\nbaz\n".
      final List<AndroidEmulator> emulators = new ArrayList<>();

      for (String str : stringBuilder.toString().split("\n")) {
        str = str.trim();
        if (str.isEmpty()) {
          continue;
        }
        emulators.add(new AndroidEmulator(this, str));
      }

      return emulators;
    }
    catch (ExecutionException | RuntimeException e) {
      FlutterUtils.warn(LOG, "Error listing android emulators", e);
      return Collections.emptyList();
    }
  }
}
