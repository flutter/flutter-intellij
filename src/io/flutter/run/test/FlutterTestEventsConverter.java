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
import com.jetbrains.lang.dart.ide.runner.test.DartTestEventsConverterZ;
import com.jetbrains.lang.dart.util.DartUrlResolver;
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
  private static boolean isSyntheticWidgetTestGroup(@NotNull Group group) {
    return Objects.equals(group.getUrl(), "package:flutter_test/src/widget_tester.dart") &&
           Objects.equals(group.getName(), SYNTHETIC_WIDGET_GROUP_NAME);
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
    if (isSyntheticWidgetTestGroup(test.myParent)) {
      // Skip the synthetic parent -- For example,
      //   my_test.dart
      //    "-"
      //       "my first test"
      // becomes:
      //   my_test.dart
      //     "my first test"
      test.myParent = test.myParent.myParent;
      // Fix the URL to point to local source (and not the wrapped call in
      // "package:flutter_test/src/widget_tester.dart")
      test.myUrl = test.getSuite().myUrl;
      // Doctor the test name which is prefixed with the bogus group.
      if (test.myName.startsWith(SYNTHETIC_WIDGET_GROUP_NAME)) {
        // e.g., "- my first test" => "my first test"
        test.myName = test.myName.substring(2);
      }
    }
  }

  @Override
  protected boolean handleGroup(@NotNull Group group) throws ParseException {
    // Special case synthetic widget test groups.
    return isSyntheticWidgetTestGroup(group) || super.handleGroup(group);
  }
}
