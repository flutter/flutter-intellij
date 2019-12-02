/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.gson.JsonObject;

import java.awt.image.BufferedImage;

public class Screenshot {
  public final BufferedImage image;
  public final TransformedRect transformedRect;

  Screenshot(BufferedImage image, TransformedRect transformedRect) {
    this.image = image;
    this.transformedRect = transformedRect;
  }
}
