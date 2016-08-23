/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;


import com.intellij.openapi.projectRoots.*;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterSdkType extends SdkType {

  public static final String FLUTTER_SDK_TYPE_ID = "Flutter SDK";
  private static FlutterSdkType INSTANCE;

  private FlutterSdkType() {
    super(FLUTTER_SDK_TYPE_ID);
  }

  @NotNull
  public static FlutterSdkType getInstance() {
    if (INSTANCE == null) {
      INSTANCE = SdkType.findInstance(FlutterSdkType.class);
    }
    return INSTANCE;
  }

  @NotNull
  private static String getDefaultSdkName(@NotNull String sdkHome, @Nullable FlutterSdkVersion version) {
    return version != null ? FlutterBundle.message("flutter.sdk.label.0", version.getPresentableName())
                           : FlutterBundle.message("flutter.sdk.label.unknown.0", sdkHome);
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {

  }

  @Override
  public Icon getIcon() {
    return FlutterIcons.Flutter_16;
  }

  @NotNull
  @Override
  public Icon getIconForAddAction() {
    return getIcon();
  }

  @Nullable
  @Override
  public String suggestHomePath() {
    return null;
  }

  @Nullable
  @Override
  public String getVersionString(String sdkHome) {
    return FlutterSdkUtil.getSdkVersion(sdkHome);
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return FlutterSdkUtil.isFlutterSdkHome(path);
  }

  @NotNull
  @Override
  public String suggestSdkName(@Nullable String currentSdkName, @NotNull String sdkHome) {
    return getDefaultSdkName(sdkHome, detectSdkVersion(sdkHome));
  }

  private FlutterSdkVersion detectSdkVersion(@NotNull String sdkHome) {
    return FlutterSdkVersion.forVersionString(getVersionString(sdkHome));
  }

  @Nullable
  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return FlutterBundle.message("flutter.sdk.name");
  }
}
