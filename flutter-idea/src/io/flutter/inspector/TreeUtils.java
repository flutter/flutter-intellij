/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import javax.swing.tree.DefaultMutableTreeNode;

public class TreeUtils {
  public static DiagnosticsNode maybeGetDiagnostic(DefaultMutableTreeNode treeNode) {
    if (treeNode == null) {
      return null;
    }
    final Object userObject = treeNode.getUserObject();
    return (userObject instanceof DiagnosticsNode) ? (DiagnosticsNode)userObject : null;
  }
}
