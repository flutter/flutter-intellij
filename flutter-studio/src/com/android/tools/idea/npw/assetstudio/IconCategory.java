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

/**
 * Icon category when rendering multiple icon images. Given the compatibility requirements between
 * the old style api and the new style API, this enumeration contains a superset of everything
 * needed to sort things out in the upper layers.
 */
public enum IconCategory {
  REGULAR,

  // Various image.
  LEGACY,
  ROUND_API_25,
  WEB,

  // Adaptive icons previews.
  ADAPTIVE_FULL_BLEED,
  ADAPTIVE_CIRCLE,
  ADAPTIVE_SQUIRCLE,
  ADAPTIVE_ROUNDED_SQUARE,
  ADAPTIVE_SQUARE,

  // Adaptive icons layers.
  ADAPTIVE_FOREGROUND_LAYER,
  ADAPTIVE_BACKGROUND_LAYER,

  XML_RESOURCE,

  PREVIEW;

  public String getDisplayName() {
      return toString();
  }
}
