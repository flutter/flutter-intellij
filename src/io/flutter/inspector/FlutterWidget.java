/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Categorization of a Flutter widget.
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
    return JsonUtils.getStringMember(json, "name");
  }

  @Nullable
  public List<String> getCategories() {
    return JsonUtils.getValues(json, "categories");
  }

  @Nullable
  public List<String> getSubCategories() {
    return JsonUtils.getValues(json, "subcategories");
  }

  /**
   * Catalog of widgets derived from widgets.json.
   */
  public static final class Catalog {
    @NotNull
    private final Map<String, FlutterWidget> widgets = new HashMap<>();

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
        if (!(json instanceof JsonArray)) throw new IllegalStateException("Unexpected Json format: expected array");
        ((JsonArray)json).forEach(element -> {
          if (!(element instanceof JsonObject)) throw new IllegalStateException("Unexpected Json format: expected object");
          final FlutterWidget widget = new FlutterWidget((JsonObject)element);
          final String name = widget.getName();
          // TODO(pq): add validation once json is repaired (https://github.com/flutter/flutter/issues/12930).
          //if (widgets.containsKey(name)) throw new IllegalStateException("Unexpected contents: widget `" + name + "` is duplicated");
          widgets.put(name, widget);
        });
      }
      catch (IOException | URISyntaxException e) {
        // Ignored -- json will be null.
      }
    }

    @Contract(pure = true)
    @NotNull
    public Collection<FlutterWidget> getWidgets() {
      return widgets.values();
    }

    @Nullable
    public FlutterWidget getWidget(@NotNull String name) {
      return widgets.get(name);
    }

    @Contract(pure = true)
    @NotNull
    public String dumpJson() {
      return Objects.toString(json);
    }
  }
}
