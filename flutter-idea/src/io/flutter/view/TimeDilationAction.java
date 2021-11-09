/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.vmService.ServiceExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ComponentNotRegistered")
public class TimeDilationAction extends FlutterViewToggleableAction<Double> {
  public TimeDilationAction(@NotNull FlutterApp app, boolean showIcon) {
    super(app, showIcon ? AllIcons.Vcs.History : null, ServiceExtensions.slowAnimations);
  }

  @Override
  protected void perform(@Nullable AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put(
      "timeDilation",
      isSelected()
      ? ServiceExtensions.slowAnimations.getEnabledValue()
      : ServiceExtensions.slowAnimations.getDisabledValue());
    if (app.isSessionActive()) {
      app.callServiceExtension(ServiceExtensions.slowAnimations.getExtension(), params);
    }
  }
}
