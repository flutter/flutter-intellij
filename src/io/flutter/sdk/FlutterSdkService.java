/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;


import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A service to coordinate SDK configuration with specific IDEA and SmallIDE
 * implementations.
 */
public abstract class FlutterSdkService {

  @NotNull
  protected final Project project;

  protected FlutterSdkService(@NotNull Project project) {
    this.project = project;
  }

  public static FlutterSdkService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterSdkService.class);
  }

  public abstract void configureDartSdk(@Nullable Module module);
}
