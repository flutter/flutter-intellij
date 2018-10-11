/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE path.
 */
package io.flutter.perf;

import com.google.common.base.Objects;

/**
 * Source location within a file with an id that is unique with the current
 * running Flutter application.
 */
public class Location {
  public Location(String path, int line, int column, int id) {
    this.path = path;
    this.line = line;
    this.column = column;
    this.id = id;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Location)) return false;
    final Location other = (Location) obj;
    return Objects.equal(line, other.line)
           && Objects.equal(column, other.column)
           && Objects.equal(path, other.path);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(line, column, path, id);
  }

  public final int line;
  public final int column;
  public final int id;
  public final String path;
}
