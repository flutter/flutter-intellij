/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.google.gson.*;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An Android SDK and its home directory; this references an IntelliJ @{@link Sdk} instance.
 */
public class IntelliJAndroidSdk {
  private static final Logger LOG = Logger.getInstance(IntelliJAndroidSdk.class);

  @NotNull
  private final Sdk sdk;

  @NotNull
  private final VirtualFile home;

  private IntelliJAndroidSdk(@NotNull Sdk sdk, @NotNull VirtualFile home) {
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
  public static IntelliJAndroidSdk fromProject(@NotNull Project project) {
    final Sdk candidate = ProjectRootManager.getInstance(project).getProjectSdk();
    return fromSdk(candidate);
  }

  /**
   * Returns the Android SDK that matches the ANDROID_HOME environment variable, provided it exists.
   */
  @Nullable
  public static IntelliJAndroidSdk fromEnvironment() {
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
   * Returns the Android SDK for the given home directory, or null if no SDK matches.
   */
  @Nullable
  public static IntelliJAndroidSdk fromHome(VirtualFile file) {
    for (IntelliJAndroidSdk candidate : findAll()) {
      if (file.equals(candidate.getHome())) {
        return candidate;
      }
    }

    return null; // not found
  }

  /**
   * Returns the best value of the Android SDK location to use, including possibly querying flutter tools for it.
   */
  public static String chooseAndroidHome(@Nullable Project project, boolean askFlutterTools) {
    if (project == null) {
      return EnvironmentUtil.getValue("ANDROID_HOME");
    }

    final IntelliJAndroidSdk intelliJAndroidSdk = fromProject(project);
    if (intelliJAndroidSdk != null) {
      return intelliJAndroidSdk.getHome().getPath();
    }

    // Ask flutter tools.
    if (askFlutterTools) {
      final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
      if (flutterSdk != null) {
        final FlutterCommand command = flutterSdk.flutterConfig("--machine");
        final OSProcessHandler process = command.startProcess(false);
        final StringBuilder stdout = new StringBuilder();
        process.addProcessListener(new ProcessAdapter() {
          boolean hasSeenStartingBrace = false;

          @Override
          public void onTextAvailable(ProcessEvent event, Key outputType) {
            // {"android-studio-dir":"/Applications/Android Studio 3.0 Preview.app/Contents"}
            if (outputType == ProcessOutputTypes.STDOUT) {
              // Ignore any non-json starting lines (like "Building flutter tool...").
              if (event.getText().startsWith("{")) {
                hasSeenStartingBrace = true;
              }
              if (hasSeenStartingBrace) {
                stdout.append(event.getText());
              }
            }
          }
        });

        LOG.info("Calling config --machine");
        final long start = System.currentTimeMillis();

        process.startNotify();

        if (process.waitFor(5000)) {
          final long duration = System.currentTimeMillis() - start;
          LOG.info("flutter config --machine: " + duration + "ms");

          final Integer code = process.getExitCode();
          if (code != null && code == 0) {
            try {
              final JsonParser jp = new JsonParser();
              final JsonElement elem = jp.parse(stdout.toString());
              final JsonObject obj = elem.getAsJsonObject();
              final JsonPrimitive primitive = obj.getAsJsonPrimitive("android-sdk");
              if (primitive != null) {
                return primitive.getAsString();
              }
            }
            catch (JsonSyntaxException ignored) {
            }
          }
          else {
            LOG.info("Exit code from flutter config --machine: " + code);
          }
        }
        else {
          LOG.info("Timeout when calling flutter config --machine");
        }
      }
    }

    return EnvironmentUtil.getValue("ANDROID_HOME");
  }

  /**
   * Returns each SDK that's an Android SDK.
   */
  @NotNull
  private static List<IntelliJAndroidSdk> findAll() {
    final List<IntelliJAndroidSdk> result = new ArrayList<>();
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      final IntelliJAndroidSdk candidate = IntelliJAndroidSdk.fromSdk(sdk);
      if (candidate != null) {
        result.add(candidate);
      }
    }
    return result;
  }

  @Nullable
  private static IntelliJAndroidSdk fromSdk(@Nullable Sdk candidate) {
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

    return new IntelliJAndroidSdk(candidate, home);
  }
}
