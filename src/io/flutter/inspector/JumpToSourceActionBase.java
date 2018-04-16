/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceValue;
import io.flutter.FlutterInitializer;
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.concurrent.CompletableFuture;

public abstract class JumpToSourceActionBase extends InspectorTreeActionBase {

  /**
   * A string id intended for tracking usage analytics.
   */
  @NotNull
  private final String id;

  JumpToSourceActionBase(@NotNull String id) {
    this.id = id;
  }

  @Override
  protected void perform(final DefaultMutableTreeNode node, final DiagnosticsNode diagnosticsNode, final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    FlutterInitializer.getAnalytics().sendEvent("inspector", id);

    final XNavigatable navigatable = sourcePosition -> {
      if (sourcePosition != null) {
        //noinspection CodeBlock2Expr
        AppUIUtil.invokeOnEdt(() -> {
          sourcePosition.createNavigatable(project).navigate(true);
        }, project.getDisposed());
      }
    };
    final XSourcePosition sourcePosition = getSourcePosition(diagnosticsNode);
    if (sourcePosition != null) {
      // Source position is available immediately.
      navigatable.setSourcePosition(sourcePosition);
      return;
    }
    // We have to get a DartVmServiceValue to compute the source position.
    final InspectorService.ObjectGroup inspectorService = diagnosticsNode.getInspectorService();
    final CompletableFuture<DartVmServiceValue> valueFuture =
      inspectorService.toDartVmServiceValueForSourceLocation(diagnosticsNode.getValueRef());
    AsyncUtils.whenCompleteUiThread(valueFuture, (DartVmServiceValue value, Throwable throwable) -> {
      if (throwable != null) {
        return;
      }
      startComputingSourcePosition(value, navigatable);
    });
  }

  /**
   * Implement if the source position is available directly from the diagnostics node.
   */
  protected abstract XSourcePosition getSourcePosition(DiagnosticsNode node);

  /**
   * Implement if the source position is available from the XValue of the diagnostics node.
   */
  protected abstract void startComputingSourcePosition(XValue value, XNavigatable navigatable);
}
