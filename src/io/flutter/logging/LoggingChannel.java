/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.gson.JsonObject;

public class LoggingChannel {
  final String name;
  final String description;
  final boolean enabled;

  public LoggingChannel(String name, String description, boolean enabled) {
    this.name = name;
    this.description = description;
    this.enabled = enabled;
  }

  public static LoggingChannel fromJson(String name, JsonObject properties) {
    final String description = properties.getAsJsonPrimitive("description").getAsString();
    final boolean enabled = properties.getAsJsonPrimitive("enabled").getAsBoolean();
    return new LoggingChannel(name, description, enabled);
  }
}
