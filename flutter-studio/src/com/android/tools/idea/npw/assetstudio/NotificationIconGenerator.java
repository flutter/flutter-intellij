/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.intellij.openapi.project.Project;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generates icons for the notifications bar.
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here.
public class NotificationIconGenerator extends IconGenerator {
  private static final Dimension ICON_SIZE = new Dimension(24, 24);

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public NotificationIconGenerator(@NotNull Project project, int minSdkVersion, @Nullable DrawableRenderer renderer) {
    super(project, minSdkVersion, new GraphicGeneratorContext(40, renderer));
  }

  @Override
  @NotNull
  public Options createOptions(boolean forPreview) {
    Options options = new Options(forPreview);
    BaseAsset asset = sourceAsset().getValueOrNull();
    if (asset != null) {
      double paddingFactor = asset.paddingPercent().get() / 100. + 1. / (ICON_SIZE.width - 1); // Add extra 1dp padding
      double scaleFactor = 1. / (1 + paddingFactor * 2);
      options.image = new TransformedImageAsset(asset, ICON_SIZE, scaleFactor, Color.WHITE, getGraphicGeneratorContext());
    }

    return options;
  }

  @Override
  @NotNull
  public BufferedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull Options options) {
    return generateRasterImage(ICON_SIZE, options);
  }

  @Override
  protected int calculateMinRequiredApiLevel(@NotNull String xmlDrawableText, int minSdk) {
    if (minSdk < 24) {
      return 24;
    }
    return 0;
  }
}
