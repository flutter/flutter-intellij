/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import io.flutter.sdk.FlutterSdkUtil;
import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class DevToolsUrlTest {

  @Test
  public void testGetUrlString() {
    DevToolsUrl.Builder builder = new DevToolsUrl.Builder()
      .setDevToolsHost("127.0.0.1")
      .setDevToolsPort(9100)
      .setVmServiceUri("http://127.0.0.1:12345/abc=")
      .setIdeName("Test IDE Name");

    // Mock FlutterSdkUtil to avoid calling real IntelliJ APIs which might fail in unit tests.
    builder.setFlutterSdkUtil(new FlutterSdkUtil() {
      @Override
      public String getFlutterHostEnvValue() {
        return "Test:IDE";
      }
    });

    DevToolsUrl devToolsUrl = builder.build();
    String url = devToolsUrl.getUrlString();

    // Test:IDE encoded becomes Test%3AIDE
    assertTrue(url.contains("ide=Test%3AIDE"));
    assertTrue(url.contains("dashTool=intellij-plugins"));

    // Test IDE Name encoded becomes Test+IDE+Name
    assertTrue(url.contains("dashIdeName=Test+IDE+Name"));
  }
}
