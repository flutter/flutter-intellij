/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;

/**
 * Represents a path in a DiagnosticsNode tree used to quickly display a path
 * in the DiagnosticsNode tree in response to a change in the selected object
 * on the device.
 */
public class DiagnosticsPathNode {
  private final InspectorService.ObjectGroup inspectorService;
  private final JsonObject json;

  public DiagnosticsPathNode(JsonObject json, InspectorService.ObjectGroup inspectorService) {
    this.inspectorService = inspectorService;
    this.json = json;
  }

  public DiagnosticsNode getNode() {
    // We are lazy about getting the diagnosticNode instanceRef so that no additional round trips using the observatory protocol
    // are yet triggered for the typical case where properties of a node are not inspected.
    return new DiagnosticsNode(json.getAsJsonObject("node"), inspectorService, false, null);
  }

  public ArrayList<DiagnosticsNode> getChildren() {
    final ArrayList<DiagnosticsNode> children = new ArrayList<>();
    final JsonElement childrenElement = json.get("children");
    if (childrenElement.isJsonNull()) {
      return children;
    }
    final JsonArray childrenJson = childrenElement.getAsJsonArray();
    for (int i = 0; i < childrenJson.size(); ++i) {
      children.add(new DiagnosticsNode(childrenJson.get(i).getAsJsonObject(), inspectorService, false, null));
    }
    return children;
  }

  /**
   * Returns the index of the child that continues the path if any.
   */
  public int getChildIndex() {
    final JsonElement childIndex = json.get("childIndex");
    if (childIndex.isJsonNull()) {
      return -1;
    }
    return childIndex.getAsInt();
  }
}
