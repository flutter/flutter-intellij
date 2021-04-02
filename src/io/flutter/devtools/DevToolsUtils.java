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
    String page,
    boolean embed
  ) {
    return generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, embed, null);
  }

  public static String generateDevToolsUrl(
    String devtoolsHost,
    int devtoolsPort,
    String serviceProtocolUri,
    String page,
    boolean embed,
    String colorHexCode
  ) {
    final DevToolsUrl devToolsUrl = new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, embed, colorHexCode);
    return devToolsUrl.getUrlString();
  }

  public static String findWidgetId(String url) {
    final String searchFor = "inspectorRef=";
    final String[] split = url.split("&");
    for (String part : split) {
      if (part.startsWith(searchFor)) {
        return part.substring(searchFor.length());
      }
    }
    return null;
  }
}
