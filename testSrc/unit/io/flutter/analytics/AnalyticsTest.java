/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnalyticsTest extends TestCase {
  private Analytics analytics;
  private MockAnalyticsTransport transport;

  protected void setUp() throws Exception {
    super.setUp();

    transport = new MockAnalyticsTransport();

    analytics = new Analytics("123e4567-e89b-12d3-a456-426655440000", "1.0", "IntelliJ CE", "2016.3.2");
    analytics.setTransport(transport);
    analytics.setCanSend(true);
  }

  public void testSendScreenView() throws Exception {
    analytics.sendScreenView("testAnalyticsPage");
    assertEquals(1, transport.sentValues.size());
  }

  public void testSendEvent() throws Exception {
    analytics.sendEvent("flutter", "doctor");
    assertEquals(1, transport.sentValues.size());
  }

  public void testSendTiming() throws Exception {
    analytics.sendTiming("perf", "reloadTime", 100);
    assertEquals(1, transport.sentValues.size());
  }

  public void testSendException() throws Exception {
    analytics.sendException(new UnsupportedOperationException("test operation"), true);
    assertEquals(1, transport.sentValues.size());
  }

  public void testOptOutDoesntSend() throws Exception {
    analytics.setCanSend(false);
    analytics.sendScreenView("testAnalyticsPage");
    assertEquals(0, transport.sentValues.size());
  }

  private static class MockAnalyticsTransport implements Analytics.Transport {
    public List<Map<String, String>> sentValues = new ArrayList<>();

    @Override
    public void send(String url, Map<String, String> values) {
      sentValues.add(values);
    }
  }
}
