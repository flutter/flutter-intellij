/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.lang.dart.psi.DartFile;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartSyntax;
import io.flutter.run.FlutterRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class FlutterTestLineMarkerContributor extends RunLineMarkerContributor {
  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    if (isTestCall(element)) {
      final AnAction[] actions = ExecutorAction.getActions();
      final Function<PsiElement, String> tooltipProvider =
        psiElement -> StringUtil.join(ContainerUtil.mapNotNull(actions, action -> getText(action, element)), "\n");
      return new Info(AllIcons.RunConfigurations.TestState.Run, tooltipProvider, actions);
    }

    return null;
  }

  private static boolean isTestCall(@NotNull PsiElement element) {
    if (!DartSyntax.isTestCall(element)) return false;

    final DartFile file = FlutterRunConfigurationProducer.getDartFile(element);
    return file != null && FlutterUtils.isInTestDir(file);
  }
}
