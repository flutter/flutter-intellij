/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import org.jetbrains.annotations.NonNls;

public interface InspectorActions {
  @NonNls String JUMP_TO_TYPE_SOURCE = "Flutter.JumpToTypeSource";
  @NonNls String JUMP_TO_SOURCE = "Flutter.JumpToSource";
  @NonNls String INSPECT_WITH_DEBUGGER = "Flutter.InspectWithDebugger";
  @NonNls String INSPECT_ELEMENT_WITH_DEBUGGER = "Flutter.InspectElementWithDebugger";
}

