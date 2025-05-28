/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.gson.JsonObject;

import java.awt.geom.Rectangle2D;

public class TransformedRect {
  final JsonObject json;

  public TransformedRect(JsonObject json) {
    this.json = json;
  }

  public Rectangle2D getRectangle() {
    return new Rectangle2D.Double(
      json.getAsJsonPrimitive("left").getAsDouble(),
      json.getAsJsonPrimitive("top").getAsDouble(),
      json.getAsJsonPrimitive("width").getAsDouble(),
      json.getAsJsonPrimitive("height").getAsDouble()
    );
  }
}
