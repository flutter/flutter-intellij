/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import icons.FlutterIcons;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Categorization of a Flutter widget.
 */
public class FlutterWidget {
  private static final Logger LOG = Logger.getInstance(FlutterWidget.class);

  enum Category {
    ACCESSIBILITY("Accessibility", FlutterIcons.Accessibility),
    ANIMATION_AND_MOTION("Animation and Motion", FlutterIcons.Animation),
    ASSETS_IMAGES_AND_ICONS("Assets, Images, and Icons", FlutterIcons.Assets),
    ASYNC("Async", FlutterIcons.Async),
    //BASICS("Basics", FlutterIcons.Basics),
    //CUPERTINO("Cupertino (iOS-style widgets)", FlutterIcons.Cupertino),
    INPUT("Input", FlutterIcons.Input),
    PAINTING_AND_EFFECTS("Painting and effects", FlutterIcons.Painting),
    SCROLLING("Scrolling", FlutterIcons.Scrollbar),
    STACK("Stack", FlutterIcons.Stack),
    STYLING("Styling", FlutterIcons.Styling),
    TEXT("Text", FlutterIcons.Text);

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

  static abstract class Filter {

    public static final Predicate<DiagnosticsNode> PRIVATE_CLASS = forPattern("_.*");

    public static Predicate<DiagnosticsNode> forPatterns(@NotNull String... regexps) {
      return Arrays.stream(regexps).map(Filter::forPattern).reduce(node -> false,
                                                                   Predicate::or);
    }

    public static Predicate<DiagnosticsNode> forPattern(@NotNull String regexp) {
      final Pattern pattern = Pattern.compile(regexp);
      return node -> {
        final String description = node.getDescription();
        return description != null && pattern.matcher(description).matches();
      };
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
        final byte[] contentBytes = ByteStreams.toByteArray((InputStream)resource.getContent());
        final String content = new String(contentBytes, Charsets.UTF_8);
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
      catch (IOException e) {
        LOG.error(e);
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
