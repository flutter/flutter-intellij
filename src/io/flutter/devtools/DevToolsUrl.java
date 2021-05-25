/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import io.flutter.sdk.FlutterSdkUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class DevToolsUrl {
  private String devtoolsHost;
  private int devtoolsPort;
  private String vmServiceUri;
  private String page;
  private boolean embed;
  public String colorHexCode;
  public String widgetId;
  public Float fontSize;

  public DevToolsUrl(String devtoolsHost, int devtoolsPort, String vmServiceUri, String page, boolean embed, String colorHexCode, Float fontSize) {
    this.devtoolsHost = devtoolsHost;
    this.devtoolsPort = devtoolsPort;
    this.vmServiceUri = vmServiceUri;
    this.page = page;
    this.embed = embed;
    this.colorHexCode = colorHexCode;
    this.fontSize = fontSize;
  }

  public String getUrlString() {
    final List<String> params = new ArrayList<>();

    params.add("ide=" + FlutterSdkUtil.getFlutterHostEnvValue());
    if (page != null) {
      params.add("page=" + page);
    }
    if (colorHexCode != null) {
      params.add("backgroundColor=" + colorHexCode);
    }
    if (embed) {
      params.add("embed=true");
    }
    if (fontSize != null) {
      params.add("fontSize=" + fontSize);
    }

    if (vmServiceUri != null) {
      try {
        final String urlParam = URLEncoder.encode(vmServiceUri, "UTF-8");
        params.add("uri=" + urlParam);
      }
      catch (UnsupportedEncodingException ignored) {
      }
    }

    if (widgetId != null) {
      params.add("inspectorRef=" + widgetId);
    }
    return "http://" + devtoolsHost + ":" + devtoolsPort + "/?" + String.join("&", params);
  }
}
