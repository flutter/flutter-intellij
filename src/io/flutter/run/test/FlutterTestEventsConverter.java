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

import java.util.Objects;

public class FlutterTestEventsConverter extends DartTestEventsConverterZ {

  public FlutterTestEventsConverter(@NotNull String testFrameworkName,
                                    @NotNull TestConsoleProperties consoleProperties,
                                    @NotNull DartUrlResolver urlResolver) {
    super(testFrameworkName, consoleProperties, urlResolver);
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

  @Nullable
  private static JsonElement getValue(@NotNull JsonElement element, @NotNull String member) {
    if (!(element instanceof JsonObject)) return null;

    final JsonObject object = (JsonObject)element;
    return object.has(member) ? object.get(member) : null;
  }
}
