/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.actions.ReaderModeMatcher;
import com.intellij.codeInsight.actions.ReaderModeProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterReaderModeMatcher implements ReaderModeMatcher {
  @Nullable
  @Override
  public Boolean matches(@NotNull Project project,
                         @NotNull VirtualFile file,
                         @Nullable Editor editor,
                         @NotNull ReaderModeProvider.ReaderMode mode) {
    if (ReaderModeProvider.ReaderMode.READ_ONLY == mode) {
      return null;
    }
    List<Module> contents = FlutterModuleUtils.findModulesWithFlutterContents(project);
    if (contents.isEmpty()) {
      return null;
    }
    PubRoot root = PubRoot.forDescendant(file, project);
    if (root == null) {
      return null;
    }
    return false;
  }
}
