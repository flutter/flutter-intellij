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
import com.google.dart.server.utilities.general.ObjectUtilities;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ExtractWidgetOptions extends RefactoringOptions {

  public static final ExtractWidgetOptions[] EMPTY_ARRAY = new ExtractWidgetOptions[0];

  public static final List<ExtractWidgetOptions> EMPTY_LIST = Lists.newArrayList();

  /**
   * The name that the widget class should be given.
   */
  private String name;

  /**
   * True if a StatefulWidget should be created.
   */
  private boolean stateful;

  /**
   * Constructor for {@link ExtractWidgetOptions}.
   */
  public ExtractWidgetOptions(String name, boolean stateful) {
    this.name = name;
    this.stateful = stateful;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ExtractWidgetOptions) {
      final ExtractWidgetOptions other = (ExtractWidgetOptions) obj;
      return
        ObjectUtilities.equals(other.name, name) &&
        other.stateful == stateful;
    }
    return false;
  }

  public static ExtractWidgetOptions fromJson(JsonObject jsonObject) {
    final String name = jsonObject.get("name").getAsString();
    final boolean stateful = jsonObject.get("stateful").getAsBoolean();
    return new ExtractWidgetOptions(name, stateful);
  }

  public static List<ExtractWidgetOptions> fromJsonArray(JsonArray jsonArray) {
    if (jsonArray == null) {
      return EMPTY_LIST;
    }
    final ArrayList<ExtractWidgetOptions> list = new ArrayList<>(jsonArray.size());
    for (JsonElement aJsonArray : jsonArray) {
      list.add(fromJson(aJsonArray.getAsJsonObject()));
    }
    return list;
  }

  /**
   * The name that the widget class should be given.
   */
  public String getName() {
    return name;
  }

  /**
   * True if a StatefulWidget should be created.
   */
  public boolean stateful() {
    return stateful;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * The name that the widget class should be given.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * True if a StatefulWidget should be created.
   */
  public void setStateful(boolean stateful) {
    this.stateful = stateful;
  }

  public JsonObject toJson() {
    final JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("name", name);
    jsonObject.addProperty("stateful", stateful);
    return jsonObject;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("[");
    builder.append("name=");
    builder.append(name);
    builder.append(", ");
    builder.append("stateful=");
    builder.append(stateful);
    builder.append("]");
    return builder.toString();
  }

}
