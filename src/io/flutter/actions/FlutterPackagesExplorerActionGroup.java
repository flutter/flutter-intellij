/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterUtils;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;


public class FlutterPackagesExplorerActionGroup extends DefaultActionGroup {

  private static boolean isFlutterPubspec(@NotNull AnActionEvent e) {
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    return FlutterUtils.exists(file) && FlutterUtils.isPubspecFile(file) && FlutterModuleUtils.declaresFlutterDependency(file);
  }

  @Override
  public void update(AnActionEvent e) {
    final boolean enabled = isFlutterPubspec(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }
}
