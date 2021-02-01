/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.engine.PasswordStore;
import io.flutter.FlutterInitializer;

import java.io.File;
import java.nio.file.Paths;

import static com.teamdev.jxbrowser.engine.RenderingMode.HARDWARE_ACCELERATED;
import static com.teamdev.jxbrowser.engine.RenderingMode.OFF_SCREEN;

public class EmbeddedBrowserEngine {
  private static final Logger LOG = Logger.getInstance(EmbeddedBrowserEngine.class);
  private final Engine engine;

  public static EmbeddedBrowserEngine getInstance() {
    return ServiceManager.getService(EmbeddedBrowserEngine.class);
  }

  public EmbeddedBrowserEngine() {
    final String dataPath = JxBrowserManager.DOWNLOAD_PATH + File.separatorChar + "user-data";
    LOG.info("JxBrowser user data path: " + dataPath);

    final EngineOptions options =
      EngineOptions.newBuilder(SystemInfo.isWindows ? OFF_SCREEN : HARDWARE_ACCELERATED)
        .userDataDir(Paths.get(dataPath))
        .passwordStore(PasswordStore.BASIC)
        .build();

    Engine temp;
    try {
      temp = Engine.newInstance(options);
    } catch (Exception ex) {
      temp = null;
      LOG.error(ex);
      FlutterInitializer.getAnalytics().sendException(StringUtil.getThrowableText(ex), false);
    }
    engine = temp;

    ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
      @Override
      public boolean canExitApplication() {
        if (engine != null) {
          engine.close();
        }
        return true;
      }
    });
  }

  public Engine getEngine() {
    return engine;
  }
}
