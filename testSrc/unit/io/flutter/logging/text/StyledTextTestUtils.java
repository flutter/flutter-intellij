/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.text;

import com.intellij.execution.filters.BrowserHyperlinkInfo;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ReflectionUtil;
import org.junit.Assert;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

public class StyledTextTestUtils {

  public static StyledText text(String string, SimpleTextAttributes style) {
    return new StyledText(string, style);
  }

  public static StyledText[] arrayOf(StyledText... texts) {
    return texts;
  }

  public static void assertEquals(StyledText[] expected, List<StyledText> actual) {
    for (int i = 0; i < expected.length; ++i) {
      assertEquals(expected[i], actual.get(i));
    }
  }

  public static void assertEquals(StyledText expected, StyledText actual) {
    Assert.assertEquals(expected.getText(), actual.getText());
    assertEquals(expected.getStyle(), actual.getStyle());
    final String expectedUrl = getUrl(expected.getTag());
    // If a tag is specified, check it.
    if (expectedUrl != null) {
      Assert.assertEquals(expectedUrl, getUrl(actual.getTag()));
    }
  }

  public static void assertEquals(SimpleTextAttributes expected, SimpleTextAttributes actual) {
    if (expected == null) {
      Assert.assertNull(actual);
    }
    else {
      assertNotNull(actual);
      Assert.assertEquals(expected.getStyle(), actual.getStyle());
      Assert.assertEquals(expected.getFontStyle(), actual.getFontStyle());
      Assert.assertEquals(expected.getFgColor(), actual.getFgColor());
    }
  }

  private static String getUrl(Object tag) {
    if (tag instanceof BrowserHyperlinkInfo) {
      final Field field = ReflectionUtil.getDeclaredField(tag.getClass(), "myUrl");
      if (field != null) {
        try {
          return (String)field.get(tag);
        }
        catch (Throwable ignored) {
        }
      }
    }
    return null;
  }
}
