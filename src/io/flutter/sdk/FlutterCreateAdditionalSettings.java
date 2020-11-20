/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.util.text.StringUtil;
import io.flutter.module.FlutterProjectType;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

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
  @Nullable
  private Boolean platformAndroid;
  @Nullable
  private Boolean platformIos;
  @Nullable
  private Boolean platformWeb;
  @Nullable
  private Boolean platformLinux;
  @Nullable
  private Boolean platformMacos;
  @Nullable
  private Boolean platformWindows;

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
                                          @Nullable Boolean platformAndroid,
                                          @Nullable Boolean platformIos,
                                          @Nullable Boolean platformWeb,
                                          @Nullable Boolean platformLinux,
                                          @Nullable Boolean platformMacos,
                                          @Nullable Boolean platformWindows) {
    this.includeDriverTest = includeDriverTest;
    this.type = type;
    this.description = description;
    this.org = org;
    this.swift = swift;
    this.kotlin = kotlin;
    this.offlineMode = offlineMode;
    this.platformAndroid = platformAndroid;
    this.platformIos = platformIos;
    this.platformWeb = platformWeb;
    this.platformLinux = platformLinux;
    this.platformMacos = platformMacos;
    this.platformWindows = platformWindows;
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

    if (swift == null || Boolean.FALSE.equals(swift)) {
      args.add("--ios-language");
      args.add("objc");
    }

    if (kotlin == null || Boolean.FALSE.equals(kotlin)) {
      args.add("--android-language");
      args.add("java");
    }

    StringBuilder platforms = new StringBuilder();
    if (Boolean.TRUE.equals(platformAndroid)) {
      platforms.append("android,");
    }
    if (Boolean.TRUE.equals(platformIos)) {
      platforms.append("ios,");
    }
    if (Boolean.TRUE.equals(platformWeb)) {
      platforms.append("web,");
    }
    if (Boolean.TRUE.equals(platformLinux)) {
      platforms.append("linux,");
    }
    if (Boolean.TRUE.equals(platformMacos)) {
      platforms.append("macos,");
    }
    if (Boolean.TRUE.equals(platformWindows)) {
      platforms.append("windows,");
    }

    int lastComma = platforms.lastIndexOf(",");
    if (lastComma > 0) {
      platforms.deleteCharAt(lastComma);
      String platformsArg = platforms.toString();
      if (!platformsArg.equals("android,ios")) {
        args.add("--platforms");
        args.add(platformsArg);
      }
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

  @Nullable
  public Boolean getPlatformAndroid() {
    return platformAndroid;
  }

  @Nullable
  public Boolean getPlatformIos() {
    return platformIos;
  }

  @Nullable
  public Boolean getPlatformWeb() {
    return platformWeb;
  }

  @Nullable
  public Boolean getPlatformLinux() {
    return platformLinux;
  }

  @Nullable
  public Boolean getPlatformMacos() {
    return platformMacos;
  }

  @Nullable
  public Boolean getPlatformWindows() {
    return platformWindows;
  }

  @Nullable
  public FlutterProjectType getType() {
    return type;
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
    private Boolean platformAndroid;
    @Nullable
    private Boolean platformIos;
    @Nullable
    private Boolean platformWeb;
    @Nullable
    private Boolean platformLinux;
    @Nullable
    private Boolean platformMacos;
    @Nullable
    private Boolean platformWindows;

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

    public Builder setPlatformAndroid(@Nullable Boolean platformAndroid) {
      this.platformAndroid = platformAndroid;
      return this;
    }

    public Builder setPlatformIos(@Nullable Boolean platformIos) {
      this.platformIos = platformIos;
      return this;
    }

    public Builder setPlatformWeb(@Nullable Boolean platformWeb) {
      this.platformWeb = platformWeb;
      return this;
    }

    public Builder setPlatformLinux(@Nullable Boolean platformLinux) {
      this.platformLinux = platformLinux;
      return this;
    }

    public Builder setPlatformMacos(@Nullable Boolean platformMacos) {
      this.platformMacos = platformMacos;
      return this;
    }

    public Builder setPlatformWindows(@Nullable Boolean platformWindows) {
      this.platformWindows = platformWindows;
      return this;
    }

    public Builder setOffline(@Nullable Boolean offlineMode) {
      this.offlineMode = offlineMode;
      return this;
    }

    public FlutterCreateAdditionalSettings build() {
      return new FlutterCreateAdditionalSettings(
        includeDriverTest, type, description, org, swift, kotlin, offlineMode,
        platformAndroid, platformIos, platformWeb, platformLinux, platformMacos, platformWindows);
    }
  }
}
