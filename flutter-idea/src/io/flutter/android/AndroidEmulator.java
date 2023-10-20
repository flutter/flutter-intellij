/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ReflectionUtil;
import io.flutter.FlutterMessages;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AndroidEmulator {
  private static final Logger LOG = Logger.getInstance(AndroidEmulator.class);

  @NotNull final AndroidSdk androidSdk;
  @NotNull final String id;
  ProcessAdapter listener;

  AndroidEmulator(@NotNull AndroidSdk androidSdk, @NotNull String id) {
    this.androidSdk = androidSdk;
    this.id = id;
  }

  public String getName() {
    return id.replaceAll("_", " ");
  }

  public void startEmulator() {
    if (androidSdk.project.isDisposed()) {
      return;
    }
    final VirtualFile emulator = androidSdk.getEmulatorToolExecutable();
    if (emulator == null) {
      FlutterMessages.showError(
        "Error Opening Emulator",
        "Unable to locate the emulator tool in the Android SDK.",
        androidSdk.project);
      return;
    }

    final String emulatorPath = emulator.getCanonicalPath();
    assert (emulatorPath != null);

    final GeneralCommandLine cmd = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(androidSdk.getHome().getCanonicalPath())
      .withExePath(emulatorPath)
      .withParameters("-avd", this.id);

    final boolean shouldLaunchEmulatorInToolWindow = getLaunchInToolWindow();
    if (shouldLaunchEmulatorInToolWindow) {
      cmd.addParameter("-qt-hide-window");
      cmd.addParameter("-grpc-use-token");
      cmd.addParameters("-idle-grpc-timeout", "300");
    }

    try {
      final StringBuilder stdout = new StringBuilder();
      final ColoredProcessHandler process = new MostlySilentColoredProcessHandler(cmd);
      listener = new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          if (androidSdk.project.isDisposed()) {
            return;
          }
          if (outputType == ProcessOutputTypes.STDERR || outputType == ProcessOutputTypes.STDOUT) {
            stdout.append(event.getText());
          }
          openEmulatorToolWindow(shouldLaunchEmulatorInToolWindow);
        }

        public void processTerminated(@NotNull ProcessEvent event) {
          process.removeProcessListener(listener);
          final int exitCode = event.getExitCode();
          if (exitCode != 0) {
            final String message = stdout.isEmpty()
                                   ? "Android emulator terminated with exit code " + exitCode
                                   : stdout.toString().trim();
            FlutterMessages.showError("Error Opening Emulator", message, androidSdk.project);
          }
        }
      };
      process.addProcessListener(listener);
      process.startNotify();
    }
    catch (ExecutionException | RuntimeException e) {
      FlutterMessages.showError("Error Opening Emulator", e.toString(), androidSdk.project);
    }
  }

  private void openEmulatorToolWindow(boolean shouldLaunchEmulatorInToolWindow) {
    if (!shouldLaunchEmulatorInToolWindow) {
      return;
    }
    if (androidSdk.project.isDisposed()) {
      return;
    }
    final ToolWindowManager wm = ToolWindowManager.getInstance(androidSdk.project);
    final ToolWindow tw = wm.getToolWindow("Android Emulator");
    if (tw == null || tw.isVisible()) {
      return;
    }

    assert ApplicationManager.getApplication() != null;
    ApplicationManager.getApplication().invokeLater(() -> {
      tw.setAutoHide(false);
      tw.show();
    }, ModalityState.stateForComponent(tw.getComponent()));
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof AndroidEmulator && ((AndroidEmulator)obj).id.equals(id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  // This is: EmulatorSettings.getInstance().getLaunchInToolWindow();
  // Beginning in 2022.2, Android Studio moved this class to a different package.
  // IntelliJ did not adopt that change, and we cannot build separate plugins for the two.
  @SuppressWarnings("ConstantConditions")
  private boolean getLaunchInToolWindow() {
    Class<?> aClass;
    try {
      // IntelliJ
      aClass = Class.forName("com.android.tools.idea.emulator.EmulatorSettings");
    }
    catch (ClassNotFoundException e) {
      try {
        // Android Studio
        aClass = Class.forName("com.android.tools.idea.streaming.EmulatorSettings");
      }
      catch (ClassNotFoundException ex) {
        return false;
      }
    }
    Method method = ReflectionUtil.getDeclaredMethod(aClass, "getInstance");
    try {
      Object instance = method.invoke(null);
      Method option = ReflectionUtil.getDeclaredMethod(aClass, "getLaunchInToolWindow");
      return (boolean)option.invoke(instance);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      return false;
    }
  }
}
