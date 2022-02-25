/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
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
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.js.ConsoleMessage;
import com.teamdev.jxbrowser.navigation.event.LoadFinished;
import com.teamdev.jxbrowser.view.swing.BrowserView;
import com.teamdev.jxbrowser.view.swing.callback.DefaultAlertCallback;
import com.teamdev.jxbrowser.view.swing.callback.DefaultConfirmCallback;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.settings.FlutterSettings;
import io.flutter.view.EmbeddedBrowser;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmbeddedJxBrowser extends EmbeddedBrowser {
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);

  @NotNull
  public static EmbeddedJxBrowser getInstance(Project project) {
    return ServiceManager.getService(project, EmbeddedJxBrowser.class);
  }

  private Browser browser;

  private EmbeddedJxBrowser(Project project) {
    System.setProperty("jxbrowser.force.dpi.awareness", "1.0");
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      System.setProperty("jxbrowser.logging.level", "ALL");
    }

    try {
      final Engine engine = EmbeddedBrowserEngine.getInstance().getEngine();
      if (engine == null) {
        return;
      }

      this.browser = engine.newBrowser();
      browser.settings().enableTransparentBackground();
      browser.on(ConsoleMessageReceived.class, event -> {
        final ConsoleMessage consoleMessage = event.consoleMessage();
        LOG.info("Browser message(" + consoleMessage.level().name() + "): " + consoleMessage.message());
      });
    } catch (UnsupportedRenderingModeException ex) {
      // Skip using a transparent background if an exception is thrown.
    } catch (Exception | Error ex) {
      LOG.info(ex);
      FlutterInitializer.getAnalytics().sendExpectedException("jxbrowser-setup", ex);
    }

    resetUrl();

    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        if (browser != null) {
          browser.close();
          browser = null;
        }
      }
    });
  }

  public Logger logger() {
    return LOG;
  }

  /**
   * This is to clear out a potentially old URL, i.e. a URL from an app that's no longer running.
   */
  public void resetUrl() {
    if (devToolsUrlFuture != null && !devToolsUrlFuture.isDone()) {
      devToolsUrlFuture.complete(null);
    }
    this.devToolsUrlFuture = new CompletableFuture<>();
  }

  public void openPanel(ContentManager contentManager, String tabName, DevToolsUrl devToolsUrl, Runnable onBrowserUnavailable) {
    // If the browser failed to start during setup, run unavailable callback.
    if (browser == null) {
      onBrowserUnavailable.run();
      return;
    }

    // Multiple LoadFinished events can occur, but we only need to add content the first time.
    final AtomicBoolean contentLoaded = new AtomicBoolean(false);

    try {
      browser.navigation().loadUrl(devToolsUrl.getUrlString());
    } catch (Exception ex) {
      devToolsUrlFuture.completeExceptionally(ex);
      onBrowserUnavailable.run();
      LOG.info(ex);
      FlutterInitializer.getAnalytics().sendExpectedException("jxbrowser-load", ex);
      return;
    }

    devToolsUrlFuture.complete(devToolsUrl);
    browser.navigation().on(LoadFinished.class, event -> {
      if (!contentLoaded.compareAndSet(false, true)) {
        return;
      }
      ApplicationManager.getApplication().invokeLater(() -> {
        if (contentManager.isDisposed()) {
          return;
        }

        contentManager.removeAllContents(false);
        final Content content = contentManager.getFactory().createContent(null, tabName, false);

        // Creating Swing component for rendering web content
        // loaded in the given Browser instance.
        final BrowserView view = BrowserView.newInstance(browser);
        view.setPreferredSize(new Dimension(contentManager.getComponent().getWidth(), contentManager.getComponent().getHeight()));

        // DevTools may show a confirm dialog to use a fallback version.
        browser.set(ConfirmCallback.class, new DefaultConfirmCallback(view));
        browser.set(AlertCallback.class, new DefaultAlertCallback(view));

        content.setComponent(view);
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
        // TODO(helin24): Use differentiated icons for each tab and copy from devtools toolbar.
        content.setIcon(FlutterIcons.Phone);
        contentManager.addContent(content);
      });
    });
  }

  public void navigateToUrl(String url) {
    browser.navigation().loadUrl(url);
  }
}
