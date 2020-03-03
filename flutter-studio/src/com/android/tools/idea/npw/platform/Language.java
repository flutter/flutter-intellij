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
package com.android.tools.idea.npw.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representations of supported programming languages we can target when building an app.
 */
public enum Language {
  KOTLIN("Kotlin"), // Recommended at the top
  JAVA("Java");

  /**
   * Finds a language matching the requested name. Returns specified 'defaultValue' if not found.
   */
  public static Language fromName(@Nullable String name, @NotNull Language defaultValue) {
    if (name != null) {
      for (Language language : Language.values()) {
        if (language.getName().equals(name)) {
          return language;
        }
      }
    }

    return defaultValue;
  }

  private final String myName;

  Language(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return getName();
  }
}
