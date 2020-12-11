/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.util.text.StringUtil;
import io.flutter.module.FlutterProjectType;
import io.flutter.module.settings.InitializeOnceBoolValueProperty;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

  // These objects get re-used each time the UI is rebuilt, including after the Finish button is clicked.
  @NotNull final private InitializeOnceBoolValueProperty platformAndroid = new InitializeOnceBoolValueProperty();
  @NotNull final private InitializeOnceBoolValueProperty platformIos = new InitializeOnceBoolValueProperty();
  @NotNull final private InitializeOnceBoolValueProperty platformWeb = new InitializeOnceBoolValueProperty();
  @NotNull final private InitializeOnceBoolValueProperty platformLinux = new InitializeOnceBoolValueProperty();
  @NotNull final private InitializeOnceBoolValueProperty platformMacos = new InitializeOnceBoolValueProperty();
  @NotNull final private InitializeOnceBoolValueProperty platformWindows = new InitializeOnceBoolValueProperty();

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
    this.platformAndroid.set(Boolean.TRUE.equals(platformAndroid));
    this.platformIos.set(Boolean.TRUE.equals(platformIos));
    this.platformWeb.set(Boolean.TRUE.equals(platformWeb));
    this.platformLinux.set(Boolean.TRUE.equals(platformLinux));
    this.platformMacos.set(Boolean.TRUE.equals(platformMacos));
    this.platformWindows.set(Boolean.TRUE.equals(platformWindows));
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

  @NonNls
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
    if (platformAndroid.get()) {
      platforms.append("android,");
    }
    if (platformIos.get()) {
      platforms.append("ios,");
    }
    if (platformWeb.get()) {
      platforms.append("web,");
    }
    if (platformLinux.get()) {
      platforms.append("linux,");
    }
    if (platformMacos.get()) {
      platforms.append("macos,");
    }
    if (platformWindows.get()) {
      platforms.append("windows,");
    }

    int lastComma = platforms.lastIndexOf(",");
    if (lastComma > 0) {
      platforms.deleteCharAt(lastComma);
      String platformsArg = platforms.toString();
      args.add("--platforms");
      args.add(platformsArg);
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
    return platformAndroid.get();
  }

  @Nullable
  public Boolean getPlatformIos() {
    return platformIos.get();
  }

  @Nullable
  public Boolean getPlatformWeb() {
    return platformWeb.get();
  }

  @Nullable
  public Boolean getPlatformLinux() {
    return platformLinux.get();
  }

  @Nullable
  public Boolean getPlatformMacos() {
    return platformMacos.get();
  }

  @Nullable
  public Boolean getPlatformWindows() {
    return platformWindows.get();
  }

  @Nullable
  public FlutterProjectType getType() {
    return type;
  }

  public boolean isSomePlatformSelected() {
    return platformAndroid.get() ||
           platformIos.get() ||
           platformLinux.get() ||
           platformMacos.get() ||
           platformWeb.get() ||
           platformWindows.get();
  }

  public InitializeOnceBoolValueProperty getPlatformAndroidProperty() {
    return platformAndroid;
  }

  public InitializeOnceBoolValueProperty getPlatformIosProperty() {
    return platformIos;
  }

  public InitializeOnceBoolValueProperty getPlatformWebProperty() {
    return platformWeb;
  }

  public InitializeOnceBoolValueProperty getPlatformLinuxProperty() {
    return platformLinux;
  }

  public InitializeOnceBoolValueProperty getPlatformMacosProperty() {
    return platformMacos;
  }

  public InitializeOnceBoolValueProperty getPlatformWindowsProperty() {
    return platformWindows;
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
