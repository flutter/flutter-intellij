/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JsonUtils {

  private JsonUtils() {
  }

  @Nullable
  public static String getStringMember(@NotNull JsonObject json, @NotNull String memberName) {
    if (!json.has(memberName)) return null;

    final JsonElement value = json.get(memberName);
    return value instanceof JsonNull ? null : value.getAsString();
  }

  public static int getIntMember(@NotNull JsonObject json, @NotNull String memberName) {
    if (!json.has(memberName)) return -1;

    final JsonElement value = json.get(memberName);
    return value instanceof JsonNull ? -1: value.getAsInt();
  }

  @NotNull
  public static List<String> getValues(@NotNull JsonObject json, @NotNull String member) {
    if (!json.has(member)) {
      return Collections.emptyList();
    }

    final JsonArray rawValues = json.getAsJsonArray(member);
    final ArrayList<String> values = new ArrayList<>(rawValues.size());
    rawValues.forEach(element -> values.add(element.getAsString()));

    return values;
  }
}
