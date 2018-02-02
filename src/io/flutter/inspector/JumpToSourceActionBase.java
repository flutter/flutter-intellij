/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceValue;
import io.flutter.utils.AsyncUtils;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.concurrent.CompletableFuture;

public abstract class JumpToSourceActionBase extends InspectorTreeActionBase {
  @Override
  protected void perform(final DefaultMutableTreeNode node, final DiagnosticsNode diagnosticsNode, final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final XNavigatable navigatable = sourcePosition -> {
      if (sourcePosition != null) {
        //noinspection CodeBlock2Expr
        AppUIUtil.invokeOnEdt(() -> {
          sourcePosition.createNavigatable(project).navigate(true);
        }, project.getDisposed());
      }
    };

    final InspectorService inspectorService = diagnosticsNode.getInspectorService();
    final CompletableFuture<DartVmServiceValue> valueFuture =
      inspectorService.toDartVmServiceValueForSourceLocation(diagnosticsNode.getValueRef());
    AsyncUtils.whenCompleteUiThread(valueFuture, (DartVmServiceValue value, Throwable throwable) -> {
      if (throwable != null) {
        return;
      }
      startComputingSourcePosition(value, navigatable);
    });
  }

  protected abstract void startComputingSourcePosition(XValue value, XNavigatable navigatable);
}
