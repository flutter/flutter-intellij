/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidModuleLibraryProperties extends LibraryProperties<AndroidModuleLibraryProperties> {

  @Override
  public boolean equals(Object obj) {
    return false;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Nullable
  @Override
  public AndroidModuleLibraryProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AndroidModuleLibraryProperties state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
