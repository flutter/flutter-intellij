/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.runner.DartRunner;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import com.jetbrains.lang.dart.util.DartUrlResolverImpl;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDaemonService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * This class could be independent of DartRunner; just copy down the code that is not implemented here.
 * Note that we have redefined DartRunner in third_party, which is a slightly modified version of the original.
 */
public class FlutterRunner extends DartRunner {

  private static final Logger LOG = Logger.getInstance(FlutterRunner.class);

  @Nullable
  private ObservatoryConnector myConnector;

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterRunner";
  }

  @Override
  public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
    FlutterDaemonService service = FlutterDaemonService.getInstance();
    return (profile instanceof FlutterRunConfiguration &&
            (DefaultRunExecutor.EXECUTOR_ID.equals(executorId) || DefaultDebugExecutor.EXECUTOR_ID.equals(executorId))) &&
           (service != null && service.getSelectedDevice() != null);
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    if (state instanceof FlutterAppState) {
      final FlutterAppState appState = (FlutterAppState)state;
      myConnector = new ObservatoryConnector() {
        @Override
        public boolean isConnectionReady() {
          return appState.isConnectionReady();
        }

        @Override
        public int getPort() {
          return appState.getObservatoryPort();
        }

        @Override
        public FlutterApp getApp() {
          return appState.getApp();
        }
      };
    }
    return super.doExecute(state, env);
  }

  // TODO(devoncarew): This may not be necessary with the latest debugger code from the Dart plugin.
  //@Override
  //protected DartUrlResolver getDartUrlResolver(@NotNull final Project project, @NotNull final VirtualFile contextFileOrDir) {
  //  return new FlutterUrlResolver(project, contextFileOrDir);
  //}

  @Override
  protected int getTimeout() {
    return 60000; // Allow 60 seconds to connect to the observatory.
  }

  @Nullable
  protected ObservatoryConnector getConnector() {
    return myConnector;
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
