/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.refactoring.ServerRefactoring;
import org.dartlang.analysis.server.protocol.ExtractWidgetOptions;
import org.dartlang.analysis.server.protocol.RefactoringFeedback;
import org.dartlang.analysis.server.protocol.RefactoringKind;
import org.dartlang.analysis.server.protocol.RefactoringOptions;
import org.jetbrains.annotations.NotNull;

/**
 * LTK wrapper around Analysis Server 'Extract Widget' refactoring.
 */
public class ExtractWidgetRefactoring extends ServerRefactoring {
  private final ExtractWidgetOptions options = new ExtractWidgetOptions("NewWidget", false);

  public ExtractWidgetRefactoring(@NotNull final Project project,
                                  @NotNull final VirtualFile file,
                                  final int offset,
                                  final int length) {
    super(project, "Extract Widget", RefactoringKind.EXTRACT_WIDGET, file, offset, length);
  }

  @Override
  protected RefactoringOptions getOptions() {
    return options;
  }

  public void setStateful(boolean value) {
    options.setStateful(value);
  }

  @Override
  protected void setFeedback(@NotNull RefactoringFeedback feedback) {
  }

  public void setName(@NotNull String name) {
    options.setName(name);
  }

  public void sendOptions() {
    setOptions(true, null);
  }
}
