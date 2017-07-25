/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.util.text.StringUtil;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class FlutterCreateAdditionalSettings {
  public static class Builder {
    @Nullable
    private Boolean includeDriverTest;
    @Nullable
    private Boolean generatePlugin;
    @Nullable
    private String description;
    @Nullable
    private String org;
    @Nullable
    private Boolean swift;
    @Nullable
    private Boolean kotlin;

    public Builder() {
    }

    public Builder setIncludeDriverTest(@Nullable Boolean includeDriverTest) {
      this.includeDriverTest = includeDriverTest;
      return this;
    }

    public Builder setGeneratePlugin(@Nullable Boolean generatePlugin) {
      this.generatePlugin = generatePlugin;
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

    public FlutterCreateAdditionalSettings build() {
      return new FlutterCreateAdditionalSettings(includeDriverTest, generatePlugin, description, org, swift, kotlin);
    }
  }

  @Nullable
  private Boolean includeDriverTest;
  @Nullable
  private Boolean generatePlugin;
  @Nullable
  private String description;
  @Nullable
  private String org;
  @Nullable
  private Boolean swift;
  @Nullable
  private Boolean kotlin;

  private FlutterCreateAdditionalSettings(Boolean includeDriverTest,
                                          Boolean generatePlugin,
                                          String description,
                                          String org,
                                          Boolean swift,
                                          Boolean kotlin) {
    this.includeDriverTest = includeDriverTest;
    this.generatePlugin = generatePlugin;
    this.description = description;
    this.org = org;
    this.swift = swift;
    this.kotlin = kotlin;
  }

  public List<String> getArgs() {
    final List<String> args = new ArrayList<>();

    if (BooleanUtils.isTrue(includeDriverTest)) {
      args.add("--with-driver-test");
    }

    if (BooleanUtils.isTrue(generatePlugin)) {
      args.add("--plugin");
    }

    if (!StringUtil.isEmptyOrSpaces(description) ) {
      args.add("--description");
      args.add(description);
    }

    if (!StringUtil.isEmptyOrSpaces(org)) {
      args.add("--org");
      args.add(org);
    }

    if (BooleanUtils.isTrue(swift)) {
      args.add("--ios-language");
      args.add("swift");
    }

    if (BooleanUtils.isTrue(kotlin)) {
      args.add("--android-language");
      args.add("kotlin");
    }

    return args;
  }
}
