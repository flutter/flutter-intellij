/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;

public class JumpToSourceAction extends JumpToSourceActionBase {
  public JumpToSourceAction() {
    super("jumpToSource");
  }

  @Override
  protected XSourcePosition getSourcePosition(DiagnosticsNode node) {
    if (!node.hasCreationLocation()) {
      return null;
    }
    return node.getCreationLocation().getXSourcePosition();
  }

  @Override
  protected void startComputingSourcePosition(XValue value, XNavigatable navigatable) {
    // This case only typically works for Function objects where the source
    // position is available.
    value.computeSourcePosition(navigatable);
  }

  protected boolean isSupported(DiagnosticsNode diagnosticsNode) {
    // TODO(jacobr): also return true if the value of the DiagnosticsNode is
    // a Function object as we can get a source position through the
    // Observatory protocol in that case.
    return diagnosticsNode.hasCreationLocation();
  }
}
