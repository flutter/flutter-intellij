/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import io.flutter.run.common.TestLineMarkerContributor;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Annotates bazel Flutter tests with line markers.
 */
public class FlutterBazelTestLineMarkerContributor extends TestLineMarkerContributor {

  public FlutterBazelTestLineMarkerContributor() {
    super(BazelTestConfigUtils.getInstance());
  }

  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    final FlutterSettings settings = FlutterSettings.getInstance();
    return super.getInfo(element);
  }
}
