/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.gson.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A representation of a Flutter widget.
 */
public class FlutterWidget {

  private static final Catalog catalog = new Catalog();
  private final JsonObject json;

  private FlutterWidget(@NotNull JsonObject json) {
    this.json = json;
  }

  @Contract(pure = true)
  @NotNull
  public static Catalog getCatalog() {
    return catalog;
  }

  @Nullable
  public String getName() {
    return getStringMember("name");
  }

  @Nullable
  public List<String> getCategories() {
    return getValues("categories");
  }

  @Nullable
  public List<String> getSubCategories() {
    return getValues("subcategories");
  }

  @NotNull
  private List<String> getValues(@NotNull String member) {
    if (!json.has(member)) {
      return Collections.emptyList();
    }

    final JsonArray rawValues = json.getAsJsonArray(member);
    final ArrayList<String> values = new ArrayList<>(rawValues.size());
    rawValues.forEach(element -> values.add(element.getAsString()));

    return values;
  }

  @Nullable
  private String getStringMember(String memberName) {
    if (!json.has(memberName)) return null;

    final JsonElement value = json.get(memberName);
    return value instanceof JsonNull ? null : value.getAsString();
  }

  /**
   * Catalog of widgets derived from widgets.json.
   */
  public static final class Catalog {
    @NotNull
    private final List<FlutterWidget> widgets = new ArrayList<>();
    @Nullable
    private JsonElement json;

    private Catalog() {
      init();
    }

    private void init() {
      try {
        // Local copy of: https://github.com/flutter/website/tree/master/_data/catalog/widget.json
        final URL resource = getClass().getResource("widgets.json");
        final String content = new String(Files.readAllBytes(Paths.get(resource.toURI())));
        final JsonParser parser = new JsonParser();
        json = parser.parse(content);
        if (json instanceof JsonArray) {
          ((JsonArray)json).forEach(element -> {
            if (element instanceof JsonObject) {
              widgets.add(new FlutterWidget((JsonObject)element));
            }
          });
        }
      }
      catch (IOException | URISyntaxException e) {
        // Ignored -- json will be null.
      }
    }

    @Contract(pure = true)
    @NotNull
    public List<FlutterWidget> getWidgets() {
      return widgets;
    }

    @Nullable
    public FlutterWidget getWidget(@NotNull String name) {
      return getWidgets().stream().filter(w -> Objects.equals(w.getName(), name))
        .findFirst().orElse(null);
    }

    @Contract(pure = true)
    @NotNull
    public String dumpJson() {
      return Objects.toString(json);
    }
  }
}
