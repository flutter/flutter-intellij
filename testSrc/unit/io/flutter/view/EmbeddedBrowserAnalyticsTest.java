/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.content.ContentManager;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import io.flutter.analytics.AnalyticsConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EmbeddedBrowserAnalyticsTest extends BasePlatformTestCase {
  private static final String TOOL_WINDOW_ID = "Flutter Inspector";

  public void testOpenedReportsNewTabThenReusedTab() {
    final EmbeddedTab embeddedTab = mock(EmbeddedTab.class);
    when(embeddedTab.getTabComponent(any())).thenReturn(new JPanel());
    final TestBrowser browser = new TestBrowser(() -> embeddedTab);
    final ToolWindow toolWindow = toolWindow();

    final AnalyticsData first = assertOneElement(record(() -> open(browser, toolWindow)));
    assertEvent(first, AnalyticsConstants.EMBEDDED_BROWSER_OPENED, true, null);

    final AnalyticsData second = assertOneElement(record(() -> open(browser, toolWindow)));
    assertEvent(second, AnalyticsConstants.EMBEDDED_BROWSER_OPENED, false, null);
  }

  public void testTabCreateFailed() {
    final TestBrowser browser = new TestBrowser(() -> {
      throw new IllegalStateException("boom");
    });

    final AnalyticsData event = assertOneElement(record(() -> open(browser, toolWindow())));
    assertEvent(event, AnalyticsConstants.EMBEDDED_BROWSER_UNAVAILABLE, true, AnalyticsConstants.REASON_TAB_CREATE_FAILED);
  }

  public void testEngineNotReadyWhenNoEmbeddedTab() {
    final TestBrowser browser = new TestBrowser(() -> null);

    final AnalyticsData event = assertOneElement(record(() -> open(browser, toolWindow())));
    assertEvent(event, AnalyticsConstants.EMBEDDED_BROWSER_UNAVAILABLE, true, AnalyticsConstants.REASON_ENGINE_NOT_READY);
  }

  public void testLoadFailed() {
    final EmbeddedTab embeddedTab = mock(EmbeddedTab.class);
    doThrow(new IllegalStateException("boom")).when(embeddedTab).loadUrl(anyString());
    final TestBrowser browser = new TestBrowser(() -> embeddedTab);

    final AnalyticsData event = assertOneElement(record(() -> open(browser, toolWindow())));
    assertEvent(event, AnalyticsConstants.EMBEDDED_BROWSER_UNAVAILABLE, true, AnalyticsConstants.REASON_LOAD_FAILED);
  }

  private void assertEvent(@NotNull AnalyticsData event, @NotNull String id, boolean newTab, @Nullable String reason) {
    assertEquals(id, event.getId());
    assertEquals(AnalyticsConstants.EMBEDDED_BROWSER_TYPE, event.getData().get("type"));
    assertEquals(AnalyticsConstants.ENGINE_JCEF, event.getData().get("engine"));
    assertEquals(TOOL_WINDOW_ID, event.getData().get("feature"));
    assertEquals(newTab, event.getData().get("newTab"));
    if (reason == null) {
      assertFalse(event.getData().containsKey("reason"));
    }
    else {
      assertEquals(reason, event.getData().get("reason"));
    }
    // Events without a project are dropped by the Dart reporter.
    assertSame(getProject(), event.getProject());
  }

  private static void open(@NotNull TestBrowser browser, @NotNull ToolWindow toolWindow) {
    final BrowserUrlProvider urlProvider = mock(BrowserUrlProvider.class);
    when(urlProvider.getBrowserUrl()).thenReturn("http://127.0.0.1:9100/");
    browser.openPanel(toolWindow, "tab", null, urlProvider, error -> {});
  }

  private static @NotNull ToolWindow toolWindow() {
    final ContentManager contentManager = mock(ContentManager.class);
    // Skip the deferred UI work after a successful open.
    when(contentManager.isDisposed()).thenReturn(true);
    final ToolWindow toolWindow = mock(ToolWindow.class);
    when(toolWindow.getId()).thenReturn(TOOL_WINDOW_ID);
    when(toolWindow.getContentManager()).thenReturn(contentManager);
    return toolWindow;
  }

  private static List<AnalyticsData> record(@NotNull Runnable action) {
    final List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, action);
    return events;
  }

  private class TestBrowser extends EmbeddedBrowser {
    private final Supplier<EmbeddedTab> tabSupplier;

    TestBrowser(@NotNull Supplier<EmbeddedTab> tabSupplier) {
      super(getProject());
      this.tabSupplier = tabSupplier;
    }

    @Override
    public @NotNull Logger logger() {
      return Logger.getInstance(EmbeddedBrowserAnalyticsTest.class);
    }

    @Override
    protected @NotNull String analyticsEngine() {
      return AnalyticsConstants.ENGINE_JCEF;
    }

    @Override
    public EmbeddedTab openEmbeddedTab(ContentManager contentManager) {
      return tabSupplier.get();
    }
  }
}
