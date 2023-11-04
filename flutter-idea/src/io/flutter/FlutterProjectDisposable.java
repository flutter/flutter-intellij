/*
 * Copyright 2023 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import io.flutter.sdk.FlutterSdkManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class FlutterProjectDisposable implements Disposable {
  private final Project project;
  public static FlutterProjectDisposable getInstance(@NotNull Project project) {
    return Objects.requireNonNull(project.getService(FlutterProjectDisposable.class));
  }

  private FlutterProjectDisposable(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void dispose() {
    System.out.println("in dispose");
  }
}
