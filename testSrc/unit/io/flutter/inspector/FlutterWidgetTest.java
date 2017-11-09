/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class FlutterWidgetTest {

  @Contract(pure = true)
  @NotNull
  private static Matcher<FlutterWidget> hasCategories(@Nullable String... category) {
    final List<String> expected = category != null ? Arrays.asList(category) : Collections.emptyList();
    return new BaseMatcher<FlutterWidget>() {
      @Override
      public boolean matches(final Object item) {
        final List<String> categories = ((FlutterWidget)item).getCategories();
        return categories != null && categories.containsAll(expected);
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("getCategories should return ").appendValue(category);
      }
    };
  }

  @Contract(pure = true)
  @NotNull
  private static Matcher<FlutterWidget> hasSubCategories(@Nullable String... subcategory) {
    final List<String> expected = subcategory != null ? Arrays.asList(subcategory) : Collections.emptyList();
    return new BaseMatcher<FlutterWidget>() {
      @Override
      public boolean matches(final Object item) {
        final List<String> subcategories = ((FlutterWidget)item).getSubCategories();
        return subcategories != null && subcategories.containsAll(expected);
      }

      @Override
      public void describeMismatch(Object item, Description description) {
        description.appendText("got " + ((FlutterWidget)item).getSubCategories());
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("getSubCategories should return ").appendValue(subcategory);
      }
    };
  }

  @NotNull
  private static FlutterWidget widget(@NotNull String name) {
    final FlutterWidget widget = FlutterWidget.getCatalog().getWidget(name);
    assertNotNull(widget);
    return widget;
  }

  @Test
  public void allCategories() {
    // Ensure all actual categories are accounted for.
    final List<String> collectedCategories =
      FlutterWidget.getCatalog().getWidgets().stream().map(FlutterWidget::getCategories).flatMap(Collection::stream).distinct().sorted()
        .collect(Collectors.toList());
    final List<String>
      allCategories = Arrays.stream(FlutterWidget.Category.values()).map(FlutterWidget.Category::getLabel).collect(Collectors.toList());

    assertThat(collectedCategories, equalTo(allCategories));
  }

  @Test
  public void categories() {
    assertThat(widget("Container"), hasCategories("Basics"));
    assertThat(widget("Icon"), hasCategories("Basics", "Assets, Images, and Icons"));
    assertThat(widget("Scrollable"), hasCategories("Scrolling"));
    assertThat(widget("Text"), hasCategories("Basics", "Text"));
    assertThat(widget("Theme"), hasCategories("Styling"));
  }

  @Test
  public void subcategories() {
    assertThat(widget("Container"), hasSubCategories("Single-child layout widgets"));
    assertThat(widget("Icon"), hasSubCategories("Information displays"));
    assertThat(widget("Scrollable"), hasSubCategories("Touch interactions"));
    assertThat(widget("Text"), hasSubCategories());
    assertThat(widget("Theme"), hasSubCategories());
  }
}
