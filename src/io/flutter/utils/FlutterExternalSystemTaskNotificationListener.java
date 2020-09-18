// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package io.flutter.utils;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class FlutterExternalSystemTaskNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {

  public FlutterExternalSystemTaskNotificationListener() {
  }

  @Override
  public void onSuccess(@NotNull ExternalSystemTaskId id) {
    if (id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT && id.getProjectSystemId() == GradleConstants.SYSTEM_ID) {
      final Project project = id.findProject();
      if (project != null) {
        AndroidUtils.checkDartSupport(project);
      }
    }
  }
}
