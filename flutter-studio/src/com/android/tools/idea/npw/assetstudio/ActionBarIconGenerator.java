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
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.intellij.openapi.project.Project;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generates icons for the action bar.
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here.
public class ActionBarIconGenerator extends IconGenerator {
  public static final Theme DEFAULT_THEME = Theme.HOLO_LIGHT;
  public static final Color DEFAULT_CUSTOM_COLOR = new Color(0x33B5E5);
  private static final Color HOLO_LIGHT_COLOR = new Color(0x333333);
  private static final Color HOLO_DARK_COLOR = new Color(0xFFFFFF);
  private static final Dimension ICON_SIZE = new Dimension(24, 24);

  private final ObjectProperty<Theme> myTheme = new ObjectValueProperty<>(DEFAULT_THEME);
  private final ObjectProperty<Color> myCustomColor = new ObjectValueProperty<>(DEFAULT_CUSTOM_COLOR);

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public ActionBarIconGenerator(@NotNull Project project, int minSdkVersion, @Nullable DrawableRenderer renderer) {
    super(project, minSdkVersion, new GraphicGeneratorContext(40, renderer));
  }

  /**
   * The theme for this icon, which influences its foreground/background colors.
   */
  @NotNull
  public ObjectProperty<Theme> theme() {
    return myTheme;
  }

  /**
   * A custom color which will be used if {@link #theme()} is set to {@link Theme#CUSTOM}.
   */
  @NotNull
  public ObjectProperty<Color> customColor() {
    return myCustomColor;
  }

  @Override
  @NotNull
  public Options createOptions(boolean forPreview) {
    Options options = new Options(forPreview);
    BaseAsset asset = sourceAsset().getValueOrNull();
    if (asset != null) {
      double paddingFactor = asset.paddingPercent().get() / 100.;
      double scaleFactor = 1. / (1 + paddingFactor * 2);

      Color color;
      int opacityPercent;
      switch (myTheme.get()) {
        case HOLO_DARK:
          color = HOLO_DARK_COLOR;
          opacityPercent = 80;
          break;
        case HOLO_LIGHT:
          color = HOLO_LIGHT_COLOR;
          opacityPercent = 60;
          break;
        case CUSTOM:
          color = myCustomColor.get();
          opacityPercent = 80;
          break;
        default:
          color = null;
          opacityPercent = 100;
          break;
      }
      asset.opacityPercent().set(opacityPercent);
      options.image = new TransformedImageAsset(asset, ICON_SIZE, scaleFactor, color, getGraphicGeneratorContext());
    }

    return options;
  }

  @Override
  @NotNull
  public BufferedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull Options options) {
    return generateRasterImage(ICON_SIZE, options);
  }

  /** The themes to generate action bar icons for. */
  public enum Theme {
    /** Theme.Holo - a dark (and default) version of the Honeycomb theme. */
    HOLO_DARK,

    /** Theme.HoloLight - a light version of the Honeycomb theme. */
    HOLO_LIGHT,

    /** Theme.Custom - custom colors. */
    CUSTOM
  }
}
