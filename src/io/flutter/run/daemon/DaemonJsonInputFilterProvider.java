/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.filters.ConsoleInputFilterProvider;
import com.intellij.execution.filters.InputFilter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DaemonJsonInputFilterProvider implements ConsoleInputFilterProvider {
  private static final boolean VERBOSE = false;

  @NotNull
  @Override
  public InputFilter[] getDefaultFilters(@NotNull Project project) {
    return new InputFilter[]{new DaemonJsonInputFilter()};
  }

  private static class DaemonJsonInputFilter implements InputFilter {
    @Nullable
    @Override
    public List<Pair<String, ConsoleViewContentType>> applyFilter(String text, ConsoleViewContentType contentType) {
      if (!VERBOSE) {
        final String trimmed = text.trim();

        if (trimmed.startsWith("[{") && trimmed.endsWith("}]")) {
          return Collections.singletonList(Pair.create(null, contentType));
        }

        if (trimmed.startsWith("Observatory listening on http") && !trimmed.contains("\n")) {
          return Collections.singletonList(Pair.create(null, contentType));
        }
      }

      return Collections.singletonList(Pair.create(text, contentType));
    }
  }
}
