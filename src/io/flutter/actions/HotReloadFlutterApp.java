/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;

import java.lang.reflect.Method;

public class HotReloadFlutterApp extends FlutterAppAction {

  public static final String ID = "Flutter.HotReloadFlutterApp"; //NON-NLS

  public HotReloadFlutterApp(ObservatoryConnector connector, Computable<Boolean> isApplicable) {
    super(connector, FlutterBundle.message("app.reload.action.text"), FlutterBundle.message("app.reload.action.description"),
          FlutterIcons.Play2, isApplicable, ID);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ifReadyThen(() -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      boolean pauseAfterRestart = hasCapability("supports.pausePostRequest");
      getApp().performHotReload(pauseAfterRestart);
    });
  }

  private static boolean hasCapability(String featureId) {
    // return DartPluginCapabilities.isSupported(featureId);

    try {
      Class clazz = Class.forName("com.jetbrains.lang.dart.DartPluginCapabilities");
      Method method = clazz.getMethod("isSupported", String.class);
      Object result = method.invoke(null, featureId);
      return result instanceof Boolean && ((Boolean)result).booleanValue();
    }
    catch (Throwable t) {
      return false;
    }
  }
}
