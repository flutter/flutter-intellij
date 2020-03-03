/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.assets;

import com.android.tools.idea.npw.assetstudio.TextRenderUtil;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An asset that represents a text value and related settings.
 */
@SuppressWarnings("UseJBColor")
public final class TextAsset extends BaseAsset {
  public static final String DEFAULT_TEXT = "Aa";
  private static final String PREFERRED_FONT = "Roboto";
  private static final int FONT_SIZE = 144;  // Large value for crisp icons.

  private final StringProperty myText = new StringValueProperty(DEFAULT_TEXT);
  private final StringProperty myFontFamily = new StringValueProperty();
  private final List<String> myAllFontFamilies;

  public TextAsset() {
    myAllFontFamilies = ImmutableList.copyOf(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
    selectFontFamily(PREFERRED_FONT);
  }

  private void selectFontFamily(@NotNull String fontFamily) {
    if (myAllFontFamilies.contains(fontFamily)) {
      myFontFamily.set(fontFamily);
    }
    else if (!myAllFontFamilies.isEmpty()) {
      myFontFamily.set(myAllFontFamilies.get(0));
    }
  }

  /**
   * Return all font families available for text rendering.
   */
  @NotNull
  public static List<String> getAllFontFamilies() {
    return ImmutableList.copyOf(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
  }

  /**
   * The text value that will be rendered into the final asset.
   */
  @NotNull
  public StringProperty text() {
    return myText;
  }

  /**
   * The font family associated with this text. Use {@link #getAllFontFamilies()} to get a list of
   * suitable values for this property.
   */
  @NotNull
  public StringProperty fontFamily() {
    return myFontFamily;
  }

  /**
   * Returns the default font family, or an empty string if no font families are available in the graphics environment.
   */
  @NotNull
  public String defaultFontFamily() {
    if (myAllFontFamilies.contains(PREFERRED_FONT)) {
      return PREFERRED_FONT;
    }
    return myAllFontFamilies.isEmpty() ? "" : myAllFontFamilies.get(0);
  }

  @Override
  @Nullable
  public ListenableFuture<BufferedImage> toImage() {
    TextRenderUtil.Options options = new TextRenderUtil.Options();
    options.font = Font.decode(myFontFamily + " " + FONT_SIZE);
    options.foregroundColor = color().getValueOr(Color.BLACK).getRGB();
    return Futures.immediateFuture(TextRenderUtil.renderTextImage(myText.get(), 1, options));
  }
}
