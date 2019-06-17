/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import io.flutter.sdk.FlutterSdkUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class DevToolsUtils {
  public static String generateDevToolsUrl(
    String devtoolsHost,
    int devtoolsPort,
    String serviceProtocolUri,
    String page
  ) {
    final List<String> params = new ArrayList<>();

    params.add("ide=" + FlutterSdkUtil.getFlutterHostEnvValue());

    if (serviceProtocolUri != null) {
      try {
        final String urlParam = URLEncoder.encode(serviceProtocolUri, "UTF-8");
        final String pageParam = page == null ? "" : ("#" + page);
        params.add("uri=" + urlParam + pageParam);
      }
      catch (UnsupportedEncodingException ignored) {
      }
    }
    return "http://" + devtoolsHost + ":" + devtoolsPort + "/?" + String.join("&", params);
  }
}
