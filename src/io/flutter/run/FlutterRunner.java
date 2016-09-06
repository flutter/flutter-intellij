/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.runner.DartRunner;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import com.jetbrains.lang.dart.util.DartUrlResolverImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

public class FlutterRunner extends DartRunner {
  private static final Logger LOG = Logger.getInstance(FlutterRunner.class);

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterRunner";
  }

  @Override
  public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
    return (profile instanceof FlutterRunConfiguration &&
            (DefaultRunExecutor.EXECUTOR_ID.equals(executorId) || DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)));
  }

  @Override
  protected DartUrlResolver getDartUrlResolver(@NotNull final Project project, @NotNull final VirtualFile contextFileOrDir) {
    return new FlutterUrlResolver(project, contextFileOrDir);
  }

  @Override
  protected int getTimeout() {
    return 30000; // Allow 30 seconds to connect to the observatory.
  }

  private static class FlutterUrlResolver extends DartUrlResolverImpl {
    private static final String PACKAGE_PREFIX = "package:";
    //private static final String PACKAGES_PREFIX = "packages/";

    FlutterUrlResolver(final @NotNull Project project, final @NotNull VirtualFile contextFile) {
      super(project, contextFile);
    }

    public boolean mayNeedDynamicUpdate() {
      return false;
    }

    @NotNull
    public String getDartUrlForFile(final @NotNull VirtualFile file) {
      String str = super.getDartUrlForFile(file);
      if (str.startsWith(PACKAGE_PREFIX)) {
        // Convert package: prefix to packages/ one.
        //return PACKAGES_PREFIX + str.substring(PACKAGE_PREFIX.length());
        return file.getPath(); // TODO This works on Mac running flutter locally. Not sure about other configs.
      }
      else if (str.startsWith("file:")) {
        return URI.create(str).toString();
      }
      return str;
    }

    @Nullable
    public VirtualFile findFileByDartUrl(final @NotNull String url) {
      VirtualFile file = super.findFileByDartUrl(url);
      if (file == null) {
        file = LocalFileSystem.getInstance().findFileByPath(SystemInfo.isWindows ? url : ("/" + url));
      }
      return file;
    }
  }
}
