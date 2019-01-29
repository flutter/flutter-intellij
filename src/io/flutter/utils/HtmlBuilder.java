/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

public class HtmlBuilder {

  public static String span(String style, String contents) {
    return "<span style = \"" + style + "\"> " + contents + "</span>";
  }

  public static String span(String contents) {
    return tag("span", contents);
  }

  public static String attr(String attribute, String value) {
    return attribute + " = \"" + value + "\"";
  }

  public static String html(String... contents) {
    return tag("html", join(contents));
  }

  public static String pre(String... contents) {
    return tag("pre", join(contents));
  }

  public static String body(String... contents) {
    return join(contents);
  }

  private static String join(String... contents) {
    final StringBuilder sb = new StringBuilder();
    for (String c : contents) {
      sb.append(c);
      sb.append('\n');
    }
    return sb.toString();
  }

  public static String tag(String tag, String contents) {
    return "<" + tag + ">" + contents + "</" + tag + ">";
  }

  public static String div(String attrs, String contents) {
    return "<div " + attrs + ">" + contents + "</div>";
  }

  public static String cls(String value) {
    return attr("class", value);
  }
}
