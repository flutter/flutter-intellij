/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.engine.PasswordStore;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Paths;

import static com.teamdev.jxbrowser.engine.RenderingMode.HARDWARE_ACCELERATED;
import static com.teamdev.jxbrowser.engine.RenderingMode.OFF_SCREEN;

public class EmbeddedBrowserEngine {
  private static final @NotNull Logger LOG = Logger.getInstance(EmbeddedBrowserEngine.class);
  private final Engine engine;

  public static EmbeddedBrowserEngine getInstance() {
    return ApplicationManager.getApplication().getService(EmbeddedBrowserEngine.class);
  }

  public EmbeddedBrowserEngine() {
    final String dataPath = JxBrowserManager.DOWNLOAD_PATH + File.separatorChar + "user-data";
    LOG.info("JxBrowser user data path: " + dataPath);

    final EngineOptions.Builder optionsBuilder =
      EngineOptions.newBuilder(SystemInfo.isMac ? HARDWARE_ACCELERATED : OFF_SCREEN)
        .userDataDir(Paths.get(dataPath))
        .passwordStore(PasswordStore.BASIC)
        .addSwitch("--disable-features=NativeNotifications");

    if (SystemInfo.isLinux) {
      optionsBuilder.addSwitch("--force-device-scale-factor=1");
    }

    final EngineOptions options = optionsBuilder.build();

    Engine temp;
    try {
      temp = Engine.newInstance(options);
    }
    catch (Exception ex) {
      temp = null;
      LOG.info(ex);
    }
    engine = temp;

    ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
      @Override
      public boolean canExitApplication() {
        try {
          if (engine != null && !engine.isClosed()) {
            engine.close();
          }
        }
        catch (Exception ex) {
          LOG.info(ex);
        }
        return true;
      }
    });
  }

  public Engine getEngine() {
    return engine;
  }
}
