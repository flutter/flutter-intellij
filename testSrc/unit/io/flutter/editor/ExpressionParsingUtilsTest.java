/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class ExpressionParsingUtilsTest {
  @Test
  public void parseColorComponents() {
    assertThat(
      ExpressionParsingUtils.parseColorComponents("255, 255, 255, 255)", "", true), is(notNullValue()));
    assertThat(
      ExpressionParsingUtils.parseColorComponents("256, 255, 255, 255)", "", true), is(nullValue()));
  }
}
