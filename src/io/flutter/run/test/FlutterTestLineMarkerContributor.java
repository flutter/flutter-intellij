/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import io.flutter.run.common.CommonTestConfigUtils;
import io.flutter.run.common.TestLineMarkerContributor;

/**
 * Annotates conventional Flutter tests with line markers.
 */
public class FlutterTestLineMarkerContributor extends TestLineMarkerContributor {
  public FlutterTestLineMarkerContributor() {
    super(TestConfigUtils.getInstance());
  }
}
