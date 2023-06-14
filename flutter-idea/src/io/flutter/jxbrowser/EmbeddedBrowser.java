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
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class EmbeddedBrowser {
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);

  @NotNull
  public static EmbeddedBrowser getInstance(@NotNull Project project) {
    return Objects.requireNonNull(project.getService(EmbeddedBrowser.class));
  }

  private final Map<@NotNull String, @NotNull Browser> browsers = new HashMap<>();
  private final Map<@NotNull String, @NotNull Content> contents = new HashMap<>();
  private CompletableFuture<DevToolsUrl> devToolsUrlFuture;

  private EmbeddedBrowser(Project project) {
    System.setProperty("jxbrowser.force.dpi.awareness", "1.0");
    System.setProperty("jxbrowser.logging.level", "DEBUG");
    System.setProperty("jxbrowser.logging.file", PathManager.getLogPath() + File.separatorChar + "jxbrowser.log");
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      System.setProperty("jxbrowser.logging.level", "ALL");
    }

    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        for (final String key: browsers.keySet()) {
          final Browser browser = browsers.get(key);
          if (browser != null) {
            try {
              browser.close();
            } catch (Exception ex) {
              LOG.info(ex);
            }
          }
        }
        browsers.clear();
        contents.clear();
      }
    });
  }

  private void openBrowserInstanceFor(String tabName) {
    try {
      final Engine engine = EmbeddedBrowserEngine.getInstance().getEngine();
      if (engine == null) {
        return;
      }
      final Browser newBrowser = engine.newBrowser();
      newBrowser.settings().enableTransparentBackground();
      newBrowser.on(ConsoleMessageReceived.class, event -> {
        final ConsoleMessage consoleMessage = event.consoleMessage();
        LOG.info("Browser message(" + consoleMessage.level().name() + "): " + consoleMessage.message());
      });
      final Browser oldBrowser = browsers.get(tabName);
      if (oldBrowser != null) {
        try {
          oldBrowser.close();
        } catch (Exception ex) {
          LOG.info(ex);
        }
      }
      browsers.put(tabName, newBrowser);
    }
    catch (UnsupportedRenderingModeException ex) {
      // Skip using a transparent background if an exception is thrown.
    }
    catch (Exception | Error ex) {
      LOG.info(ex);
      FlutterInitializer.getAnalytics().sendExpectedException("jxbrowser-setup", ex);
    }
    resetUrl();
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

  public void openPanel(ContentManager contentManager, @NotNull String tabName, DevToolsUrl devToolsUrl, Runnable onBrowserUnavailable) {
    final Browser firstBrowser = browsers.get(tabName);
    if (browsers.get(tabName) == null) {
      openBrowserInstanceFor(tabName);
    }
    // If the browser failed to start during setup, run unavailable callback.
    final Browser browser = browsers.get(tabName);
    if (browser == null) {
      onBrowserUnavailable.run();
      return;
    }

    // Multiple LoadFinished events can occur, but we only need to add content the first time.
    final AtomicBoolean contentLoaded = new AtomicBoolean(false);

    try {
      browser.navigation().loadUrl(devToolsUrl.getUrlString());
    }
    catch (Exception ex) {
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
        if (contents.get(tabName) != null) {
          contents.remove(tabName);
        }
        for (final Content content: contents.values()) {
          contentManager.addContent(content);
        }
        final Content previousContent = contents.get(tabName);
        if (previousContent != null) {
          contentManager.setSelectedContent(previousContent, true);
          return;
        }
        final Content content = contentManager.getFactory().createContent(null, tabName, false);

        // Creating Swing component for rendering web content
        // loaded in the given Browser instance.
        try {
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
          contentManager.setSelectedContent(content, true);
          contents.put(tabName, content);

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
        }
        catch (UnsatisfiedLinkError error) {
          // Sometimes this error occurs once but is resolved on IDE restart. In this case use our fallback option.
          onBrowserUnavailable.run();
          LOG.info(error);
          FlutterInitializer.getAnalytics().sendExpectedException("browser-view-load", error);
        }
      });
    });
  }

  public void updatePanelToWidget(String widgetId) {
    updateUrlAndReload(devToolsUrl -> {
      devToolsUrl.widgetId = widgetId;
      return devToolsUrl;
    });
  }

  public void updateColor(String newColor) {
    updateUrlAndReload(devToolsUrl -> {
      if (devToolsUrl.colorHexCode.equals(newColor)) {
        return null;
      }
      devToolsUrl.colorHexCode = newColor;
      return devToolsUrl;
    });
  }

  public void updateFontSize(float newFontSize) {
    updateUrlAndReload(devToolsUrl -> {
      if (devToolsUrl.fontSize.equals(newFontSize)) {
        return null;
      }
      devToolsUrl.fontSize = newFontSize;
      return devToolsUrl;
    });
  }

  private void updateUrlAndReload(Function<DevToolsUrl, DevToolsUrl> newDevToolsUrlFn) {
    final CompletableFuture<DevToolsUrl> updatedUrlFuture = devToolsUrlFuture.thenApply(devToolsUrl -> {
      if (devToolsUrl == null) {
        // This happens if URL has already been reset (e.g. new app has started). In this case [openPanel] should be called again instead of
        // modifying the URL.
        return null;
      }
      return newDevToolsUrlFn.apply(devToolsUrl);
    });

    AsyncUtils.whenCompleteUiThread(updatedUrlFuture, (devToolsUrl, ex) -> {
      if (ex != null) {
        LOG.info(ex);
        FlutterInitializer.getAnalytics().sendExpectedException("jxbrowser-update", ex);
        return;
      }
      if (devToolsUrl == null) {
        // Reload is no longer needed - either URL has been reset or there has been no change.
        return;
      }
      for (final Browser browser: browsers.values()) {
        browser.navigation().loadUrl(devToolsUrl.getUrlString());
      }
    });
  }
}
