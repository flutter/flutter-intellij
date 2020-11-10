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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.browser.UnsupportedRenderingModeException;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.navigation.event.LoadFinished;
import com.teamdev.jxbrowser.view.swing.BrowserView;
import icons.FlutterIcons;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.teamdev.jxbrowser.engine.RenderingMode.HARDWARE_ACCELERATED;

public class EmbeddedBrowser {
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);

  @NotNull
  public static EmbeddedBrowser getInstance(Project project) {
    return ServiceManager.getService(project, EmbeddedBrowser.class);
  }

  private Browser browser;

  private EmbeddedBrowser(Project project) {
    final String dataPath = JxBrowserManager.DOWNLOAD_PATH + File.separatorChar + "user-data" + File.separatorChar + project.getName();
    LOG.info("JxBrowser user data path: " + dataPath);

    final EngineOptions options =
      EngineOptions.newBuilder(HARDWARE_ACCELERATED)
        .userDataDir(Paths.get(dataPath))
        .build();
    final Engine engine = Engine.newInstance(options);
    this.browser = engine.newBrowser();

    try {
      browser.settings().enableTransparentBackground();
    } catch (UnsupportedRenderingModeException ex) {
      // Skip using a transparent background if an exception is thrown.
    }
  }

  public void openPanel(ContentManager contentManager, String tabName, String url) {
    // Multiple LoadFinished events can occur, but we only need to add content the first time.
    final AtomicBoolean contentLoaded = new AtomicBoolean(false);

    browser.navigation().loadUrl(url);
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

        content.setComponent(view);
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
        // TODO(helin24): Use differentiated icons for each tab and copy from devtools toolbar.
        content.setIcon(FlutterIcons.Phone);
        contentManager.addContent(content);
      });
    });
  }
}
