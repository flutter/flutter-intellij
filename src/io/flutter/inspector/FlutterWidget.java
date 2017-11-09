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
import icons.FlutterIcons;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

  enum Category {
    // TODO(pq): fill in undefined widgets.
    ACCESSIBILITY("Accessibility", null), ANIMATION_AND_MOTION("Animation and Motion", null),
    ASSETS_IMAGES_AND_ICONS("Assets, Images, and Icons", FlutterIcons.AnyType),
    ASYNC("Async", null), BASICS("Basics", null), CUPERTINO("Cupertino (iOS-style widgets)", null),
    INPUT("Input", null), PAINTING_AND_EFFECTS("Painting and effects", FlutterIcons.Colors),
    SCROLLING("Scrolling", FlutterIcons.Scrollbar), STACK("Stack", FlutterIcons.Value),
    STYLING("Styling", FlutterIcons.Annotation), TEXT("Text", FlutterIcons.TextArea);

    @NotNull
    private final String label;
    private final Icon icon;

    Category(@NotNull String label, Icon icon) {
      this.label = label;
      this.icon = icon;
    }

    @NotNull
    public String getLabel() {
      return label;
    }

    @Nullable
    public static Category forLabel(@NotNull String label) {
      return Arrays.stream(values()).filter(c -> c.getLabel().equals(label)).findFirst().orElse(null);
    }

    @Contract(pure = true)
    @Nullable
    public Icon getIcon() {
      return icon;
    }
  }

  private static final Catalog catalog = new Catalog();
  private final JsonObject json;
  private final Icon icon;

  private FlutterWidget(@NotNull JsonObject json) {
    this.json = json;
    this.icon = initIcon();
  }

  @Nullable
  private Icon initIcon() {
    final List<String> categories = getCategories();
    if (categories != null) {
      // TODO(pq): consider priority over first match.
      for (String label : categories) {
        final Category category = Category.forLabel(label);
        if (category != null) {
          final Icon icon = category.getIcon();
          if (icon != null) return icon;
        }
      }
    }
    return null;
  }

  @Contract(pure = true)
  @NotNull
  public static Catalog getCatalog() {
    return catalog;
  }

  @Contract(pure = true)
  @Nullable
  public Icon getIcon() {
    return icon;
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

    @Contract("null -> null")
    @Nullable
    public FlutterWidget getWidget(@Nullable String name) {
      return name != null ? widgets.get(name) : null;
    }

    @Contract(pure = true)
    @NotNull
    public String dumpJson() {
      return Objects.toString(json);
    }
  }
}
