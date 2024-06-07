/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.sdk.FlutterSdkVersion;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DevToolsUrl {
  private String devToolsHost;
  private int devToolsPort;
  private String vmServiceUri;
  private String page;
  private boolean embed;
  public String colorHexCode;
  public Boolean isBright;
  public String widgetId;
  public Float fontSize;
  private final FlutterSdkVersion flutterSdkVersion;
  private final FlutterSdkUtil sdkUtil;

  private final boolean canUseDevToolsPathUrl;
  private final boolean canUseMultiEmbed;

  public final DevToolsIdeFeature ideFeature;

  private final DevToolsUtils devToolsUtils;

  public static class Builder {
    private String devToolsHost;

    private int devToolsPort;
    private String vmServiceUri;
    private String page;
    private Boolean embed;
    private String widgetId;
    private Float fontSize;

    private FlutterSdkVersion flutterSdkVersion;
    private WorkspaceCache workspaceCache;
    private DevToolsIdeFeature ideFeature;

    private DevToolsUtils devToolsUtils;

    private FlutterSdkUtil flutterSdkUtil;

    public Builder() {}

    public Builder setDevToolsHost(String devToolsHost) {
      this.devToolsHost = devToolsHost;
      return this;
    }

    public Builder setDevToolsPort(int devToolsPort) {
      this.devToolsPort = devToolsPort;
      return this;
    }

    public Builder setVmServiceUri(String vmServiceUri) {
      this.vmServiceUri = vmServiceUri;
      return this;
    }

    public Builder setPage(String page) {
      this.page = page;
      return this;
    }

    public Builder setEmbed(Boolean embed) {
      this.embed = embed;
      return this;
    }

    public Builder setWidgetId(String widgetId) {
      this.widgetId = widgetId;
      return this;
    }

    public Builder setFontSize(Float fontSize) {
      this.fontSize = fontSize;
      return this;
    }

    public Builder setDevToolsUtils(DevToolsUtils devToolsUtils) {
      this.devToolsUtils = devToolsUtils;
      return this;
    }

    public Builder setFlutterSdkVersion(FlutterSdkVersion sdkVersion) {
      this.flutterSdkVersion = sdkVersion;
      return this;
    }

    public Builder setWorkspaceCache(WorkspaceCache workspaceCache) {
      this.workspaceCache = workspaceCache;
      return this;
    }

    public Builder setIdeFeature(DevToolsIdeFeature ideFeature) {
      this.ideFeature = ideFeature;
      return this;
    }

    public Builder setFlutterSdkUtil(FlutterSdkUtil flutterSdkUtil) {
      this.flutterSdkUtil = flutterSdkUtil;
      return this;
    }

    public DevToolsUrl build() {
      if (devToolsUtils == null) {
        devToolsUtils = new DevToolsUtils();
      }
      if (flutterSdkUtil == null) {
        flutterSdkUtil = new FlutterSdkUtil();
      }
      if (embed == null) {
        embed = false;
      }
      return new DevToolsUrl(this);
    }
  }

  private DevToolsUrl(Builder builder) {
    this.devToolsHost = builder.devToolsHost;
    this.devToolsPort = builder.devToolsPort;
    this.vmServiceUri = builder.vmServiceUri;
    this.page = builder.page;
    this.embed = builder.embed;
    this.devToolsUtils = builder.devToolsUtils;
    if (builder.embed) {
      this.colorHexCode = builder.devToolsUtils.getColorHexCode();
      this.isBright = builder.devToolsUtils.getIsBackgroundBright();
      this.fontSize = builder.devToolsUtils.getFontSize();
    }
    this.widgetId = builder.widgetId;
    this.flutterSdkVersion = builder.flutterSdkVersion;
    this.ideFeature = builder.ideFeature;
    this.sdkUtil = builder.flutterSdkUtil;

    if (builder.workspaceCache != null && builder.workspaceCache.isBazel()) {
      this.canUseDevToolsPathUrl = true;
      this.canUseMultiEmbed = true;
    } else if (flutterSdkVersion != null) {
      this.canUseDevToolsPathUrl = flutterSdkVersion.canUseDevToolsPathUrls();
      this.canUseMultiEmbed = flutterSdkVersion.canUseDevToolsMultiEmbed();
    } else {
      this.canUseDevToolsPathUrl = false;
      this.canUseMultiEmbed = false;
    }
  }

  @NotNull
  public String getUrlString() {
    final List<String> params = new ArrayList<>();

    String ideValue = sdkUtil.getFlutterHostEnvValue();
    params.add("ide=" + (ideValue == null ? "IntelliJPluginUnknown" : ideValue));
    if (page != null && !this.canUseDevToolsPathUrl) {
      params.add("page=" + page);
    }
    if (colorHexCode != null) {
      params.add("backgroundColor=" + colorHexCode);
    }
    if (isBright != null) {
      params.add("theme=" + (isBright ? "light" : "dark"));
    }
    if (embed) {
      params.add(this.canUseMultiEmbed ? "embedMode=one" : "embed=true");
    }
    if (fontSize != null) {
      params.add("fontSize=" + fontSize);
    }
    if (ideFeature != null) {
      params.add("ideFeature=" + ideFeature.value);
    }
    if (vmServiceUri != null) {
      final String urlParam = URLEncoder.encode(vmServiceUri, StandardCharsets.UTF_8);
      params.add("uri=" + urlParam);
    }
    if (widgetId != null) {
      params.add("inspectorRef=" + widgetId);
    }
    if (this.canUseDevToolsPathUrl) {
      return "http://" + devToolsHost + ":" + devToolsPort + "/" + (page != null ? page : "") + "?" + String.join("&", params);
    }
    else {
      return "http://" + devToolsHost + ":" + devToolsPort + "/#/?" + String.join("&", params);
    }
  }

  public void maybeUpdateColor() {
    final String newColor = devToolsUtils.getColorHexCode();
    if (colorHexCode == newColor) {
      return;
    }

    colorHexCode = newColor;
    isBright = devToolsUtils.getIsBackgroundBright();
  }
}
