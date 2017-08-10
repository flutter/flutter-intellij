/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class OpenMemoryDashboardAction extends DumbAwareAction {

  private final @NotNull ObservatoryConnector myConnector;
  private final Computable<Boolean> myIsApplicable;

  public OpenMemoryDashboardAction(@NotNull final ObservatoryConnector connector, @NotNull final Computable<Boolean> isApplicable) {
    super(FlutterBundle.message("open.memorydashboard.action.text"), FlutterBundle.message("open.memorydashboard.action.description"),
          FlutterIcons.OpenMemoryDashboard);
    myConnector = connector;
    myIsApplicable = isApplicable;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabled(myIsApplicable.compute());
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    FlutterInitializer.sendAnalyticsAction(this);

    final String url = myConnector.getBrowserUrl();
    if (url != null) {
      OpenObservatoryAction.openInAnyChromeFamilyBrowser(url + "#/memory-dashboard?editor=IntelliJ");
    }
  }
}
