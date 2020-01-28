/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * Mapper from a line column offset to a range of text associated with the identifier at an
 * offset.
 * <p>
 * For example, if there is a constructor call at the specified line and offset then the
 * name of the constructor called should be returned.
 */
public interface FileLocationMapper {
  @Nullable
  TextRange getIdentifierRange(int line, int column);

  @Nullable
  String getText(@Nullable TextRange textRange);

  String getPath();
}
