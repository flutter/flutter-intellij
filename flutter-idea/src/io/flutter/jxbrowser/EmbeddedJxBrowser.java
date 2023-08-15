/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.browser.UnsupportedRenderingModeException;
import com.teamdev.jxbrowser.browser.callback.AlertCallback;
import com.teamdev.jxbrowser.browser.callback.ConfirmCallback;
import com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback;
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.js.ConsoleMessage;
import com.teamdev.jxbrowser.navigation.event.LoadFinished;
import com.teamdev.jxbrowser.ui.KeyCode;
import com.teamdev.jxbrowser.ui.event.KeyPressed;
import com.teamdev.jxbrowser.view.swing.BrowserView;
import com.teamdev.jxbrowser.view.swing.callback.DefaultAlertCallback;
import com.teamdev.jxbrowser.view.swing.callback.DefaultConfirmCallback;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.settings.FlutterSettings;
import io.flutter.view.EmbeddedBrowser;
import io.flutter.view.EmbeddedTab;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

class EmbeddedJxBrowserTab implements EmbeddedTab {
  private final Engine engine;
  private Browser browser;
  private static final Logger LOG = Logger.getInstance(EmbeddedJxBrowserTab.class);

  public EmbeddedJxBrowserTab(Engine engine) {
    this.engine = engine;

    try {
      this.browser = engine.newBrowser();
      this.browser.settings().enableTransparentBackground();
      this.browser.on(ConsoleMessageReceived.class, event -> {
        final ConsoleMessage consoleMessage = event.consoleMessage();
        LOG.info("Browser message(" + consoleMessage.level().name() + "): " + consoleMessage.message());
      });
    }
    catch (UnsupportedRenderingModeException ex) {
      // Skip using a transparent background if an exception is thrown.
    }
    catch (Exception | Error ex) {
      LOG.info(ex);
      FlutterInitializer.getAnalytics().sendExpectedException("jxbrowser-setup", ex);
    }
  }

  @Override
  public void loadUrl(String url) {
    this.browser.navigation().loadUrl(url);
  }

  @Override
  public void close() {
    this.browser.close();
  }

  @Override
  public JComponent getTabComponent(ContentManager contentManager) {
    // Creating Swing component for rendering web content
    // loaded in the given Browser instance.
    final BrowserView view = BrowserView.newInstance(browser);
    view.setPreferredSize(new Dimension(contentManager.getComponent().getWidth(), contentManager.getComponent().getHeight()));

    // DevTools may show a confirm dialog to use a fallback version.
    browser.set(ConfirmCallback.class, new DefaultConfirmCallback(view));
    browser.set(AlertCallback.class, new DefaultAlertCallback(view));

    // This is for pulling up Chrome inspector for debugging purposes.
    browser.set(PressKeyCallback.class, params -> {
      KeyPressed keyEvent = params.event();
      boolean keyCodeC = keyEvent.keyCode() == KeyCode.KEY_CODE_J;
      boolean controlDown = keyEvent.keyModifiers().isControlDown();
      if (controlDown && keyCodeC) {
        browser.devTools().show();
      }
      return PressKeyCallback.Response.proceed();
    });

    return view;
  }
}

public class EmbeddedJxBrowser extends EmbeddedBrowser {
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);
  private final Engine engine;

  @NotNull
  public static EmbeddedJxBrowser getInstance(@NotNull Project project) {
    return Objects.requireNonNull(project.getService(EmbeddedJxBrowser.class));
  }

  private EmbeddedJxBrowser(Project project) {
    super(project);
    System.setProperty("jxbrowser.force.dpi.awareness", "1.0");
    System.setProperty("jxbrowser.logging.level", "DEBUG");
    System.setProperty("jxbrowser.logging.file", PathManager.getLogPath() + File.separatorChar + "jxbrowser.log");
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      System.setProperty("jxbrowser.logging.level", "ALL");
    }

    this.engine = EmbeddedBrowserEngine.getInstance().getEngine();
  }

  @Override
  public Logger logger() {
    return LOG;
  }

  @Override
  public EmbeddedTab openEmbeddedTab() {
    return new EmbeddedJxBrowserTab(engine);
  }
}
