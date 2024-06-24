/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import io.flutter.view.EmbeddedBrowser;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class DevToolsExtensionsViewService extends DevToolsViewService {
  DevToolsExtensionsViewService(@NotNull Project project) {
    super(project);
  }
}
