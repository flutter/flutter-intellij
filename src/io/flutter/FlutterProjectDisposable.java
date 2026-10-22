/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Project-level {@link Disposable} parent for Flutter plugin resources.
 */
@Service(Service.Level.PROJECT)
public final class FlutterProjectDisposable implements Disposable {
  @NotNull
  public static Disposable getInstance(@NotNull Project project) {
    return project.getService(FlutterProjectDisposable.class);
  }

  @Override
  public void dispose() {
  }
}
