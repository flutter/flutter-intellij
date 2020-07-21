/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.view.swing.BrowserView;
import icons.FlutterIcons;

import java.awt.*;

import static com.teamdev.jxbrowser.engine.RenderingMode.HARDWARE_ACCELERATED;

public class EmbeddedBrowser {
  public void openPanel(ContentManager contentManager, String tabName, String url) {
    EngineOptions options =
      EngineOptions.newBuilder(HARDWARE_ACCELERATED).build();
    Engine engine = Engine.newInstance(options);
    Browser browser = engine.newBrowser();

    if (contentManager.isDisposed()) {
      return;
    }
    final Content content = contentManager.getFactory().createContent(null, tabName, false);

    // Creating Swing component for rendering web content
    // loaded in the given Browser instance.
    BrowserView view = BrowserView.newInstance(browser);
    view.setPreferredSize(new Dimension(contentManager.getComponent().getWidth(), contentManager.getComponent().getHeight()));

    content.setComponent(view);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    // TODO(helin24): Use differentiated icons for each tab and copy from devtools toolbar.
    content.setIcon(FlutterIcons.Phone);
    contentManager.addContent(content);

    browser.navigation().loadUrl(url);
  }
}
