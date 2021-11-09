/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.gson.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    return value instanceof JsonNull ? -1 : value.getAsInt();
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

  // JsonObject.keySet() is defined in 2.8.6 but not 2.7.
  // The 2020.3 version of Android Studio includes both, and 2.7 is first on the class path.
  public static Set<String> getKeySet(JsonObject obj) {
    final Set<Map.Entry<String, JsonElement>> entries = obj.entrySet();
    final HashSet<String> strings = new HashSet<>(entries.size());
    for (Map.Entry<String, JsonElement> entry : entries) {
      strings.add(entry.getKey());
    }
    return strings;
  }

  public static boolean hasJsonData(@Nullable String data) {
    return StringUtils.isNotEmpty(data) && !Objects.equals(data, "null");
  }

  /**
   * Parses the specified JSON string into a JsonElement.
   */
  public static JsonElement parseString(String json) throws JsonSyntaxException {
    return new JsonParser().parse(json);
  }

  /**
   * Parses the specified JSON string into a JsonElement.
   */
  public static JsonElement parseReader(Reader reader) throws JsonIOException, JsonSyntaxException {
    return new JsonParser().parse(reader);
  }
}
