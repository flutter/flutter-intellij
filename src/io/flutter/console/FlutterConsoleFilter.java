/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

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

  @Nullable
  public Result applyFilter(final String line, final int entireLength) {
    if (line.startsWith("Run \"flutter doctor\" for information about installing additional components.")) {
      return getFlutterDoctorResult(line, entireLength - line.length());
    }

    boolean openAsExternalFile = false;

    String pathPart = line.trim();

    // Check for, e.g., "Launching lib/main.dart"
    if (line.startsWith("Launching ")) {
      final String[] parts = line.split(" ");
      if (parts.length > 1) {
        pathPart = parts[1];
      }
    }

    // Check for, e.g., "open ios/Runner.xcworkspace"
    if (pathPart.startsWith("open ")) {
      final String[] parts = pathPart.split(" ");
      if (parts.length > 1) {
        if (parts[1].endsWith(".xcworkspace")) {
          pathPart = parts[1];
          openAsExternalFile = true;
        }
      }
    }

    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile root : roots) {
      final String baseDirPath = root.getPath();
      final String path = baseDirPath + "/" + pathPart;

      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (!pathPart.isEmpty() && file != null && file.exists()) {
        final int lineStart = entireLength - line.length() + line.indexOf(pathPart);

        final HyperlinkInfo hyperlinkInfo =
          openAsExternalFile ? new OpenExternalFileHyperlink(file) : new OpenFileHyperlinkInfo(module.getProject(), file, 0, 0);
        return new Result(lineStart, lineStart + pathPart.length(), hyperlinkInfo);
      }
    }

    return null;
  }

  private Result getFlutterDoctorResult(final String line, final int lineStart) {
    final int commandStart = line.indexOf('"') + 1;
    final int startOffset = lineStart + commandStart;
    final int commandLength = "flutter doctor".length();
    return new Result(startOffset, startOffset + commandLength, new FlutteryHyperlinkInfo());
  }

  private class FlutteryHyperlinkInfo implements HyperlinkInfo {
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
