/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MockAnalyticsTransport implements Analytics.Transport {
  final public List<Map<String, String>> sentValues = new ArrayList<>();

  @Override
  public void send(String url, Map<String, String> values) {
    sentValues.add(values);
  }
}
