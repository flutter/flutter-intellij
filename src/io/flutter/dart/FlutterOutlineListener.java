/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.SourceEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.List;

public interface FlutterOutlineListener extends EventListener {
  void outlineUpdated(@NotNull final String filePath,
                      @NotNull final FlutterOutline outline,
                      @Nullable final String instrumentedCode);
}
