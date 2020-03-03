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

import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.intellij.openapi.project.Project;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generates icons for the vector drawable.
 */
public class VectorIconGenerator extends IconGenerator {
  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public VectorIconGenerator(@NotNull Project project, int minSdkVersion) {
    super(project, minSdkVersion, new GraphicGeneratorContext(40, null));
  }

  @Override
  @NotNull
  public VectorIconOptions createOptions(boolean forPreview) {
    VectorIconOptions options = new VectorIconOptions(forPreview);
    BaseAsset asset = sourceAsset().getValueOrNull();
    if (asset != null) {
      options.sourceImageFuture = asset.toImage();
      options.isTrimmed = asset.trimmed().get();
      options.paddingPercent = asset.paddingPercent().get();
    }

    options.density = Density.ANYDPI;
    return options;
  }

  @Override
  @NotNull
  public BufferedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull Options options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    BufferedImage image = getTrimmedAndPaddedImage(options);
    if (image == null) {
      image = PLACEHOLDER_IMAGE;
    }
    return image;
  }


  @Nullable
  private static BufferedImage getTrimmedAndPaddedImage(@NotNull Options options) {
    if (options.sourceImageFuture == null) {
      return null;
    }
    try {
      BufferedImage image = options.sourceImageFuture.get();
      if (image != null) {
        if (options.isTrimmed) {
          image = AssetStudioUtils.trim(image);
        }
        if (options.paddingPercent != 0) {
          image = AssetStudioUtils.pad(image, options.paddingPercent);
        }
      }
      return image;
    }
    catch (InterruptedException | ExecutionException e) {
      return null;
    }
  }

  @Override
  @NotNull
  public Collection<GeneratedIcon> generateIcons(@NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    VectorAsset vectorAsset = (VectorAsset)sourceAsset().getValue();
    VectorAsset.ParseResult result = vectorAsset.parse();
    if (!result.isValid()) {
      return Collections.emptySet();
    }
    String xmlContent = result.getXmlContent();
    GeneratedIcon icon = new GeneratedXmlResource(name, new PathString(getIconPath(options, name)), IconCategory.XML_RESOURCE, xmlContent);
    return Collections.singleton(icon);
  }

  public static class VectorIconOptions extends Options {
    public VectorIconOptions(boolean forPreview) {
      super(forPreview);
      iconFolderKind = IconFolderKind.DRAWABLE_NO_DPI;
    }
  }
}
