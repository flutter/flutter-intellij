/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.run.profile;

import com.intellij.execution.Executor;
import com.intellij.openapi.util.text.StringUtil;
import icons.FlutterIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterProfileExecutor extends Executor {
  // We don't use 'profile' here in the chance that that would conflict with other
  // third-party IntelliJ plugins.
  public static final String EXECUTOR_ID = "flutter-profile";
  public static final String NAME = "Profile";

  @Override
  public String getToolWindowId() {
    return NAME;
  }

  @Override
  public Icon getToolWindowIcon() {
    return FlutterIcons.Profile; // or AllIcons.Actions.Profile
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return FlutterIcons.Profile; // or AllIcons.Actions.Profile
  }

  @Override
  public Icon getDisabledIcon() {
    return null;
  }

  @Override
  public String getDescription() {
    return NAME + " selected configuration";
  }

  @NotNull
  @Override
  public String getActionName() {
    return NAME;
  }

  @NotNull
  @Override
  public String getId() {
    return EXECUTOR_ID;
  }

  @NotNull
  @Override
  public String getStartActionText() {
    return NAME;
  }

  @Override
  public String getContextActionId() {
    return "ProfileClass";
  }

  @Override
  public String getHelpId() {
    return "ideaInterface.run";
  }

  @Override
  public String getStartActionText(String configurationName) {
    return "Run " +
           (StringUtil.isEmpty(configurationName) ? "" : " \'" + StringUtil.first(configurationName, 30, true) + "\'") +
           " (--profile mode)";
  }
}
