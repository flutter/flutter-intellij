/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.analytics.Analytics;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

class BrowserTab {
  protected EmbeddedTab embeddedTab;
  protected Content content;
  protected CompletableFuture<DevToolsUrl> devToolsUrlFuture;
}


public abstract class EmbeddedBrowser {
  public static final String ANALYTICS_CATEGORY = "embedded-browser";

  protected final Map<@NotNull String, @NotNull BrowserTab> tabs = new HashMap<>();

  public abstract Logger logger();
  private final Analytics analytics;

  public EmbeddedBrowser(Project project) {
    this.analytics = FlutterInitializer.getAnalytics();
    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        for (final String tabName: tabs.keySet()) {
          final BrowserTab tab = tabs.get(tabName);
          if (tab.embeddedTab != null) {
            try {
              tab.embeddedTab.close();
            } catch (Exception ex) {
              logger().info(ex);
            }
          }
        }
        tabs.clear();
      }
    });
  }

  public void openPanel(ContentManager contentManager, String tabName, DevToolsUrl devToolsUrl, Consumer<String> onBrowserUnavailable) {
    final BrowserTab firstTab = tabs.get(tabName);
    if (firstTab == null) {
      try {
        openBrowserTabFor(tabName);
      } catch (Exception ex) {
        analytics.sendEvent(ANALYTICS_CATEGORY, "openBrowserTabFailed-" + this.getClass());
        onBrowserUnavailable.accept(ex.getMessage());
        return;
      }
    }

    final BrowserTab tab = tabs.get(tabName);
    // If the browser failed to start during setup, run unavailable callback.
    if (tab == null) {
      onBrowserUnavailable.accept("Browser failed to start during setup.");
      return;
    }

    // Multiple LoadFinished events can occur, but we only need to add content the first time.
    final AtomicBoolean contentLoaded = new AtomicBoolean(false);

    try {
      tab.embeddedTab.loadUrl(devToolsUrl.getUrlString());
    } catch (Exception ex) {
      tab.devToolsUrlFuture.completeExceptionally(ex);
      onBrowserUnavailable.accept(ex.getMessage());
      logger().info(ex);
      return;
    }

    tab.devToolsUrlFuture.complete(devToolsUrl);

    JComponent component = tab.embeddedTab.getTabComponent(contentManager);

    ApplicationManager.getApplication().invokeLater(() -> {
      if (contentManager.isDisposed()) {
        return;
      }

      contentManager.removeAllContents(false);

      for (final String otherTabName: tabs.keySet()) {
        if (otherTabName.equals(tabName)) {
          continue;
        }
        final BrowserTab browserTab = tabs.get(otherTabName);
        contentManager.addContent(browserTab.content);
      }

      tab.content = contentManager.getFactory().createContent(null, tabName, false);
      tab.content.setComponent(component);
      tab.content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
      // TODO(helin24): Use differentiated icons for each tab and copy from devtools toolbar.
      tab.content.setIcon(FlutterIcons.Phone);
      contentManager.addContent(tab.content);
      contentManager.setSelectedContent(tab.content, true);
    });
  }

  private BrowserTab openBrowserTabFor(String tabName) throws Exception {
    BrowserTab tab = new BrowserTab();
    tab.devToolsUrlFuture = new CompletableFuture<>();
    tab.embeddedTab = openEmbeddedTab();
    tabs.put(tabName, tab);
    return tab;
  }

  public abstract EmbeddedTab openEmbeddedTab() throws Exception;

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
    this.tabs.forEach((tabName, tab) -> {
      final CompletableFuture<DevToolsUrl> updatedUrlFuture = tab.devToolsUrlFuture.thenApply(devToolsUrl -> {
        if (devToolsUrl == null) {
          // This happens if URL has already been reset (e.g. new app has started). In this case [openPanel] should be called again instead of
          // modifying the URL.
          return null;
        }
        return newDevToolsUrlFn.apply(devToolsUrl);
      });

      AsyncUtils.whenCompleteUiThread(updatedUrlFuture, (devToolsUrl, ex) -> {
        if (ex != null) {
          logger().info(ex);
          FlutterInitializer.getAnalytics().sendExpectedException("browser-update", ex);
          return;
        }
        if (devToolsUrl == null) {
          // Reload is no longer needed - either URL has been reset or there has been no change.
          return;
        }
        tab.embeddedTab.loadUrl(devToolsUrl.getUrlString());
      });
    });
  }
}
