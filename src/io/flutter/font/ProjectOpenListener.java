/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.font;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class ProjectOpenListener implements ProjectManagerListener {
  @Override
  public void projectOpened(@NotNull Project project) {
    // Ensure this isn't part of testing
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    MessageBusConnection connection = project.getMessageBus().connect();
    final FontPreviewProcessor service = ApplicationManager.getApplication().getService(FontPreviewProcessor.class);

    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        service.generate(project);
      }
    });
  }
}
