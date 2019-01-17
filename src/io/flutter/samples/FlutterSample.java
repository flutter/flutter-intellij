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
  private final String sourcePath;
  @NotNull
  private final String description;

  FlutterSample(@NotNull String element, @NotNull String library, @NotNull String id, @NotNull String file,
                @NotNull String sourcePath, @NotNull String description) {
    this.element = element;
    this.library = library;
    this.id = id;
    this.file = file;
    this.sourcePath = sourcePath;
    this.description = description;
  }

  @Override
  public String toString() {
    return getDisplayLabel();
  }

  /**
   * Get a label suitable for display in a chooser (e.g., combobox).
   */
  public String getDisplayLabel() {
    // TODO(pq): add disambiguation once it's needed.
    return getElement();
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

  @NotNull
  public String getSourcePath() {
    return sourcePath;
  }
}
