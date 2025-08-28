/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.JBUI;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.LabelInput;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * There is one instance of embedded browser across the project, but it manages tabs across multiple tool
 * windows. Each tab can display the contents of an independent URL.
 */
public abstract class EmbeddedBrowser {
  static public class BrowserTab {
    protected EmbeddedTab embeddedTab;
    protected Content content;
    protected CompletableFuture<DevToolsUrl> devToolsUrlFuture;
    public ContentManager contentManager;
  }

  public static final String ANALYTICS_CATEGORY = "embedded-browser";

  protected final Map<@NotNull String, Map<@NotNull String, @NotNull BrowserTab>> windows = new HashMap<>();

  public abstract @NotNull Logger logger();

  private DevToolsUrl url;

  public EmbeddedBrowser(Project project) {
    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        for (final String window : windows.keySet()) {
          final Map<String, BrowserTab> tabs = windows.get(window);
          for (final String tabName : tabs.keySet()) {
            final BrowserTab tab = tabs.get(tabName);
            if (tab.embeddedTab != null) {
              try {
                tab.embeddedTab.close();
              }
              catch (Exception ex) {
                logger().info(ex);
              }
            }
          }
          tabs.clear();
        }
        windows.clear();
      }
    });
  }

  public void openPanel(@NotNull ToolWindow toolWindow,
                        @NotNull String tabName,
                        @Nullable Icon tabIcon,
                        @NotNull DevToolsUrl devToolsUrl,
                        @NotNull Consumer<String> onBrowserUnavailable) {
    openPanel(toolWindow, tabName, tabIcon, devToolsUrl, onBrowserUnavailable, null);
  }

  public void openPanel(@NotNull ToolWindow toolWindow,
                        @NotNull String tabName,
                        @Nullable Icon tabIcon,
                        @NotNull DevToolsUrl devToolsUrl,
                        @NotNull Consumer<String> onBrowserUnavailable,
                        @Nullable String warningMessage) {
    this.url = devToolsUrl;
    Map<String, BrowserTab> tabs = windows.computeIfAbsent(toolWindow.getId(), k -> new HashMap<>());

    final BrowserTab firstTab = tabs.get(tabName);
    if (firstTab == null) {
      try {
        tabs.put(tabName, openBrowserTabFor(tabName, toolWindow));
      }
      catch (Exception ex) {
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
      final String url = devToolsUrl.getUrlString();
      tab.embeddedTab.loadUrl(url);
    }
    catch (Exception ex) {
      tab.devToolsUrlFuture.completeExceptionally(ex);
      onBrowserUnavailable.accept(ex.getMessage());
      logger().info(ex);
      return;
    }

    tab.devToolsUrlFuture.complete(devToolsUrl);

    JComponent component = tab.embeddedTab.getTabComponent(tab.contentManager);

    OpenApiUtils.safeInvokeLater(() -> {
      if (tab.contentManager.isDisposed()) {
        return;
      }

      tab.contentManager.removeAllContents(false);

      for (final String otherTabName : tabs.keySet()) {
        if (Objects.equals(otherTabName, tabName)) {
          continue;
        }
        final BrowserTab browserTab = tabs.get(otherTabName);
        tab.contentManager.addContent(browserTab.content);
      }

      tab.content = tab.contentManager.getFactory().createContent(null, tabName, false);
      tab.content.setComponent(component);

      tab.content.putUserData(ToolWindow.SHOW_CONTENT_ICON, tabIcon != null);
      if (tabIcon != null) {
        tab.content.setIcon(tabIcon);
      }

      final JPanel panel = new JPanel(new BorderLayout());

      if (warningMessage != null) {
        panel.add(new ViewUtils().warningLabel(warningMessage), BorderLayout.NORTH);
      }

      panel.add(tab.content.getComponent(), BorderLayout.CENTER);
      final Content panelContent = tab.contentManager.getFactory().createContent(panel, null, false);

      tab.contentManager.addContent(panelContent);
      tab.contentManager.setSelectedContent(tab.content, true);
      tab.embeddedTab.matchIdeZoom();
    });
  }

  private void openLinkInStandardBrowser(ContentManager contentManager) {
    verifyEventDispatchThread();
    if (url == null) {
      showMessage("The URL is invalid.", contentManager);
    }
    else {
      BrowserLauncher.getInstance().browse(
        url.getUrlString(),
        null
      );

      showMessage("The URL has been opened in the browser.", contentManager);
    }
  }

  protected void verifyEventDispatchThread() {
    assert (SwingUtilities.isEventDispatchThread());
  }

  protected void showMessageWithUrlLink(@NotNull String message, ContentManager contentManager) {
    final List<LabelInput> labels = new ArrayList<>();
    labels.add(new LabelInput(message));
    showLabelsWithUrlLink(labels, contentManager);
  }

  protected void showLabelsWithUrlLink(@NotNull List<LabelInput> labels, ContentManager contentManager) {
    labels.add(new LabelInput("Open DevTools in the browser?", (linkLabel, data) -> {
      openLinkInStandardBrowser(contentManager);
    }));

    showLabels(labels, contentManager);
  }

  protected void showMessage(@NotNull String message, ContentManager contentManager) {
    final List<LabelInput> labels = new ArrayList<>();
    labels.add(new LabelInput(message));
    showLabels(labels, contentManager);
  }

  protected void showLabels(List<LabelInput> labels, ContentManager contentManager) {
    final JPanel panel = new JPanel(new GridLayout(0, 1));

    for (LabelInput input : labels) {
      if (input.listener == null) {
        final JLabel descriptionLabel = new JLabel("<html>" + input.text + "</html>");
        descriptionLabel.setBorder(JBUI.Borders.empty(5));
        descriptionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(descriptionLabel, BorderLayout.NORTH);
      }
      else {
        final LinkLabel<String> linkLabel = new LinkLabel<>("<html>" + input.text + "</html>", null);
        linkLabel.setBorder(JBUI.Borders.empty(5));
        linkLabel.setListener(input.listener, null);
        linkLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(linkLabel, BorderLayout.SOUTH);
      }
    }

    final JPanel center = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE));
    center.add(panel);
    replacePanelLabel(center, contentManager);
  }

  private void replacePanelLabel(JComponent label, ContentManager contentManager) {
    new ViewUtils().replacePanelLabel(contentManager, label);
  }

  private BrowserTab openBrowserTabFor(String tabName, ToolWindow toolWindow) {
    BrowserTab tab = new BrowserTab();
    tab.devToolsUrlFuture = new CompletableFuture<>();
    tab.embeddedTab = openEmbeddedTab(toolWindow.getContentManager());
    tab.contentManager = toolWindow.getContentManager();
    return tab;
  }

  public abstract EmbeddedTab openEmbeddedTab(ContentManager contentManager);

  public void updatePanelToWidget(String widgetId) {
    updateUrlAndReload(devToolsUrl -> {
      devToolsUrl.widgetId = widgetId;
      return devToolsUrl;
    });
  }

  public void updateVmServiceUri(@NotNull String newVmServiceUri) {
    updateUrlAndReload(devToolsUrl -> {
      if (newVmServiceUri.equals(devToolsUrl.vmServiceUri)) {
        return null;
      }
      devToolsUrl.vmServiceUri = newVmServiceUri;
      return devToolsUrl;
    });
  }

  // This will refresh all the browser tabs within a tool window (e.g. if there are multiple apps running and the inspector tool window is
  // refreshed, an inspector tab will refresh for each app.)
  // TODO(helin24): Consider allowing refresh for single browser tabs within tool windows.
  public void refresh(@Nullable String toolWindowId) {
    Map<String, BrowserTab> tabs = windows.get(toolWindowId);

    if (tabs == null) {
      return;
    }

    tabs.forEach((tabName, tab) -> {
      if (tab == null || tab.devToolsUrlFuture == null) return;
      tab.devToolsUrlFuture.thenAccept(devToolsUrl -> {
        if (devToolsUrl == null) return;
        devToolsUrl.maybeUpdateColor();
        tab.embeddedTab.loadUrl(devToolsUrl.getUrlString());
        tab.embeddedTab.matchIdeZoom();
      });
    });
  }

  private void updateUrlAndReload(Function<DevToolsUrl, DevToolsUrl> newDevToolsUrlFn) {
    this.windows.forEach((window, tabs) -> {
      tabs.forEach((tabName, tab) -> {
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
            return;
          }
          if (devToolsUrl == null) {
            // Reload is no longer needed - either URL has been reset or there has been no change.
            return;
          }
          tab.embeddedTab.loadUrl(devToolsUrl.getUrlString());
          tab.embeddedTab.matchIdeZoom();
        });
      });
    });
  }
}
