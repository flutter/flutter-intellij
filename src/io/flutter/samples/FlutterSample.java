/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import org.jetbrains.annotations.NotNull;

public class FlutterSample {
  @NotNull
  private final String libraryName;
  @NotNull
  private final String className;

  FlutterSample(@NotNull String libraryName, @NotNull String className) {
    this.libraryName = libraryName;
    this.className = className;
  }

  @NotNull
  public String getLibraryName() {
    return libraryName;
  }

  @NotNull
  public String getClassName() {
    return className;
  }

  @NotNull
  public String getDisplayName() {
    return libraryName + "." + className;
  }

  @NotNull
  public String getHostedDocsUrl() {
    // https://api.flutter.dev/flutter/material/AppBar-class.html

    return "https://api.flutter.dev/flutter/" + libraryName + "/" + className + "-class.html";
  }

  @Override
  public String toString() {
    return getLibraryName() + "." + getClassName();
  }
}
