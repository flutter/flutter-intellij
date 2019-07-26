/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.util.text.StringUtil;
import io.flutter.module.FlutterProjectType;
import io.flutter.samples.FlutterSample;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FlutterCreateAdditionalSettings {
  @Nullable
  private Boolean includeDriverTest;
  @Nullable
  private FlutterProjectType type;
  @Nullable
  private String description;
  @Nullable
  private String org;
  @Nullable
  private Boolean swift;
  @Nullable
  private Boolean kotlin;
  @Nullable
  private Boolean offlineMode;
  private boolean isAndroidX;

  public FlutterCreateAdditionalSettings() {
    type = FlutterProjectType.APP;
    description = "";
    org = "";
  }

  private FlutterCreateAdditionalSettings(@Nullable Boolean includeDriverTest,
                                          @Nullable FlutterProjectType type,
                                          @Nullable String description,
                                          @Nullable String org,
                                          @Nullable Boolean swift,
                                          @Nullable Boolean kotlin,
                                          @Nullable Boolean offlineMode,
                                          boolean isAndroidX) {
    this.includeDriverTest = includeDriverTest;
    this.type = type;
    this.description = description;
    this.org = org;
    this.swift = swift;
    this.kotlin = kotlin;
    this.offlineMode = offlineMode;
    this.isAndroidX = isAndroidX;
  }

  public void setType(@Nullable FlutterProjectType value) {
    type = value;
  }

  @Nullable
  public String getOrg() {
    return org;
  }

  public void setOrg(@Nullable String value) {
    org = value;
  }

  public void setSwift(boolean value) {
    swift = value;
  }

  public void setKotlin(boolean value) {
    kotlin = value;
  }

  public List<String> getArgs() {
    final List<String> args = new ArrayList<>();

    if (Boolean.TRUE.equals(offlineMode)) {
      args.add("--offline");
    }

    if (Boolean.TRUE.equals(includeDriverTest)) {
      args.add("--with-driver-test");
    }

    if (type != null) {
      args.add("--template");
      args.add(type.arg);
    }

    if (!StringUtil.isEmptyOrSpaces(description)) {
      args.add("--description");
      args.add(description);
    }

    if (!StringUtil.isEmptyOrSpaces(org)) {
      args.add("--org");
      args.add(org);
    }

    if (Boolean.TRUE.equals(swift)) {
      args.add("--ios-language");
      args.add("swift");
    }

    if (Boolean.TRUE.equals(kotlin)) {
      args.add("--android-language");
      args.add("kotlin");
    }

    if (isAndroidX) {
      // TODO(messick): Remove the AndroidX UI components when AS 3.6 becomes the stable version. By then AndroidX should always be used.
      args.add("--androidx");
    }

    return args;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String value) {
    description = value;
  }

  @Nullable
  public Boolean getKotlin() {
    return kotlin;
  }

  @Nullable
  public Boolean getSwift() {
    return swift;
  }

  public static class Builder {
    @Nullable
    private Boolean includeDriverTest;
    @Nullable
    private FlutterProjectType type;
    @Nullable
    private String description;
    @Nullable
    private String org;
    @Nullable
    private Boolean swift;
    @Nullable
    private Boolean kotlin;
    @Nullable
    private Boolean offlineMode;
    @Nullable
    private FlutterSample sampleContent;
    private boolean isAndroidX;

    public Builder() {
    }

    public Builder setIncludeDriverTest(@Nullable Boolean includeDriverTest) {
      this.includeDriverTest = includeDriverTest;
      return this;
    }

    public Builder setType(@Nullable FlutterProjectType type) {
      this.type = type;
      return this;
    }

    public Builder setDescription(@Nullable String description) {
      this.description = description;
      return this;
    }

    public Builder setOrg(@Nullable String org) {
      this.org = org;
      return this;
    }

    public Builder setSwift(@Nullable Boolean swift) {
      this.swift = swift;
      return this;
    }

    public Builder setKotlin(@Nullable Boolean kotlin) {
      this.kotlin = kotlin;
      return this;
    }

    public Builder setOffline(@Nullable Boolean offlineMode) {
      this.offlineMode = offlineMode;
      return this;
    }

    public Builder setSampleContent(@Nullable FlutterSample sampleContent) {
      this.sampleContent = sampleContent;
      return this;
    }

    public Builder setAndroidX(boolean selected) {
      this.isAndroidX = selected;
      return this;
    }

    public FlutterCreateAdditionalSettings build() {
      return new FlutterCreateAdditionalSettings(includeDriverTest, type, description, org, swift, kotlin, offlineMode, isAndroidX);
    }
  }
}
