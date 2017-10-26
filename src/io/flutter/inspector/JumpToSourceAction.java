/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;

public class JumpToSourceAction extends JumpToSourceActionBase {
  @Override
  protected void startComputingSourcePosition(XValue value, XNavigatable navigatable) {
    value.computeSourcePosition(navigatable);
  }
}
