/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.server.OpenDartObservatoryUrlAction;
import icons.DartIcons;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;

public class OpenComputedUrlAction extends DumbAwareAction {
  private final Computable<String> myUrl;
  private final Computable<Boolean> myIsApplicable;

  public OpenComputedUrlAction(@NotNull final Computable<String> url, @NotNull final Computable<Boolean> isApplicable) {
    super(FlutterBundle.message("open.observatory.action.text"), FlutterBundle.message("open.observatory.action.description"), DartIcons.Dart_16);
    myUrl = url;
    myIsApplicable = isApplicable;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabled(myIsApplicable.compute());
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    OpenDartObservatoryUrlAction.openUrlInChromeFamilyBrowser(myUrl.compute());
  }
}
