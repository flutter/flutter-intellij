/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.flutter.utils.math.Matrix4;

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

  public Matrix4 getTransform() {
    final JsonArray data = json.getAsJsonArray("transform");
    final double[] storage = new double[16];
    for (int i =0 ; i < 16; i++) {
      storage[i] = data.get(i).getAsDouble();
    }
    return new Matrix4(storage);
  }
}
