/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterErrors;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterConsoleFilter implements Filter {

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

    String pathPart = line.trim();

    // Check for, e.g., "Running lib/main.dart"
    if (line.startsWith("Running")) {
      final String[] parts = line.split(" ");
      if (parts.length > 1) {
        pathPart = parts[1];
      }
    }

    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile root : roots) {
      final String baseDirPath = root.getPath();
      final String path = baseDirPath + "/" + pathPart;

      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (!pathPart.isEmpty() && file != null && file.exists()) {
        final int lineStart = entireLength - line.length() + line.indexOf(pathPart);
        return new Result(lineStart, lineStart + pathPart.length(), new OpenFileHyperlinkInfo(module.getProject(), file, 0, 0));
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
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk != null) {
        try {
          sdk.runProject(project, "Flutter doctor", null, "doctor");
        }
        catch (ExecutionException e) {
          FlutterErrors.showError(
            FlutterBundle.message("flutter.command.exception.title"),
            FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
          LOG.warn(e);
        }
      }
    }
  }
}
