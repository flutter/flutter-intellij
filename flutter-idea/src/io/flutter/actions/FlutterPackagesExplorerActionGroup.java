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
import io.flutter.pub.PubRoot;
import org.jetbrains.annotations.NotNull;


public class FlutterPackagesExplorerActionGroup extends DefaultActionGroup {

  private static boolean isFlutterPubspec(@NotNull AnActionEvent e) {
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    final PubRoot root = file == null ? null : PubRoot.forDirectory(file.getParent());
    return root != null && root.getPubspec().equals(file) && root.declaresFlutter();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final boolean enabled = isFlutterPubspec(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }
}
