/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;
import org.jetbrains.annotations.NotNull;

public class JumpToTypeSourceAction extends JumpToSourceActionBase {
  public JumpToTypeSourceAction() {
    super(InspectorActions.JUMP_TO_TYPE_SOURCE);
  }

  @Override
  protected void startComputingSourcePosition(XValue value, XNavigatable navigatable) {
    value.computeTypeSourcePosition(navigatable);
  }

  @Override
  protected XSourcePosition getSourcePosition(DiagnosticsNode node) {
    return null;
  }
}
