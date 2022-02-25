/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.jcef.JBCefBrowser;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.jxbrowser.JxBrowserManager;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmbeddedJcefBrowser extends EmbeddedBrowser {
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);

  private JBCefBrowser browser;

  @NotNull
  public static EmbeddedJcefBrowser getInstance(Project project) {
    return ServiceManager.getService(project, EmbeddedJcefBrowser.class);
  }

  private EmbeddedJcefBrowser(Project project) {
    browser = new JBCefBrowser();
    resetUrl();
  }

  public Logger logger() {
    return LOG;
  }

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
      browser.loadURL(devToolsUrl.getUrlString());
    } catch (Exception ex) {
      devToolsUrlFuture.completeExceptionally(ex);
      onBrowserUnavailable.run();
      LOG.info(ex);
      FlutterInitializer.getAnalytics().sendExpectedException("jcef-load", ex);
      return;
    }

    devToolsUrlFuture.complete(devToolsUrl);

    if (contentManager.isDisposed()) {
      return;
    }

    contentManager.removeAllContents(false);
    final Content content = contentManager.getFactory().createContent(null, tabName, false);
    browser.getComponent().setPreferredSize(new Dimension(contentManager.getComponent().getWidth(), contentManager.getComponent().getHeight()));
    content.setComponent(browser.getComponent());
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    // TODO(helin24): Use differentiated icons for each tab and copy from devtools toolbar.
    content.setIcon(FlutterIcons.Phone);
    contentManager.addContent(content);
  }

  public void navigateToUrl(String url) {
    browser.loadURL(url);
  }

}
