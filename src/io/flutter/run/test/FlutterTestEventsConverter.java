/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.test.DartTestEventsConverterZ;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.Objects;

public class FlutterTestEventsConverter extends DartTestEventsConverterZ {

  /**
   * Name of the synthetic group created dynamically by package:flutter_test when wrapping calls to test().
   */
  public static final String SYNTHETIC_WIDGET_GROUP_NAME = "-";

  public FlutterTestEventsConverter(@NotNull String testFrameworkName,
                                    @NotNull TestConsoleProperties consoleProperties,
                                    @NotNull DartUrlResolver urlResolver) {
    super(testFrameworkName, consoleProperties, urlResolver);
  }

  /**
   * Checks if the given group is one created dynamically by flutter_test in the widget_tester
   * widget test wrapper.
   */
  private static boolean isSyntheticWidgetTestGroup(@Nullable Item item) {
    return item instanceof Group && Objects.equals(item.getUrl(), "package:flutter_test/src/widget_tester.dart") &&
           isSyntheticWidgetGroupName(item.getName());
  }

  private static boolean isSyntheticWidgetGroupName(@Nullable String name) {
    return name != null && name.endsWith(SYNTHETIC_WIDGET_GROUP_NAME);
  }

  @Nullable
  private static JsonElement getValue(@NotNull JsonElement element, @NotNull String member) {
    if (!(element instanceof JsonObject)) return null;

    final JsonObject object = (JsonObject)element;
    return object.has(member) ? object.get(member) : null;
  }

  @Override
  protected boolean process(@NotNull JsonArray array) {
    // Consume (and don't print) observatoryUri output, e.g.,
    //     [{"event":"test.startedProcess","params":{"observatoryUri":"http://127.0.0.1:51770/"}}]
    if (array.size() == 1) {
      final JsonElement element = array.get(0);
      final JsonElement event = getValue(element, "event");
      if (event != null) {
        if (Objects.equals(event.getAsString(), "test.startedProcess")) {
          final JsonElement params = getValue(element, "params");
          if (params != null) {
            final JsonElement uri = getValue(params, "observatoryUri");
            if (uri != null) return true;
          }
        }
      }
    }

    return false;
  }

  @Override
  protected void preprocessTestStart(@NotNull Test test) {
    // Reparent tests who are in a synthetic widget test group.
    // This (and the special treatment in #handleGroup()):
    //   * gets rid of a needless extra group in the result tree, and
    //   * properly wires up the test's location URL
    Item item = test;
    while (item != null) {
      if (isSyntheticWidgetTestGroup(item.myParent)) {
        // Skip the synthetic parent -- For example,
        //   my_test.dart
        //    "-"
        //       "my first test"
        // becomes:
        //   my_test.dart
        //     "my first test"
        item.myParent = item.myParent.myParent;
        // Fix the URL to point to local source (and not the wrapped call in
        // "package:flutter_test/src/widget_tester.dart")
        item.myUrl = item.getSuite().myUrl;

        // Doctor the test name which is prefixed with the bogus group.
        final int groupNameIndex = item.myName.lastIndexOf(SYNTHETIC_WIDGET_GROUP_NAME);
        if (groupNameIndex != -1) {
          // e.g.,
          //     "- my first test" => "my first test"
          //     "foo group - Counter increments smoke test" => "foo group Counter increments smoke test"
          final StringBuilder sb = new StringBuilder(item.myName);
          sb.replace(groupNameIndex, groupNameIndex + SYNTHETIC_WIDGET_GROUP_NAME.length() + 1, "");
          item.myName = sb.toString();
        }
      }

      item = item.myParent;
    }
  }

  @Override
  protected boolean handleGroup(@NotNull Group group) throws ParseException {
    // Special case synthetic widget test groups.
    return isSyntheticWidgetTestGroup(group) || super.handleGroup(group);
  }
}
