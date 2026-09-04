/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilities for reading and writing IntelliJ run configurations to and from the disk.
 */
public class ElementIO {
  public static void addOption(@NotNull Element element, @NotNull String name, @Nullable String value) {
    if (value == null) return;

    final Element child = new Element("option");
    child.setAttribute("name", name);
    child.setAttribute("value", value);
    element.addContent(child);
  }

  public static Map<String, String> readOptions(Element element) {
    final Map<String, String> result = new HashMap<>();
    for (Element child : element.getChildren()) {
      if ("option".equals(child.getName())) {
        final String name = child.getAttributeValue("name");
        final String value = child.getAttributeValue("value");
        if (name != null && value != null) {
          result.put(name, value);
        }
      }
    }
    return result;
  }
}
