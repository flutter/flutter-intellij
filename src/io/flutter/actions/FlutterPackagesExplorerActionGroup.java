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
import io.flutter.FlutterConstants;
import org.jetbrains.annotations.NotNull;


public class FlutterPackagesExplorerActionGroup extends DefaultActionGroup {

  private static boolean isFlutterYamlFile(@NotNull AnActionEvent e) {
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    return file != null && FlutterConstants.FLUTTER_YAML.equalsIgnoreCase(file.getName());
  }

  @Override
  public void update(AnActionEvent e) {
    final boolean enabled = isFlutterYamlFile(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }
}
