/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

class TimeDilationAction extends FlutterViewToggleableAction {
  TimeDilationAction(@NotNull FlutterApp app, boolean showIcon) {
    super(app, "Enable Slow Animations", null, showIcon ? AllIcons.Vcs.History : null);

    setExtensionCommand("ext.flutter.timeDilation");
  }

  @Override
  protected void perform(@Nullable AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put("timeDilation", isSelected() ? 5.0 : 1.0);
    if (app.isSessionActive()) {
      app.callServiceExtension("ext.flutter.timeDilation", params);
    }
  }

  @Override
  public void handleAppRestarted() {
    if (isSelected()) {
      perform(null);
    }
  }
}
