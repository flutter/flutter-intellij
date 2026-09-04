// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.ide.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.sdk.DartSdk;
import icons.DartIcons;
import org.jetbrains.annotations.NotNull;

public class CreateDartFileAction extends CreateFileFromTemplateAction {

  @Override
  protected boolean isAvailable(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    return super.isAvailable(dataContext) && project != null && DartSdk.getDartSdk(project) != null;
  }

  @Override
  protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
    return DartBundle.message("title.create.dart.file.0", newName);
  }

  @Override
  protected void buildDialog(@NotNull Project project,
                             @NotNull PsiDirectory directory,
                             @NotNull CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(DartBundle.message("new.dart.file.title"))
      .addKind(DartBundle.message("list.item.dart.file"), DartIcons.Dart_file, "Dart File");

    // Notice that this is an odd place to hook into action execution but the more
    // expected `actionPerformed` is marked final in super.

    // TODO (pq): see if there's some way to find this action's place (w/o event data).
    Analytics.report(AnalyticsData.forAction(ActionManager.getInstance().getId(this), project));
  }
}
