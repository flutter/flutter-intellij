/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.dynamicapp;

import org.jetbrains.annotations.NotNull;

/**
 * Enum defining the various distribution options for a device feature of a dynamic feature module.
 */
public enum DeviceFeatureKind {
  GL_ES_VERSION("OpenGL ES Version"),

  NAME("Name");

  @NotNull
  private String myDisplayName;

  DeviceFeatureKind(@NotNull String displayName) {
    this.myDisplayName = displayName;
  }

  @NotNull
  public String getDisplayName() {
    return this.myDisplayName;
  }

  @Override
  @NotNull
  public String toString() {
    return this.myDisplayName;
  }
}