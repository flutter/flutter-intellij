/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.FlutterUtils;
import io.flutter.actions.AttachDebuggerAction;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.pub.PubRoot;
import io.flutter.run.SdkAttachConfig;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static io.flutter.actions.AttachDebuggerAction.ATTACH_IS_ACTIVE;
import static io.flutter.actions.AttachDebuggerAction.findRunConfig;

public class AddToAppUtils {

  private AddToAppUtils() {
  }

  public static boolean initializeAndDetectFlutter(@NotNull Project project) {
    // In 2019, we added some logic to reflectively access a not-yet public GRADLE_SYNC_TOPIC on `GradleSyncState` subscribing
    // a listener that called `GradleUtils.checkDartSupport()` on all sync events.
    // Sometime since then, the field has gone away entirely and we are not getting these notifications.
    // TODO(pq): 5/23/25  Asses whether we want to try and restore this logic

    MessageBusConnection connection = project.getMessageBus().connect(FlutterDartAnalysisServer.getInstance(project));

    if (!FlutterModuleUtils.hasFlutterModule(project)) {
      connection.subscribe(ModuleListener.TOPIC, new ModuleListener() {
        @Override
        public void modulesAdded(@NotNull Project proj, @NotNull List<? extends Module> modules) {
          for (Module module : modules) {
            if (module == null) continue;
            if (AndroidUtils.FLUTTER_MODULE_NAME.equals(module.getName()) ||
                (FlutterUtils.flutterGradleModuleName(project)).equals(module.getName())) {
              //connection.disconnect(); TODO(messick) Test this deletion!
              AppExecutorUtil.getAppExecutorService().execute(() -> {
                GradleUtils.enableCoeditIfAddToAppDetected(project);
              });
            }
          }
        }
      });
      return false;
    }
    else {
      Collection<ProjectType> projectTypes = ProjectTypeService.getProjectTypes(project);
      for (ProjectType projectType : projectTypes) {
        if (projectType != null && "Android".equals(projectType.getId())) {
          // This is an add-to-app project.
          connection.subscribe(DebuggerManagerListener.TOPIC, makeAddToAppAttachListener(project));
        }
      }
    }
    return true;
  }

  @NotNull
  private static DebuggerManagerListener makeAddToAppAttachListener(@NotNull Project project) {
    return new DebuggerManagerListener() {

      DebugProcessListener dpl = new DebugProcessListener() {
        @Override
        public void processDetached(@NotNull DebugProcess process, boolean closedByUser) {
          ThreeState state = project.getUserData(ATTACH_IS_ACTIVE);
          if (state != null) {
            project.putUserData(ATTACH_IS_ACTIVE, null);
          }
        }

        @Override
        public void processAttached(@NotNull DebugProcess process) {
          if (project.getUserData(ATTACH_IS_ACTIVE) != null) {
            return;
          }
          // Launch flutter attach if a run config can be found.
          @Nullable RunConfiguration runConfig = findRunConfig(project);
          if (runConfig == null) {
            // Either there is no Flutter run config or there are more than one.
            return;
          }
          if (!(runConfig instanceof SdkAttachConfig)) {
            // The selected run config at this point is not Flutter, so we can't start the process automatically.
            return;
          }
          FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
          if (sdk == null) {
            return;
          }
          // If needed, DataContext could be saved by FlutterReloadManager.beforeActionPerformed() in project user data.
          DataContext context = DataContext.EMPTY_CONTEXT;
          PubRoot pubRoot = ((SdkAttachConfig)runConfig).pubRoot;
          Application app = ApplicationManager.getApplication();
          project.putUserData(ATTACH_IS_ACTIVE, ThreeState.fromBoolean(true));
          if (app == null) return;
          // Note: Using block comments to preserve formatting.
          app.invokeLater( /* After the Android launch completes, */
            () -> app.executeOnPooledThread( /* but not on the EDT, */
              () -> app.runReadAction( /* with read access, */
                () -> new AttachDebuggerAction().startCommand(project, sdk, pubRoot, context)))); /* attach. */
        }
      };

      @Override
      public void sessionCreated(DebuggerSession session) {
        if (session != null) {
          session.getProcess().addDebugProcessListener(dpl);
        }
      }

      @Override
      public void sessionRemoved(DebuggerSession session) {
        if (session != null) {
          session.getProcess().removeDebugProcessListener(dpl);
        }
      }
    };
  }
}
