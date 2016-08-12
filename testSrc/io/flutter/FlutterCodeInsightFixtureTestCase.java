/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import io.flutter.util.FlutterTestUtils;

abstract public class FlutterCodeInsightFixtureTestCase extends DartCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return FlutterTestUtils.BASE_TEST_DATA_PATH + getBasePath();
  }
}
