/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.sdk.FlutterSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DevToolsUrl {
  private String devToolsHost;
  private int devToolsPort;
  public String vmServiceUri;
  private String page;
  private boolean embed;
  public String colorHexCode;
  public Boolean isBright;
  public String widgetId;
  public String hide;
  private final FlutterSdkVersion flutterSdkVersion;
  private final FlutterSdkUtil sdkUtil;

  private final boolean canUseMultiEmbed;

  public final DevToolsIdeFeature ideFeature;

  @NotNull private final DevToolsUtils devToolsUtils;

  public static class Builder {
    private @Nullable String devToolsHost;

    private int devToolsPort;
    private @Nullable String vmServiceUri;
    private String page;
    private Boolean embed;
    private String widgetId;
    private String hide;

    private @Nullable FlutterSdkVersion flutterSdkVersion;
    private WorkspaceCache workspaceCache;
    private DevToolsIdeFeature ideFeature;

    private DevToolsUtils devToolsUtils;

    private FlutterSdkUtil flutterSdkUtil;

    public Builder() {
    }

    @NotNull
    public Builder setDevToolsHost(@Nullable String devToolsHost) {
      this.devToolsHost = devToolsHost;
      return this;
    }

    @NotNull
    public Builder setDevToolsPort(int devToolsPort) {
      this.devToolsPort = devToolsPort;
      return this;
    }

    @NotNull
    public Builder setVmServiceUri(@Nullable String vmServiceUri) {
      this.vmServiceUri = vmServiceUri;
      return this;
    }

    @NotNull
    public Builder setPage(String page) {
      this.page = page;
      return this;
    }

    @NotNull
    public Builder setEmbed(Boolean embed) {
      this.embed = embed;
      return this;
    }

    @NotNull
    public Builder setWidgetId(String widgetId) {
      this.widgetId = widgetId;
      return this;
    }

    @NotNull
    public Builder setHide(String hide) {
      this.hide = hide;
      return this;
    }

    @NotNull
    public Builder setDevToolsUtils(DevToolsUtils devToolsUtils) {
      this.devToolsUtils = devToolsUtils;
      return this;
    }

    @NotNull
    public Builder setFlutterSdkVersion(@Nullable FlutterSdkVersion sdkVersion) {
      this.flutterSdkVersion = sdkVersion;
      return this;
    }

    @NotNull
    public Builder setWorkspaceCache(WorkspaceCache workspaceCache) {
      this.workspaceCache = workspaceCache;
      return this;
    }

    @NotNull
    public Builder setIdeFeature(DevToolsIdeFeature ideFeature) {
      this.ideFeature = ideFeature;
      return this;
    }

    @NotNull
    public Builder setFlutterSdkUtil(FlutterSdkUtil flutterSdkUtil) {
      this.flutterSdkUtil = flutterSdkUtil;
      return this;
    }

    @NotNull
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
    }
    this.hide = builder.hide;
    this.widgetId = builder.widgetId;
    this.flutterSdkVersion = builder.flutterSdkVersion;
    this.ideFeature = builder.ideFeature;
    this.sdkUtil = builder.flutterSdkUtil;

    if (builder.workspaceCache != null && builder.workspaceCache.isBazel()) {
      this.canUseMultiEmbed = true;
    }
    else if (flutterSdkVersion != null) {
      this.canUseMultiEmbed = flutterSdkVersion.canUseDevToolsMultiEmbed();
    }
    else {
      this.canUseMultiEmbed = false;
    }
  }

  @SuppressWarnings("HttpUrlsUsage")
  @NotNull
  public String getUrlString() {
    final List<String> params = new ArrayList<>();

    String ideValue = sdkUtil.getFlutterHostEnvValue();
    params.add("ide=" + (ideValue == null ? "IntelliJPluginUnknown" : ideValue));
    if (colorHexCode != null) {
      params.add("backgroundColor=" + colorHexCode);
    }
    if (isBright != null) {
      params.add("theme=" + (isBright ? "light" : "dark"));
    }
    if (embed) {
      if (!this.canUseMultiEmbed) {
        // This is for older versions of DevTools that do not support embed= one vs. many.
        params.add("embed=true");
      }
      else {
        if (hide != null) {
          // If we are using the hide param, we can assume that we are trying to embed multiple tabs.
          params.add("embedMode=many");
          params.add("hide=" + hide);
        }
        else {
          params.add("embedMode=one");
        }
      }
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
    return "http://" + devToolsHost + ":" + devToolsPort + "/" + (page != null ? page : "") + "?"
           + StringUtil.join(params, "&");
  }

  public boolean maybeUpdateColor() {
    final String newColor = devToolsUtils.getColorHexCode();
    if (Objects.equals(colorHexCode, newColor)) {
      return false;
    }

    colorHexCode = newColor;
    isBright = devToolsUtils.getIsBackgroundBright();
    return true;
  }
}
