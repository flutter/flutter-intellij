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
package com.android.tools.idea.npw.assetstudio.icon;

import static com.android.tools.idea.npw.assetstudio.IconGenerator.pathToDensity;

import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Class that wraps the complex, nested {@code Map<String, Map<String, BufferedImage>>} returned
 * by an {@link IconGenerator} and provides a more user-friendly API for interacting with it.
 *
 * The original map looks something like this:
 * <pre>
 *   category1
 *     path1: image
 *     path2: image
 *     path3: image
 *   cagegory2
 *     path1: image
 *     path2: image
 * </pre>
 *
 * although particular organization schemes and layouts differ slightly across icon types. In
 * addition to simplifying the underlying data structure, this class also attempts to make it
 * easier to interact with the different icon layouts.
 */
public final class CategoryIconMap {
  public static final Filter ACCEPT_ALL = category -> true;

  @NotNull private final Map<String, Map<String, BufferedImage>> myCategoryMap;

  public CategoryIconMap(@NotNull Map<String, Map<String, BufferedImage>> categoryMap) {
    myCategoryMap = categoryMap;
  }

  /**
   * Returns all icons as a single map of densities to images. Note that this may exclude images
   * that don't map neatly to any {@link Density}.
   */
  @NotNull
  public Map<Density, BufferedImage> toDensityMap() {
    return toDensityMap(ACCEPT_ALL);
  }

  /**
   * Like {@link #toDensityMap()} but with a filter for stripping out unwanted categories. This is
   * useful for icon sets organized by API.
   */
  @NotNull
  public Map<Density, BufferedImage> toDensityMap(@NotNull Filter filter) {
    Map<Density, BufferedImage> densityImageMap = new HashMap<>();
    for (String category : myCategoryMap.keySet()) {
      if (filter.accept(category)) {
        Map<String, BufferedImage> pathImageMap = myCategoryMap.get(category);
        for (String path : pathImageMap.keySet()) {
          Density density = pathToDensity(path);
          if (density != null) {
            BufferedImage image = pathImageMap.get(path);
            densityImageMap.put(density, image);
          }
        }
      }
    }

    return densityImageMap;
  }

  /**
   * Returns all icons as a single map of file paths to images. This is very useful when writing
   * icons to disk.
   */
  @NotNull
  public Map<File, BufferedImage> toFileMap(@NotNull File rootDir) {
    Map<File, BufferedImage> outputMap = new HashMap<>();
    for (Map<String, BufferedImage> pathImageMap : myCategoryMap.values()) {
      for (Map.Entry<String, BufferedImage> pathImageEntry : pathImageMap.entrySet()) {
        outputMap.put(new File(rootDir, pathImageEntry.getKey()), pathImageEntry.getValue());
      }
    }
    return outputMap;
  }

  /**
   * Category filter used when flattening our nested maps into a single-level map.
   */
  public interface Filter {
    boolean accept(@NotNull String category);
  }
}
