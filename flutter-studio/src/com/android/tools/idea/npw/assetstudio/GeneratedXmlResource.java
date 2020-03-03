/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import com.android.ide.common.util.PathString;
import org.jetbrains.annotations.NotNull;

/** A {@link GeneratedIcon} that is defined by an XML document. */
public final class GeneratedXmlResource extends GeneratedIcon {
  @NotNull private final String xmlText;

  public GeneratedXmlResource(@NotNull String name, @NotNull PathString outputPath, @NotNull IconCategory category,
                              @NotNull String xmlText) {
    super(name, outputPath, category);
    this.xmlText = xmlText;
  }

  @NotNull
  public String getXmlText() {
    return xmlText;
  }
}
