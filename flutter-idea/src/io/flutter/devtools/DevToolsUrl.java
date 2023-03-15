/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.sdk.FlutterSdkVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final FlutterSdkVersion flutterSdkVersion;
  private final FlutterSdkUtil sdkUtil;

  private final boolean canUseDevToolsPathUrl;

  public DevToolsUrl(String devtoolsHost,
                     int devtoolsPort,
                     String vmServiceUri,
                     String page,
                     boolean embed,
                     String colorHexCode,
                     Float fontSize,
                     @Nullable FlutterSdkVersion flutterSdkVersion,
                     WorkspaceCache workspaceCache) {
    this(devtoolsHost, devtoolsPort, vmServiceUri, page, embed, colorHexCode, fontSize, flutterSdkVersion, workspaceCache, new FlutterSdkUtil());
  }


  public DevToolsUrl(String devtoolsHost,
                     int devtoolsPort,
                     String vmServiceUri,
                     String page,
                     boolean embed,
                     String colorHexCode,
                     Float fontSize,
                     FlutterSdkVersion flutterSdkVersion,
                     WorkspaceCache workspaceCache,
                     FlutterSdkUtil flutterSdkUtil) {
    this.devtoolsHost = devtoolsHost;
    this.devtoolsPort = devtoolsPort;
    this.vmServiceUri = vmServiceUri;
    this.page = page;
    this.embed = embed;
    this.colorHexCode = colorHexCode;
    this.fontSize = fontSize;
    this.flutterSdkVersion = flutterSdkVersion;
    this.sdkUtil = flutterSdkUtil;

    if (workspaceCache != null && workspaceCache.isBazel()) {
      this.canUseDevToolsPathUrl = true;
    } else if (flutterSdkVersion != null) {
      this.canUseDevToolsPathUrl = flutterSdkVersion.canUseDevToolsPathUrls();
    } else {
      this.canUseDevToolsPathUrl = false;
    }
  }

  @NotNull

  public String getUrlString() {
    final List<String> params = new ArrayList<>();

    params.add("ide=" + sdkUtil.getFlutterHostEnvValue());
    if (page != null && !this.canUseDevToolsPathUrl) {
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
    if (this.canUseDevToolsPathUrl) {
      return "http://" + devtoolsHost + ":" + devtoolsPort + "/" + ( page != null ? page : "" )  + "?" + String.join("&", params);
    } else {
      return "http://" + devtoolsHost + ":" + devtoolsPort + "/#/?" + String.join("&", params);
    }
  }
}
