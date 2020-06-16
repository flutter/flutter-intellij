/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.assistant.whatsnew.actions;

import com.android.tools.idea.assistant.AssistActionHandler;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

public class SwitchToDevChannel implements AssistActionHandler {
  @Override
  public @NotNull String getId() {
    return "FlutterNewsAssistant.SwitchToDevChannel";
  }

  @Override
  public void handleAction(@NotNull ActionData actionData, @NotNull Project project) {
    FlutterInitializer.getAnalytics().sendEvent("intellij", "assistant-switch-dev");
  }
}
