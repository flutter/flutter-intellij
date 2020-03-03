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
package com.android.tools.idea.npw.assetstudio.icon;

import com.android.tools.idea.npw.assetstudio.GeneratedIcon;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.google.common.base.Predicates;
import java.util.Collection;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public final class IconGeneratorResult {
  @NotNull private final Collection<GeneratedIcon> icons;
  @NotNull private final IconGenerator.Options options;

  public IconGeneratorResult(@NotNull Collection<GeneratedIcon> icons, @NotNull IconGenerator.Options options) {
    this.icons = icons;
    this.options = options;
  }

  @NotNull
  public Collection<GeneratedIcon> getIcons() {
    return icons;
  }

  @NotNull
  public IconGenerator.Options getOptions() {
    return options;
  }

  @NotNull
  public Collection<String> getErrors() {
    return icons.stream()
        .map(GeneratedIcon::getErrorMessage)
        .filter(Predicates.notNull())
        .distinct()
        .collect(Collectors.toList());
  }
}
