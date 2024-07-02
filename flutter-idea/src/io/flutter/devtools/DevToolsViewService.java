/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.openapi.project.Project;
import io.flutter.view.EmbeddedBrowser;
import org.jetbrains.annotations.NotNull;

/**
 * Common functionality for a DevTools view embedded in a tool window.
 */
public abstract class DevToolsViewService {
  private final Project myProject;
  private EmbeddedBrowser embeddedBrowser;
  private String vmServiceUri;

  DevToolsViewService(Project project) {
    this.myProject = project;
  }

  public void setEmbeddedBrowser(EmbeddedBrowser embeddedBrowser) {
    this.embeddedBrowser = embeddedBrowser;
    if (this.vmServiceUri != null) {
      updateVmServiceUri(this.vmServiceUri);
      this.vmServiceUri = null;
    }
  }

  public void updateVmServiceUri(@NotNull String vmServiceUri) {
    if (this.embeddedBrowser == null) {
      // Store the VM service URI for later
      this.vmServiceUri = vmServiceUri;
      return;
    }
    this.embeddedBrowser.updateVmServiceUri(vmServiceUri);
  }
}
