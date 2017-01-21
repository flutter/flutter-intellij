/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.browsers.BrowserFamily;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("ComponentNotRegistered")
public class OpenObservatoryAction extends DumbAwareAction {
  @Nullable
  public static String convertWsToHttp(@Nullable String wsUrl) {
    if (wsUrl == null) {
      return null;
    }
    if (wsUrl.startsWith("ws:")) {
      wsUrl = "http:" + wsUrl.substring(3);
    }
    if (wsUrl.endsWith("/ws")) {
      wsUrl = wsUrl.substring(0, wsUrl.length() - 3);
    }
    return wsUrl;
  }

  private final Computable<String> myUrl;
  private final Computable<Boolean> myIsApplicable;

  public OpenObservatoryAction(@NotNull final Computable<String> url, @NotNull final Computable<Boolean> isApplicable) {
    super(FlutterBundle.message("open.observatory.action.text"), FlutterBundle.message("open.observatory.action.description"),
          FlutterIcons.OpenObservatory);
    myUrl = url;
    myIsApplicable = isApplicable;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabled(myIsApplicable.compute());
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    FlutterInitializer.sendActionEvent(this);

    openInAnyChromeFamilyBrowser(myUrl.compute());
  }

  public static void openInAnyChromeFamilyBrowser(@NotNull String url) {
    final List chromeBrowsers = WebBrowserManager.getInstance().getBrowsers((browser) -> browser.getFamily() == BrowserFamily.CHROME, true);
    BrowserLauncher.getInstance().browse(url, chromeBrowsers.isEmpty() ? null : (WebBrowser)chromeBrowsers.get(0));
  }
}
