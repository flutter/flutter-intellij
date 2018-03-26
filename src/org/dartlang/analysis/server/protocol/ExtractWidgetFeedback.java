/*
 * Copyright (c) 2018, the Dart project authors.  Please see the AUTHORS file
 * for details. All rights reserved. Use of this source code is governed by a
 * BSD-style license that can be found in the LICENSE file.
 *
 * This file has been automatically generated.  Please do not edit it manually.
 * To regenerate the file, use the script "pkg/analysis_server/tool/spec/generate_files".
 */
package org.dartlang.analysis.server.protocol;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ExtractWidgetFeedback extends RefactoringFeedback {

  public static final ExtractWidgetFeedback[] EMPTY_ARRAY = new ExtractWidgetFeedback[0];

  public static final List<ExtractWidgetFeedback> EMPTY_LIST = Lists.newArrayList();

  /**
   * Constructor for {@link ExtractWidgetFeedback}.
   */
  public ExtractWidgetFeedback() {
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ExtractWidgetFeedback) {
      final ExtractWidgetFeedback other = (ExtractWidgetFeedback) obj;
      return
        true;
    }
    return false;
  }

  public static ExtractWidgetFeedback fromJson(JsonObject jsonObject) {
    return new ExtractWidgetFeedback();
  }

  public static List<ExtractWidgetFeedback> fromJsonArray(JsonArray jsonArray) {
    if (jsonArray == null) {
      return EMPTY_LIST;
    }
    final ArrayList<ExtractWidgetFeedback> list = new ArrayList<>(jsonArray.size());
    for (JsonElement aJsonArray : jsonArray) {
      list.add(fromJson(aJsonArray.getAsJsonObject()));
    }
    return list;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public JsonObject toJson() {
    return new JsonObject();
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("[");
    builder.append("]");
    return builder.toString();
  }

}
