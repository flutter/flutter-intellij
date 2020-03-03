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
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A {@link GeneratedIcon} that is defined by a {@link BufferedImage} at a given density. */
public class GeneratedImageIcon extends GeneratedIcon {
  @NotNull private final Density density;
  @NotNull private final BufferedImage image;

  public GeneratedImageIcon(@NotNull String name, @Nullable PathString outputPath, @NotNull IconCategory category, @NotNull Density density,
                            @NotNull BufferedImage image) {
    super(name, outputPath, category);
    this.density = density;
    this.image = image;
  }

  @NotNull
  public Density getDensity() {
    return density;
  }

  @NotNull
  public BufferedImage getImage() {
    return image;
  }
}
