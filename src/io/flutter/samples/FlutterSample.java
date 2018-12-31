/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import org.jetbrains.annotations.NotNull;

public class FlutterSample {
  @NotNull
  private final String element;
  @NotNull
  private final String library;
  @NotNull
  private final String id;
  @NotNull
  private final String file;
  @NotNull
  private final String description;

  private String relativePath;

  FlutterSample(@NotNull String element, @NotNull String library, @NotNull String id, @NotNull String file,
                @NotNull String description) {
    this.element = element;
    this.library = library;
    this.id = id;
    this.file = file;
    this.description = description;
  }

  @Override
  public String toString() {
    return getElement() + " (" + getLibrary() + ")";
  }

  @NotNull
  public String getLibrary() {
    return library;
  }

  @NotNull
  public String getId() {
    return id;
  }

  @NotNull
  public String getElement() {
    return element;
  }

  @NotNull
  public String getDescription() {
    return description;
  }

  @NotNull
  public String getFile() {
    return file;
  }

  public String getRelativePath() {
    if (relativePath == null) {
      // Chomp .dart suffix
      relativePath = file.substring(file.length() - 5);
      // material.sample => material/sample
      relativePath = relativePath.replaceAll("\\.", "/");
      // Re-add suffix.
      relativePath += ".dart";
    }
    return relativePath;
  }
}
