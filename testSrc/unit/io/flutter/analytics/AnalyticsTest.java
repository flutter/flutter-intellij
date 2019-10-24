/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AnalyticsTest {
  private Analytics analytics;
  private MockAnalyticsTransport transport;

  @Before
  public void setUp() {
    transport = new MockAnalyticsTransport();

    analytics = new Analytics("123e4567-e89b-12d3-a456-426655440000", "1.0", "IntelliJ CE", "2016.3.2");
    analytics.setTransport(transport);
    analytics.setCanSend(true);
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void testSendScreenView() {
    analytics.sendScreenView("testAnalyticsPage");
    assertEquals(1, transport.sentValues.size());
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void testSendEvent() {
    analytics.sendEvent("flutter", "doctor");
    assertEquals(1, transport.sentValues.size());
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void testSendTiming() {
    analytics.sendTiming("perf", "reloadTime", 100);
    assertEquals(1, transport.sentValues.size());
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void testSendException() {
    final Throwable throwable = new UnsupportedOperationException("test operation");
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(stringWriter);
    throwable.printStackTrace(printWriter);

    analytics.sendException(stringWriter.toString().trim(), true);
    assertEquals(1, transport.sentValues.size());
  }

  @Test
  public void testOptOutDoesntSend() {
    analytics.setCanSend(false);
    analytics.sendScreenView("testAnalyticsPage");
    assertEquals(0, transport.sentValues.size());
  }

  private static class MockAnalyticsTransport implements Analytics.Transport {
    final public List<Map<String, String>> sentValues = new ArrayList<>();

    @Override
    public void send(String url, Map<String, String> values) {
      sentValues.add(values);
    }
  }
}
