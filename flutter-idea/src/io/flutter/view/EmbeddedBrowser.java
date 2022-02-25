/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.content.ContentManager;
import io.flutter.FlutterInitializer;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.jxbrowser.EmbeddedJxBrowser;
import io.flutter.utils.AsyncUtils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class EmbeddedBrowser {
  protected CompletableFuture<DevToolsUrl> devToolsUrlFuture;

  public abstract Logger logger();
  public abstract void resetUrl();

  public abstract void openPanel(ContentManager contentManager, String tabName, DevToolsUrl devToolsUrl, Runnable onBrowserUnavailable);

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
        logger().info(ex);
        FlutterInitializer.getAnalytics().sendExpectedException("jxbrowser-update", ex);
        return;
      }
      if (devToolsUrl == null) {
        // Reload is no longer needed - either URL has been reset or there has been no change.
        return;
      }
      navigateToUrl(devToolsUrl.getUrlString());
    });
  }

  protected abstract void navigateToUrl(String url);
}
