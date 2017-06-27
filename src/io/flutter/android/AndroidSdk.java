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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Android SDK and its home directory.
 */
public class AndroidSdk {
  private static final Logger LOG = Logger.getInstance(AndroidSdk.class);

  @NotNull
  private final Sdk sdk;

  @NotNull
  private final VirtualFile home;

  private AndroidSdk(@NotNull Sdk sdk, @NotNull VirtualFile home) {
    this.sdk = sdk;
    this.home = home;
  }

  /**
   * Returns android home directory for this SDK.
   */
  @NotNull
  public VirtualFile getHome() {
    return home;
  }

  /**
   * Changes the project's Java SDK to this one.
   */
  public void setCurrent(@NotNull Project project) {
    assert ApplicationManager.getApplication().isWriteAccessAllowed();

    final ProjectRootManager roots = ProjectRootManager.getInstance(project);
    roots.setProjectSdk(sdk);
  }

  /**
   * Returns the Java SDK in the project's configuration, or null if not an Android SDK.
   */
  @Nullable
  public static AndroidSdk fromProject(@NotNull Project project) {
    final Sdk candidate = ProjectRootManager.getInstance(project).getProjectSdk();
    return fromSdk(candidate);
  }

  /**
   * Returns the Android SDK that matches the ANDROID_HOME environment variable, provided it exists.
   */
  @Nullable
  public static AndroidSdk fromEnvironment() {
    final String path = EnvironmentUtil.getValue("ANDROID_HOME");
    if (path == null) {
      return null;
    }

    // TODO(skybrian) refresh?
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) {
      return null;
    }

    return fromHome(file);
  }

  /**
   * Choose the best possible Android SDK value. Check if one is set for the current project; use that
   * if possible, else fall back to checking the 'ANDROID_HOME' environment variable.
   */
  @Nullable
  public static AndroidSdk chooseBestSdk(@NotNull Project project) {
    final AndroidSdk sdk = fromProject(project);
    return sdk == null ? fromEnvironment() : sdk;
  }

  /**
   * Returns the Android SDK for the given home directory, or null if no SDK matches.
   */
  @Nullable
  public static AndroidSdk fromHome(VirtualFile file) {
    for (AndroidSdk candidate : findAll()) {
      if (file.equals(candidate.getHome())) {
        return candidate;
      }
    }

    return null; // not found
  }

  /**
   * Returns the best value of ANDROID_HOME to use.
   * <p>
   * If the given project has an Android SDK set, prefer that. Otherwise get it from the environment.
   */
  public static String chooseAndroidHome(@Nullable Project project) {
    final AndroidSdk sdk = project == null ? null : fromProject(project);
    return sdk == null ? EnvironmentUtil.getValue("ANDROID_HOME") : sdk.getHome().getPath();
  }

  /**
   * Returns each SDK that's an Android SDK.
   */
  @NotNull
  private static List<AndroidSdk> findAll() {
    final List<AndroidSdk> result = new ArrayList<>();
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      final AndroidSdk candidate = AndroidSdk.fromSdk(sdk);
      if (candidate != null) {
        result.add(candidate);
      }
    }
    return result;
  }

  @Nullable
  private static AndroidSdk fromSdk(@Nullable Sdk candidate) {
    if (candidate == null) {
      return null;
    }

    if (!"Android SDK".equals(candidate.getSdkType().getName())) {
      return null;
    }

    final VirtualFile home = candidate.getHomeDirectory();
    if (home == null) {
      return null; // Skip; misconfigured SDK?
    }

    return new AndroidSdk(candidate, home);
  }

  @Nullable
  public VirtualFile getEmulatorToolExecutable() {
    // Look for $ANDROID_HOME/tools/emulator.
    return home.findFileByRelativePath("tools/" + (SystemInfo.isWindows ? "emulator.exe" : "emulator"));
  }

  @NotNull
  public List<AndroidEmulator> getEmulators() {
    // Execute $ANDROID_HOME/tools/emulator -list-avds and parse the results.
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
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          if (outputType == ProcessOutputTypes.STDOUT) {
            stringBuilder.append(event.getText());
          }
        }
      });
      process.startNotify();

      // We wait a maximum of 2000ms.
      if (!process.waitFor(2000)) {
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
      LOG.warn("Error listing android emulators", e);
      return Collections.emptyList();
    }
  }
}
