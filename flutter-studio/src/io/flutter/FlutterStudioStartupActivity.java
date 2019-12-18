/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import static com.android.tools.idea.gradle.project.importing.NewProjectSetup.ANDROID_PROJECT_TYPE;
import static com.intellij.util.ReflectionUtil.findAssignableField;
import static io.flutter.actions.AttachDebuggerAction.ATTACH_IS_ACTIVE;
import static io.flutter.actions.AttachDebuggerAction.findRunConfig;

import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.ProjectTopics;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import io.flutter.actions.AttachDebuggerAction;
import io.flutter.android.AndroidModuleLibraryManager;
import io.flutter.project.FlutterProjectCreator;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.SdkRunConfig;
import io.flutter.sdk.FlutterSdk;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AddToAppUtils;
import io.flutter.utils.AndroidUtils;
import io.flutter.utils.FlutterModuleUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterStudioStartupActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    if (!AddToAppUtils.initializeAndDetectFlutter(project)) {
      return;
    }

    // Unset this flag for all projects, mainly to ease the upgrade from 3.0.1 to 3.1.
    // TODO(messick) Delete once 3.0.x has 0 7DA's.
    FlutterProjectCreator.disableUserConfig(project);
    // Monitor Android dependencies.
    if (FlutterSettings.getInstance().isSyncingAndroidLibraries() ||
        System.getProperty("flutter.android.library.sync", null) != null) {
      // TODO(messick): Remove the flag once this sync mechanism is stable.
      AndroidModuleLibraryManager.startWatching(project);
    }
  }
}
