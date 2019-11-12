/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.flutter.console.FlutterConsoles;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manage installing the webdev cli.
 *
 * @deprecated WebDevManager will be removed shortly
 */
@Deprecated
public class WebDevManager {
  public static WebDevManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, WebDevManager.class);
  }

  @NotNull private final Project project;

  private boolean installedWebdev = false;

  private WebDevManager(@NotNull Project project) {
    this.project = project;
  }

  public boolean hasInstalledWebDev() {
    return installedWebdev;
  }

  public CompletableFuture<Boolean> installWebdev() {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return createCompletedFuture(false);
    }

    final List<PubRoot> pubRoots = PubRoots.forProject(project);
    if (pubRoots.isEmpty()) {
      return createCompletedFuture(false);
    }

    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    final FlutterCommand command = sdk.flutterPackagesPub(pubRoots.get(0), "global", "activate", "webdev");

    final ProgressManager progressManager = ProgressManager.getInstance();
    //noinspection DialogTitleCapitalization
    progressManager.run(new Task.Backgroundable(project, "Installing webdev...", true) {
      Process process;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(getTitle());
        indicator.setIndeterminate(true);

        process = command.start((ProcessOutput output) -> {
          if (output.getExitCode() != 0) {
            final String message = (output.getStdout() + "\n" + output.getStderr()).trim();
            FlutterConsoles.displayMessage(project, null, message, true);
          }
        }, null);

        try {
          final int resultCode = process.waitFor();
          if (resultCode == 0) {
            installedWebdev = true;
          }
          result.complete(resultCode == 0);
        }
        catch (RuntimeException | InterruptedException re) {
          if (!result.isDone()) {
            result.complete(false);
          }
        }

        process = null;
      }

      @Override
      public void onCancel() {
        if (process != null && process.isAlive()) {
          process.destroy();
          if (!result.isDone()) {
            result.complete(false);
          }
        }
      }
    });

    return result;
  }

  private CompletableFuture<Boolean> createCompletedFuture(boolean value) {
    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    result.complete(value);
    return result;
  }
}
