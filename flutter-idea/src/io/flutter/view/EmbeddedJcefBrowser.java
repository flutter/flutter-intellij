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

import javax.swing.*;
import java.awt.Dimension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

class EmbeddedJcefBrowserTab implements EmbeddedTab {
  private JBCefBrowser browser;

  public EmbeddedJcefBrowserTab() {
    this.browser = new JBCefBrowser();
  }

  @Override
  public void loadUrl(String url) {
    browser.loadURL(url);
  }

  @Override
  public void close() {

  }

  @Override
  public JComponent getTabComponent(ContentManager contentManager) {
    browser.getComponent().setPreferredSize(new Dimension(contentManager.getComponent().getWidth(), contentManager.getComponent().getHeight()));
    return browser.getComponent();
  }
}

public class EmbeddedJcefBrowser extends EmbeddedBrowser {
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);

  public EmbeddedJcefBrowser(Project project) {
    super(project);
  }

  @NotNull
  public static EmbeddedJcefBrowser getInstance(Project project) {
    return ServiceManager.getService(project, EmbeddedJcefBrowser.class);
  }

  public Logger logger() {
    return LOG;
  }

  @Override
  public EmbeddedTab openEmbeddedTab() {
    return new EmbeddedJcefBrowserTab();
  }
}
