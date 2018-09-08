/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.Nullable;

public class TextEditorFileLocationMapper implements FileLocationMapper {
  final Document document;
  private final PsiFile psiFile;
  private final VirtualFile virtualFile;
  private final TextEditor textEditor;

  private final XDebuggerUtil debuggerUtil;

  TextEditorFileLocationMapper(TextEditor textEditor, Project project) {
    this.textEditor = textEditor;
    document = textEditor.getEditor().getDocument();
    psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    virtualFile = psiFile != null ? psiFile.getVirtualFile() : null;
    debuggerUtil = XDebuggerUtil.getInstance();
  }

  @Override
  public TextRange getIdentifierRange(int line, int column) {
    if (psiFile == null) {
      return null;
    }

    // Convert to zero based line and column indices.
    line = line - 1;
    column = column - 1;

    if (line >= document.getLineCount() || document.isLineModified(line)) {
      return null;
    }
    final XSourcePosition pos = debuggerUtil.createPosition(virtualFile, line, column);

    if (pos == null) {
      return null;
    }
    final int offset = pos.getOffset();
    final PsiElement element = psiFile.getOriginalFile().findElementAt(offset);
    if (element == null) {
      return null;
    }
    return element.getTextRange();
  }

  @Override
  public String getText(TextRange textRange) {
    return document.getText(textRange);
  }
}
