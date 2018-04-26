/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterConsoleFilter implements Filter {

  private static class OpenExternalFileHyperlink implements HyperlinkInfo {

    private final String myPath;

    OpenExternalFileHyperlink(VirtualFile file) {
      myPath = file.getPath();
    }

    @Override
    public void navigate(Project project) {
      try {
        final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters(myPath);
        final OSProcessHandler handler = new OSProcessHandler(cmd);
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(final ProcessEvent event) {
            if (event.getExitCode() != 0) {
              FlutterMessages.showError("Error Opening ", myPath);
            }
          }
        });
        handler.startNotify();
      }
      catch (ExecutionException e) {
        FlutterMessages.showError(
          "Error Opening External File",
          "Exception: " + e.getMessage());
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(FlutterConsoleFilter.class);

  private final @NotNull Module module;

  public FlutterConsoleFilter(@NotNull Module module) {
    this.module = module;
  }

  @VisibleForTesting
  @Nullable
  public VirtualFile fileAtPath(@NotNull String pathPart) {

    // "lib/main.dart:6"
    pathPart = pathPart.split(":")[0];

    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile root : roots) {
      final String baseDirPath = root.getPath();
      final String path = baseDirPath + "/" + pathPart;
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (!pathPart.isEmpty() && file != null && file.exists()) {
        return file;
      }
    }

    return null;
  }

  @Nullable
  public Result applyFilter(final String line, final int entireLength) {
    if (line.startsWith("Run \"flutter doctor\" for information about installing additional components.")) {
      return getFlutterDoctorResult(line, entireLength - line.length());
    }

    // Check for "restart" action in debugging output.
    if (line.startsWith("you may need to restart the app")) {
      return getRestartAppResult(line, entireLength - line.length());
    }

    int lineNumber = 0;
    String pathPart = line.trim();

    // Check for, e.g.,
    //   * "Launching lib/main.dart"
    //   * "open ios/Runner.xcworkspace"
    if (line.startsWith("Launching ") || (line.startsWith("open "))) {
      final String[] parts = line.split(" ");
      if (parts.length > 1) {
        pathPart = parts[1];
      }
    }

    // Check for embedded paths, e.g.,
    //    * "  • MyApp.xzzzz (lib/main.dart:6)"
    //    * "  • _MyHomePageState._incrementCounter (lib/main.dart:49)"
    final String[] parts = pathPart.split(" ");
    for (String part : parts) {
      // "(lib/main.dart:49)"
      if (part.startsWith("(") && part.endsWith(")")) {
        part = part.substring(1, part.length() - 1);
        final String[] split = part.split(":");
        if (split.length == 2) {
          try {
            // Reconcile line number indexing.
            lineNumber = Math.max(0, Integer.parseInt(split[1]) - 1);
          }
          catch (NumberFormatException e) {
            // Ignored.
          }
        }
        pathPart = part;
      }
    }

    final VirtualFile file = fileAtPath(pathPart);
    if (file != null) {
      // "open ios/Runner.xcworkspace"
      final boolean openAsExternalFile = FlutterUtils.isXcodeFileName(pathPart);
      final int lineStart = entireLength - line.length() + line.indexOf(pathPart);

      final HyperlinkInfo hyperlinkInfo =
        openAsExternalFile ? new OpenExternalFileHyperlink(file) : new OpenFileHyperlinkInfo(module.getProject(), file, lineNumber, 0);
      return new Result(lineStart, lineStart + pathPart.length(), hyperlinkInfo);
    }

    return null;
  }

  private static Result getFlutterDoctorResult(final String line, final int lineStart) {
    final int commandStart = line.indexOf('"') + 1;
    final int startOffset = lineStart + commandStart;
    final int commandLength = "flutter doctor".length();
    return new Result(startOffset, startOffset + commandLength, new FlutterDoctorHyperlinkInfo());
  }

  private static Result getRestartAppResult(final String line, final int lineStart) {
    final int commandStart = line.indexOf("restart");
    final int startOffset = lineStart + commandStart;
    final int commandLength = "restart".length();
    return new Result(startOffset, startOffset + commandLength, new RestartAppHyperlinkInfo());
  }

  private static class RestartAppHyperlinkInfo implements HyperlinkInfo {
    @Override
    public void navigate(final Project project) {
      // TODO(pq) analytics for clicking the link? (We do log the command.)
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) {
        Messages.showErrorDialog(project, "Flutter SDK not found", "Error");
        return;
      }

      final FlutterApp app = FlutterApp.fromProjectProcess(project);
      if (app == null) {
        Messages.showErrorDialog(project, "Running Flutter App not found", "Error");
        return;
      }

      FlutterReloadManager.getInstance(project).saveAllAndRestart(app);
    }
  }

  private static class FlutterDoctorHyperlinkInfo implements HyperlinkInfo {
    @Override
    public void navigate(final Project project) {
      // TODO(skybrian) analytics for clicking the link? (We do log the command.)
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) {
        Messages.showErrorDialog(project, "Flutter SDK not found", "Error");
        return;
      }
      if (sdk.flutterDoctor().startInConsole(project) == null) {
        Messages.showErrorDialog(project, "Failed to start 'flutter doctor'", "Error");
      }
    }
  }
}
