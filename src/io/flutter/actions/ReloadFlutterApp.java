/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.run.daemon.FlutterApp;

import java.lang.reflect.Method;

@SuppressWarnings("ComponentNotRegistered")
public class ReloadFlutterApp extends FlutterAppAction implements FlutterRetargetAppAction.AppAction {
  public static final String ID = "Flutter.ReloadFlutterApp"; //NON-NLS
  public static final String TEXT = FlutterBundle.message("app.reload.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.reload.action.description");

  public ReloadFlutterApp(ObservatoryConnector connector, Computable<Boolean> isApplicable) {
    super(connector, TEXT, DESCRIPTION, FlutterIcons.ReloadBoth, isApplicable, ID);
    // Shortcut is associated with toolbar action.
    copyShortcutFrom(ActionManager.getInstance().getAction("Flutter.Toolbar.ReloadAction"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    actionPerformed(getApp());
  }

  @Override
  public void actionPerformed(FlutterApp app) {
    FlutterInitializer.sendAnalyticsAction(this);

    ifReadyThen(() -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      final boolean pauseAfterRestart = hasCapability("supports.pausePostRequest");
      app.performHotReload(pauseAfterRestart);
    });
  }

  private static boolean hasCapability(@SuppressWarnings("SameParameterValue") String featureId) {
    // return DartPluginCapabilities.isSupported(featureId);

    try {
      final Class clazz = Class.forName("com.jetbrains.lang.dart.DartPluginCapabilities");
      @SuppressWarnings("unchecked") final Method method = clazz.getMethod("isSupported", String.class);
      final Object result = method.invoke(null, featureId);
      return result instanceof Boolean && (Boolean)result;
    }
    catch (Throwable t) {
      return false;
    }
  }
}
