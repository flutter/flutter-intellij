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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterConstants;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.actions.RestartFlutterApp;
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
          public void processTerminated(@NotNull final ProcessEvent event) {
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

  private final @NotNull Module module;

  public FlutterConsoleFilter(@NotNull Module module) {
    this.module = module;
  }

  @VisibleForTesting
  @Nullable
  public VirtualFile fileAtPath(@NotNull String pathPart) {
    // "lib/main.dart:6"
    pathPart = pathPart.split(":")[0];

    // We require the pathPart reference to be a file reference, otherwise we'd match things like
    // "Build: Running build completed, took 191ms".
    if (pathPart.indexOf('.') == -1) {
      return null;
    }

    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile root : roots) {
      if (!pathPart.isEmpty()) {
        final String baseDirPath = root.getPath();
        final String path = baseDirPath + "/" + pathPart;
        VirtualFile file = findFile(path);
        if (file == null) {
          // check example dir too
          // TODO(pq): remove when `example` is a content root: https://github.com/flutter/flutter-intellij/issues/2519
          final String exampleDirRelativePath = baseDirPath + "/example/" + pathPart;
          file = findFile(exampleDirRelativePath);
        }
        if (file != null) {
          return file;
        }
      }
    }

    return null;
  }

  private static VirtualFile findFile(final String path) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    return file != null && file.exists() ? file : null;
  }

  @Override
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
    if (pathPart.startsWith("Launching ") || pathPart.startsWith("open ")) {
      final String[] parts = pathPart.split(" ");
      if (parts.length > 1) {
        pathPart = parts[1];
      }
    }

    // Check for embedded paths, e.g.,
    //    * "  • MyApp.xzzzz (lib/main.dart:6)"
    //    * "  • _MyHomePageState._incrementCounter (lib/main.dart:49)"
    final String[] parts = pathPart.split(" ");
    int lineStart = -1;
    int highlightLength = 0;
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
          pathPart = part;
          lineStart = entireLength - line.length() + line.indexOf(pathPart);
          highlightLength = pathPart.length();
          break;
        }
        else if (split.length == 4 && split[0].equals("file")) {
          // part = file:///Users/user/AndroidStudioProjects/flutter_app/test/widget_test.dart:23:18
          try {
            // Reconcile line number indexing.
            lineNumber = Math.max(0, Integer.parseInt(split[2]) - 1);
          }
          catch (NumberFormatException e) {
            // Ignored.
          }
          pathPart = findRelativePath(split[1]);
          if (pathPart == null) {
            return null;
          }
          lineStart = entireLength - line.length() + line.indexOf(part);
          highlightLength = part.length();
          break;
        }
      }
    }

    final VirtualFile file = fileAtPath(pathPart);
    if (file != null) {
      // "open ios/Runner.xcworkspace"
      final boolean openAsExternalFile = FlutterUtils.isXcodeFileName(pathPart);

      final HyperlinkInfo hyperlinkInfo =
        openAsExternalFile ? new OpenExternalFileHyperlink(file) : new OpenFileHyperlinkInfo(module.getProject(), file, lineNumber, 0);
      return new Result(lineStart, lineStart + highlightLength, hyperlinkInfo);
    }

    return null;
  }

  private String findRelativePath(String threeSlashFileName) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile root : roots) {
      String path = root.getPath();
      int index = threeSlashFileName.indexOf(path);
      if (index > 0) {
        index += path.length();
        return threeSlashFileName.substring(index + 1);
      }
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
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) {
        Messages.showErrorDialog(project, "Flutter SDK not found", "Error");
        return;
      }

      final FlutterApp app = FlutterApp.firstFromProjectProcess(project);
      if (app == null) {
        Messages.showErrorDialog(project, "Running Flutter App not found", "Error");
        return;
      }

      FlutterInitializer.sendAnalyticsAction(RestartFlutterApp.class.getSimpleName());
      FlutterReloadManager.getInstance(project).saveAllAndRestart(app, FlutterConstants.RELOAD_REASON_MANUAL);
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
