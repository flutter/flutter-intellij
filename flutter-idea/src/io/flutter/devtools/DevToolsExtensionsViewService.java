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
public final class DevToolsExtensionsViewService {
  private final Project myProject;
  private EmbeddedBrowser embeddedBrowser;

  DevToolsExtensionsViewService(Project project) {
    this.myProject = project;
  }

  public void setEmbeddedBrowser(EmbeddedBrowser embeddedBrowser) {
    this.embeddedBrowser = embeddedBrowser;
  }

  public void updateVmServiceUri(@NotNull String vmServiceUri) {
    if (this.embeddedBrowser == null) return;
    this.embeddedBrowser.updateVmServiceUri(vmServiceUri);
  }
}
